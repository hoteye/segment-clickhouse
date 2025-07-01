package com.o11y.stream.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o11y.domain.model.alarm.AlarmRule;
import com.o11y.infrastructure.database.DatabaseService;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 小时级规则下发处理函数
 * 
 * <p>
 * 基于Flink定时机制，每小时整点自动从hourly_alarm_rules表中读取当前小时的规则，
 * 并下发到Kafka实现热更新。采用Flink的ProcessFunction + Timer机制，
 * 确保在分布式环境下的可靠性和一致性。
 * 
 * <p>
 * <strong>核心特性：</strong>
 * <ul>
 * <li>基于Flink Timer的精确定时触发</li>
 * <li>自动计算到下一个整点的延迟时间</li>
 * <li>从hourly_alarm_rules表读取JSON格式的规则</li>
 * <li>反序列化为Map<String, AlarmRule>并下发到Kafka</li>
 * <li>支持故障恢复和状态管理</li>
 * </ul>
 * 
 * <p>
 * <strong>工作流程：</strong>
 * <ol>
 * <li>初始化时注册到下一个整点的定时器</li>
 * <li>定时器触发时获取当前小时(0-23)</li>
 * <li>查询hourly_alarm_rules表中对应小时的规则JSON</li>
 * <li>反序列化JSON为Map<String, AlarmRule></li>
 * <li>推送到Kafka的alarm_rule_topic</li>
 * <li>注册下一个小时的定时器，循环执行</li>
 * </ol>
 * 
 * <p>
 * <strong>配置要求：</strong>
 * <ul>
 * <li>ClickHouse连接配置：用于读取规则数据</li>
 * <li>Kafka配置：用于下发规则到消息队列</li>
 * <li>定时间隔：支持自定义检查间隔（默认整点触发）</li>
 * </ul>
 * 
 * @see NewKeyTableSyncProcessFunction 参考的定时任务实现
 * @author DDD Architecture Team
 * @since 2.0.0
 */
