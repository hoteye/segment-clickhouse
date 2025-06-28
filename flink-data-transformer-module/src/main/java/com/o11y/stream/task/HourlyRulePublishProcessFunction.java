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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
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

    // 检查间隔（毫秒），默认1小时
    private final long checkIntervalMs;

    // 状态：记录上次执行的小时，避免重复执行
    private transient ValueState<Integer> lastExecutedHour;

    // 数据库服务和Kafka生产者
    private transient DatabaseService databaseService;
    private transient Producer<String, String> kafkaProducer;

    /**
     * 构造函数
     * 
     * @param clickhouseUrl         ClickHouse连接URL
     * @param schemaName            数据库schema名称
     * @param username              数据库用户名
     * @param password              数据库密码
     * @param kafkaBootstrapServers Kafka服务器地址
     * @param kafkaTopicName        Kafka主题名称
     * @param checkIntervalMs       检查间隔（毫秒）
     */
    public HourlyRulePublishProcessFunction(
            String clickhouseUrl,
            String schemaName,
            String username,
            String password,
            String kafkaBootstrapServers,
            String kafkaTopicName,
            long checkIntervalMs) {
        this.clickhouseUrl = clickhouseUrl;
        this.username = username;
        this.password = password;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.kafkaTopicName = kafkaTopicName;
        this.checkIntervalMs = checkIntervalMs;
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

        LOG.info("HourlyRulePublishProcessFunction initialized, checkInterval: {} ms", checkIntervalMs);
    }

    @Override
    public void processElement(
            String value,
            Context ctx,
            Collector<String> out) throws Exception {

        // 注册定时器到下一个检查时间点
        long nextCheckTime = calculateNextCheckTime();
        ctx.timerService().registerProcessingTimeTimer(nextCheckTime);

        LOG.debug("Registered timer for next check at: {}",
                LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(nextCheckTime),
                        java.time.ZoneId.systemDefault()));
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
            LOG.info("定时器触发 - 当前时间: {}时, 上次执行时间: {}, 检查间隔: {}ms",
                    currentHour, lastHour, checkIntervalMs);

            // 检查是否已经执行过（避免在同一小时内重复执行）
            if (lastHour != null && lastHour == currentHour) {
                LOG.info("Hour {} already processed, skipping (检查间隔: {}ms)", currentHour, checkIntervalMs);
                // 注册下一个检查时间的定时器
                long nextCheckTime = calculateNextCheckTime();
                ctx.timerService().registerProcessingTimeTimer(nextCheckTime);
                LOG.info("已注册下一个检查时间: {}",
                        LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(nextCheckTime),
                                java.time.ZoneId.systemDefault()));
                return;
            }

            LOG.info("=== 开始执行小时级规则下发任务，当前时间: {}时 ===", currentHour);

            // 从数据库读取当前小时的规则
            Map<String, AlarmRule> ruleMap = loadHourlyRules(currentHour);

            if (!ruleMap.isEmpty()) {
                // 下发规则到Kafka
                publishRulesToKafka(ruleMap, currentHour);
                LOG.info("成功下发 {}时 的规则到Kafka，规则数量: {}", currentHour, ruleMap.size());

                // 输出处理结果（可选）
                out.collect(String.format("Published %d rules for hour %d", ruleMap.size(), currentHour));
            } else {
                LOG.warn("{}时 没有找到任何规则，请检查hourly_alarm_rules表", currentHour);
            }

            // 更新状态（添加调试日志）
            lastExecutedHour.update(currentHour);
            LOG.info("已更新状态: lastExecutedHour = {}", currentHour);

            LOG.info("=== {}时 规则下发任务执行完成 ===", currentHour);

        } catch (Exception e) {
            LOG.error("执行小时级规则下发任务失败: {}", e.getMessage(), e);
        } finally {
            // 注册下一个检查时间的定时器
            long nextCheckTime = calculateNextCheckTime();
            ctx.timerService().registerProcessingTimeTimer(nextCheckTime);
            LOG.info("已注册下一个检查定时器，下次检查时间: {}",
                    LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(nextCheckTime),
                            java.time.ZoneId.systemDefault()));
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

        Connection conn = databaseService.getConnection();
        // 使用新的表结构，从各个字段构造AlarmRule对象
        String sql = "SELECT service, operator_name, operator_class, " +
                "avg_duration_low, avg_duration_mid, avg_duration_high, " +
                "max_duration_low, max_duration_mid, max_duration_high, " +
                "success_rate_low, success_rate_mid, success_rate_high, " +
                "traffic_volume_low, traffic_volume_mid, traffic_volume_high, " +
                "alarm_template FROM hourly_alarm_rules WHERE hour_of_day = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hourOfDay);

            try (ResultSet rs = ps.executeQuery()) {
                int ruleCount = 0;
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
                    ruleCount++;
                }

                if (ruleCount > 0) {
                    LOG.debug("从数据库读取到 {}时 的规则，规则数量: {}", hourOfDay, ruleCount);
                } else {
                    LOG.debug("数据库中没有找到 {}时 的规则", hourOfDay);
                }
            }
        }

        return ruleMap;
    }

    /**
     * 将规则推送到Kafka
     * 
     * @param ruleMap   规则映射
     * @param hourOfDay 小时序号
     */
    private void publishRulesToKafka(Map<String, AlarmRule> ruleMap, int hourOfDay) throws Exception {
        String json = objectMapper.writeValueAsString(ruleMap);
        String key = "hourly_rules_" + String.format("%02d", hourOfDay);

        ProducerRecord<String, String> record = new ProducerRecord<>(kafkaTopicName, key, json);

        kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                LOG.error("Failed to send rules to Kafka for hour {}: {}", hourOfDay, exception.getMessage());
            } else {
                LOG.debug("Successfully sent rules to Kafka for hour {}, offset: {}",
                        hourOfDay, metadata.offset());
            }
        });

        // 确保消息发送完成
        kafkaProducer.flush();
    }

    /**
     * 计算下一个检查时间点
     * 使用配置的检查间隔（支持测试时的1分钟间隔）
     * 
     * @return 下一个检查时间的时间戳（毫秒）
     */
    private long calculateNextCheckTime() {
        return System.currentTimeMillis() + checkIntervalMs;
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