public class HourlyRulePublishProcessFunction extends KeyedProcessFunction<String, String, String> {
    private static final Logger LOG = LoggerFactory.getLogger(HourlyRulePublishProcessFunction.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ClickHouse连接配置
    private final String clickhouseUrl;
    private final String username;
    private final String password;

    // Kafka配置
    private final String kafkaBootstrapServers;
    private final String kafkaTopicName;

    // 状态：记录上次执行的小时，避免重复执行
    private transient ValueState<Integer> lastExecutedHour;

    // 数据库服务和Kafka生产者
    private transient DatabaseService databaseService;
    private transient Producer<String, String> kafkaProducer;

    // ✅ 添加标志位，只注册一次
    private boolean timerRegistered = false;

    /**
     * 构造函数
     * 
     * @param clickhouseUrl         ClickHouse连接URL
     * @param schemaName            数据库schema名称
     * @param username              数据库用户名
     * @param password              数据库密码
     * @param kafkaBootstrapServers Kafka服务器地址
     * @param kafkaTopicName        Kafka主题名称
     */
    public HourlyRulePublishProcessFunction(
            String clickhouseUrl,
            String schemaName,
            String username,
            String password,
            String kafkaBootstrapServers,
            String kafkaTopicName) {
        this.clickhouseUrl = clickhouseUrl;
        this.username = username;
        this.password = password;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.kafkaTopicName = kafkaTopicName;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        // 初始化状态
        ValueStateDescriptor<Integer> lastHourDescriptor = new ValueStateDescriptor<>(
                "lastExecutedHour", Integer.class);
        lastExecutedHour = getRuntimeContext().getState(lastHourDescriptor);

        // 初始化ClickHouse连接
        Map<String, String> clickhouseConfig = new HashMap<>();
        clickhouseConfig.put("url", clickhouseUrl);
        clickhouseConfig.put("username", username);
        clickhouseConfig.put("password", password);
        databaseService = new DatabaseService(clickhouseConfig).initConnection();

        // 初始化Kafka生产者
        Properties kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", kafkaBootstrapServers);
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("enable.idempotence", "true");
        kafkaProps.put("retries", 3);
        kafkaProps.put("batch.size", 16384);
        kafkaProps.put("linger.ms", 1);
        kafkaProducer = new KafkaProducer<>(kafkaProps);

        LOG.info("HourlyRulePublishProcessFunction initialized for hourly rule publishing");
    }

    @Override
    public void processElement(
            String value,
            Context ctx,
            Collector<String> out) throws Exception {

        if (!timerRegistered) {
            long nextHourTime = calculateNextHourTime();
            ctx.timerService().registerProcessingTimeTimer(nextHourTime);
            timerRegistered = true;
            LOG.info("Timer registered for next hour at: {}", nextHourTime);
        }
    }

    @Override
    public void onTimer(
            long timestamp,
            OnTimerContext ctx,
            Collector<String> out) throws Exception {

        try {
            // 获取当前小时
            LocalDateTime now = LocalDateTime.now();
            int currentHour = now.getHour();

            // 检查状态值（添加调试日志）
            Integer lastHour = lastExecutedHour.value();

            // 检查是否已经执行过（避免在同一小时内重复执行）
            if (lastHour != null && lastHour == currentHour) {
                // 注册下一个整点的定时器
                long nextHourTime = calculateNextHourTime();
                ctx.timerService().registerProcessingTimeTimer(nextHourTime);
                return;
            }

            LOG.info("开始执行小时级规则下发任务，当前时间: {}时", currentHour);

            // 从数据库读取当前小时的规则
            Map<String, AlarmRule> ruleMap = loadHourlyRules(currentHour);

            if (!ruleMap.isEmpty()) {
                // 下发规则到Kafka
                publishRulesToKafka(ruleMap, currentHour);
                LOG.info("成功下发{}时的规则到Kafka，规则数量: {}", currentHour, ruleMap.size());

                // 更新状态
                lastExecutedHour.update(currentHour);
            } else {
                LOG.warn("{}时没有找到规则数据", currentHour);
            }

            // 注册下一个整点的定时器
            long nextHourTime = calculateNextHourTime();
            ctx.timerService().registerProcessingTimeTimer(nextHourTime);

        } catch (Exception e) {
            LOG.error("小时级规则下发任务执行失败: {}", e.getMessage(), e);

            // 即使出现异常，也要注册下一个整点定时器，确保任务继续执行
            long nextHourTime = calculateNextHourTime();
            ctx.timerService().registerProcessingTimeTimer(nextHourTime);
        }
    }

    /**
     * 从hourly_alarm_rules表加载指定小时的规则（从新表结构读取）
     * 
     * @param hourOfDay 小时序号 (0-23)
     * @return 规则Map，key为规则标识，value为AlarmRule对象
     */
    private Map<String, AlarmRule> loadHourlyRules(int hourOfDay) throws Exception {
        Map<String, AlarmRule> ruleMap = new HashMap<>();

        String sql = "SELECT service, operator_name, operator_class, " +
                "avg_duration_low, avg_duration_mid, avg_duration_high, " +
                "max_duration_low, max_duration_mid, max_duration_high, " +
                "success_rate_low, success_rate_mid, success_rate_high, " +
                "traffic_volume_low, traffic_volume_mid, traffic_volume_high, " +
                "alarm_template " +
                "FROM hourly_alarm_rules " +
                "WHERE hour_of_day = ?";

        try (PreparedStatement ps = databaseService.getConnection().prepareStatement(sql)) {
            ps.setInt(1, hourOfDay);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AlarmRule rule = new AlarmRule();
                    rule.service = rs.getString("service");
                    rule.operatorName = rs.getString("operator_name");
                    rule.operatorClass = rs.getString("operator_class");
                    rule.avgDurationLow = rs.getDouble("avg_duration_low");
                    rule.avgDurationMid = rs.getDouble("avg_duration_mid");
                    rule.avgDurationHigh = rs.getDouble("avg_duration_high");
                    rule.maxDurationLow = rs.getDouble("max_duration_low");
                    rule.maxDurationMid = rs.getDouble("max_duration_mid");
                    rule.maxDurationHigh = rs.getDouble("max_duration_high");
                    rule.successRateLow = rs.getDouble("success_rate_low");
                    rule.successRateMid = rs.getDouble("success_rate_mid");
                    rule.successRateHigh = rs.getDouble("success_rate_high");
                    rule.trafficVolumeLow = rs.getDouble("traffic_volume_low");
                    rule.trafficVolumeMid = rs.getDouble("traffic_volume_mid");
                    rule.trafficVolumeHigh = rs.getDouble("traffic_volume_high");
                    rule.alarmTemplate = rs.getString("alarm_template");

                    String ruleKey = rule.combine();
                    ruleMap.put(ruleKey, rule);
                }
            }
        }

        return ruleMap;
    }

    /**
     * 将规则推送到Kafka
     * 
     * 使用固定key配合Kafka Log Compaction策略，确保：
     * 1. 每次发送都会覆盖之前的规则（避免重复累积）
     * 2. 消费者启动时能获取到最新的完整规则集
     * 3. 利用Kafka的幂等性保证消息不重复
     * 
     * @param ruleMap   规则映射
     * @param hourOfDay 小时序号
     */
    private void publishRulesToKafka(Map<String, AlarmRule> ruleMap, int hourOfDay) throws Exception {
        String ruleMapJson = objectMapper.writeValueAsString(ruleMap);
        // 使用固定key，配合Log Compaction策略
        String key = "global_alarm_rules";

        ProducerRecord<String, String> record = new ProducerRecord<>(kafkaTopicName, key, ruleMapJson);

        // 同步发送，确保消息真正发送成功
        var result = kafkaProducer.send(record).get();

        LOG.debug("规则推送到Kafka成功: topic={}, key={}, partition={}, offset={}, 规则数量={}",
                result.topic(), key, result.partition(), result.offset(), ruleMap.size());
    }

    /**
     * 计算下一个整点时间
     * 确保在每小时的整点触发（如12:00, 13:00, 14:00...）
     */
    private long calculateNextHourTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextHour = now.plusHours(1)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        return nextHour.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public void close() throws Exception {
        super.close();

        if (kafkaProducer != null) {
            kafkaProducer.close();
        }

        if (databaseService != null) {
            databaseService.close();
        }

        LOG.info("HourlyRulePublishProcessFunction closed");
    }
}