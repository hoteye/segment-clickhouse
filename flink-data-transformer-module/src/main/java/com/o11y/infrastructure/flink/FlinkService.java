package com.o11y.infrastructure.flink;

import com.o11y.stream.operator.aggregate.AggregateOperator;
import com.o11y.stream.operator.base.FlinkOperator;
import com.o11y.domain.model.aggregation.ServiceAggResult;
import com.o11y.shared.util.OperatorRegistry;
import com.o11y.stream.sink.AlarmGatewaySink;
import com.o11y.stream.sink.AggResultClickHouseSink;
import com.o11y.stream.sink.SimpleClickHouseSink;
import com.o11y.stream.task.NewKeyTableSyncProcessFunction;
import com.o11y.stream.task.HourlyRulePublishProcessFunction;
import com.o11y.infrastructure.database.DatabaseService;
import com.o11y.stream.source.SegmentDeserializationSchema;
import com.o11y.shared.util.OperatorParamLoader;
import com.o11y.shared.util.InfiniteSource;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import segment.v3.Segment.SegmentObject;
import segment.v3.Segment.SpanObject;
import segment.v3.Segment.SpanType;
import com.twitter.chill.protobuf.ProtobufSerializer;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.o11y.domain.model.alarm.AlarmRule;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import com.o11y.domain.model.alarm.AlertMessage;

/**
 * Flink 流处理服务类。
 * 
 * <p>
 * 负责构建和管理完整的 Flink 流处理作业，包括数据源配置、流式算子注册、
 * 告警规则广播、数据汇聚和存储等核心功能的协调和管理。
 * 
 * <p>
 * <strong>主要职责：</strong>
 * <ul>
 * <li>Flink 执行环境初始化和配置</li>
 * <li>Kafka 数据源构建和 SegmentObject 流创建</li>
 * <li>动态算子注册和参数加载</li>
 * <li>告警规则广播流管理</li>
 * <li>聚合结果和告警消息的 Sink 配置</li>
 * <li>ClickHouse 表结构动态管理</li>
 * </ul>
 * 
 * <p>
 * <strong>架构特点：</strong>
 * <ul>
 * <li>支持多种业务聚合算子的动态注册</li>
 * <li>实现告警规则的实时广播和状态管理</li>
 * <li>提供表结构动态扩展能力</li>
 * <li>支持批量写入和性能优化</li>
 * </ul>
 * 
 * <p>
 * <strong>配置依赖：</strong>
 * 需要 application.yaml 中的 kafka、clickhouse、batch、flink 等配置段。
 * 
 * @see OperatorRegistry 算子注册管理
 * @see DatabaseService ClickHouse 数据库服务
 * @see AggAlertBroadcastFunction 告警广播函数
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class FlinkService {
        private static final Logger LOG = LoggerFactory.getLogger(FlinkService.class);
        private Map<String, Object> config;
        private Map<String, String> kafkaConfig;
        private Map<String, String> clickhouseConfig;
        private Map<String, Integer> batchConfig;
        private Map<String, Object> flinkConfig;
        private StreamExecutionEnvironment env;
        private DataStream<SegmentObject> stream;
        private DatabaseService dbService;

        /**
         * 构造函数，初始化配置参数。
         *
         * @param config 全局配置，包含 kafka、clickhouse、batch、flink 等子配置
         */
        public FlinkService(Map<String, Object> config) {
                this.config = config;
                this.kafkaConfig = (Map<String, String>) config.get("kafka");
                this.clickhouseConfig = (Map<String, String>) config.get("clickhouse");
                this.batchConfig = (Map<String, Integer>) config.get("batch");
                this.flinkConfig = (Map<String, Object>) config.get("flink");
        }

        /**
         * Flink 作业主流程入口，依次完成环境初始化、数据源构建、规则流广播、业务流与规则流 connect、算子注册、sink 配置等操作。
         *
         * @throws Exception 各阶段可能抛出的异常
         */
        public void run() throws Exception {
                initEnv();
                buildSource();
                registerOperators();
                setupSimpleSink();
                setupDatabaseService();
                cleanNewKeyTable();
                startNewKeySyncTask();
                startHourlyRulePublishTask();
                // 规则流广播和状态描述符
                MapStateDescriptor<String, Map<String, AlarmRule>> ruleStateDescriptor = new MapStateDescriptor<>(
                                "alarmRules",
                                Types.STRING,
                                TypeInformation.of(new TypeHint<Map<String, AlarmRule>>() {
                                }));
                BroadcastStream<Map<String, AlarmRule>> broadcastRuleStream = RuleBroadcastStreamFactory
                                .buildRuleBroadcastStream(env, kafkaConfig, ruleStateDescriptor);
                // 聚合流与规则流 connect、告警流 sink
                setupOperatorSinks(broadcastRuleStream, ruleStateDescriptor);
                execute();
        }

        /**
         * 初始化 Flink 执行环境，设置并行度、checkpoint、序列化等参数。
         * 注意：并行度和 checkpoint 参数需在 config 中配置。
         */
        private void initEnv() {
                env = StreamExecutionEnvironment.getExecutionEnvironment();
                env.setParallelism((Integer) flinkConfig.get("parallelism"));
                env.enableCheckpointing(((Number) flinkConfig.get("checkpoint_interval")).longValue());
                env.getCheckpointConfig().setCheckpointTimeout(
                                ((Number) flinkConfig.get("checkpoint_timeout")).longValue());
                env.getConfig().addDefaultKryoSerializer(
                                segment.v3.Segment.SegmentObject.class,
                                ProtobufSerializer.class);
                LOG.warn("Flink 环境初始化完成");
        }

        /**
         * 构建 Kafka 数据源，反序列化为 SegmentObject 流，并设置水位线和时间戳分配。
         * 支持乱序 2 秒，时间戳取 Entry 类型 span 的 endTime。
         */
        private void buildSource() {
                KafkaSource<SegmentObject> kafkaSource = KafkaSource.<SegmentObject>builder()
                                .setBootstrapServers(kafkaConfig.get("bootstrap_servers"))
                                .setTopics(kafkaConfig.get("topic"))
                                .setGroupId(kafkaConfig.get("group_id"))
                                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
                                .setValueOnlyDeserializer(new SegmentDeserializationSchema())
                                .build();
                stream = env.fromSource(
                                kafkaSource,
                                WatermarkStrategy.<SegmentObject>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                                .withTimestampAssigner((segment, ts) -> {
                                                        for (SpanObject span : segment.getSpansList()) {
                                                                if (span.getSpanType() == SpanType.Entry) {
                                                                        return span.getEndTime();
                                                                }
                                                        }
                                                        return System.currentTimeMillis();
                                                }),
                                "KafkaSource-SegmentObject");
                LOG.warn("Kafka 数据源和 DataStream 构建完成");
        }

        /**
         * 注册所有聚合算子到 OperatorRegistry，便于后续自动遍历和 sink。
         * 支持延迟、成功率、吞吐量等多种业务聚合。
         */
        private void registerOperators() {
                OperatorRegistry.register(new AggregateOperator());
                LOG.warn("所有算子已注册");
        }

        /**
         * 配置主流的简单 ClickHouse sink，直接写入原始 SegmentObject 数据。
         */
        private void setupSimpleSink() {
                stream.addSink(new SimpleClickHouseSink(clickhouseConfig, batchConfig))
                                .name(SimpleClickHouseSink.class.getSimpleName());
        }

        /**
         * 初始化 ClickHouse 数据库服务，建立连接。
         *
         * @throws Exception 连接失败时抛出
         */
        private void setupDatabaseService() throws Exception {
                dbService = new DatabaseService(
                                clickhouseConfig.get("url"),
                                clickhouseConfig.get("schema_name"),
                                clickhouseConfig.get("table_name"),
                                clickhouseConfig.get("username"),
                                clickhouseConfig.get("password")).initConnection();
        }

        /**
         * 清理 ClickHouse 新增字段表（new_key），移除无效字段。
         * 
         * <p>
         * <strong>清理逻辑：</strong><br>
         * 删除 new_key 表中那些在 events 表中已不存在的字段记录，
         * 确保 new_key 表与实际的表结构保持一致。
         * 
         * <p>
         * <strong>SQL 解释：</strong>
         * 
         * <pre>
         * DELETE FROM new_key 
         * WHERE keyName NOT IN (
         *     SELECT name FROM system.columns 
         *     WHERE table='events' AND database='default'
         * )
         * </pre>
         * 
         * <p>
         * <strong>使用场景：</strong>
         * <ul>
         * <li>手动删除了 events 表中的某些列</li>
         * <li>表结构回滚后需要清理过期的字段记录</li>
         * <li>定期维护，确保元数据一致性</li>
         * </ul>
         *
         * @throws Exception SQL 执行异常
         */
        private void cleanNewKeyTable() throws Exception {
                // 构建清理 SQL，删除在 events 表中不存在的字段记录
                String sql = "DELETE FROM new_key WHERE keyName NOT IN (SELECT `name` FROM system.columns WHERE table='"
                                + clickhouseConfig.get("table_name") + "' AND database='"
                                + clickhouseConfig.get("schema_name") + "')";
                LOG.info("Cleaning up new_key table with SQL: {}", sql);
                try (Statement stmt = dbService.getConnection().createStatement()) {
                        stmt.execute(sql);
                }
        }

        /**
         * 启动新字段动态同步任务，实现 ClickHouse 表结构的自动演进。
         * 
         * <p>
         * <strong>业务背景：</strong><br>
         * 在流处理系统中，随着业务发展和数据结构的演进，可能会不断出现新的字段和属性。
         * 该方法启动一个独立的 Flink 算子，定期扫描 new_key 表中的新字段定义，
         * 自动为 ClickHouse events 表添加对应的列，实现 schema 的动态演进。
         * 
         * <p>
         * <strong>addColumnsInterval 参数详解：</strong>
         * <table border="1" cellpadding="5" cellspacing="0">
         * <tr>
         * <th>属性</th>
         * <th>说明</th>
         * <th>示例</th>
         * </tr>
         * <tr>
         * <td>配置来源</td>
         * <td>application.yaml 根级别配置项</td>
         * <td>add_columns_interval: 60000</td>
         * </tr>
         * <tr>
         * <td>配置路径</td>
         * <td>config.get("add_columns_interval")</td>
         * <td>直接从根配置读取</td>
         * </tr>
         * <tr>
         * <td>数据类型</td>
         * <td>Long 类型，毫秒为单位</td>
         * <td>60000L (1分钟)</td>
         * </tr>
         * <tr>
         * <td>默认值建议</td>
         * <td>30秒到5分钟之间</td>
         * <td>30000-300000ms</td>
         * </tr>
         * <tr>
         * <td>调优考虑</td>
         * <td>平衡及时性与系统负载</td>
         * <td>生产建议60秒</td>
         * </tr>
         * </table>
         * 
         * <p>
         * <strong>配置示例 (application.yaml)：</strong>
         * 
         * <pre>{@code
         * # 根级别配置 - 新字段同步间隔
         * add_columns_interval: 60000  # 60秒，平衡及时性和性能
         * 
         * # 其他相关配置
         * clickhouse:
         *   url: jdbc:clickhouse://localhost:8123/default
         *   schema_name: default
         *   table_name: events
         *   username: default
         *   password: ""
         * 
         * flink:
         *   parallelism: 4
         *   checkpoint_interval: 30000
         * }</pre>
         * 
         * <p>
         * <strong>时间间隔选择指南：</strong>
         * <ul>
         * <li><strong>开发环境：</strong>10-30秒，便于快速验证新字段</li>
         * <li><strong>测试环境：</strong>30-60秒，模拟生产环境</li>
         * <li><strong>生产环境：</strong>60-300秒，确保系统稳定性</li>
         * <li><strong>高频场景：</strong>30秒，新字段较多时</li>
         * <li><strong>低频场景：</strong>300秒，稳定业务场景</li>
         * </ul>
         * 
         * <p>
         * <strong>算子配置特点：</strong>
         * <ul>
         * <li><strong>数据源：</strong>InfiniteSource - 产生无限的触发信号</li>
         * <li><strong>并行度：</strong>setParallelism(1) - 确保全局唯一的同步过程</li>
         * <li><strong>键分组：</strong>keyBy(x -> x) - 所有元素使用相同key，路由到同一算子实例</li>
         * <li><strong>处理函数：</strong>NewKeyTableSyncProcessFunction - 核心同步逻辑</li>
         * <li><strong>输出处理：</strong>DiscardingSink - 丢弃输出，仅执行副作用操作</li>
         * </ul>
         * 
         * <p>
         * <strong>后期用途和扩展：</strong>
         * <ol>
         * <li><strong>实时监控：</strong>
         * <ul>
         * <li>监控新字段发现的频率和数量</li>
         * <li>跟踪同步任务的执行状态和耗时</li>
         * <li>检测同步过程中的失败和重试</li>
         * </ul>
         * </li>
         * <li><strong>性能优化：</strong>
         * <ul>
         * <li>根据业务负载动态调整 addColumnsInterval</li>
         * <li>批量处理多个新字段，减少 DDL 操作频率</li>
         * <li>基于数据库负载自适应调整同步策略</li>
         * </ul>
         * </li>
         * <li><strong>功能扩展：</strong>
         * <ul>
         * <li>支持多表同步，扩展到不同的 ClickHouse 表</li>
         * <li>添加字段类型校验和转换规则</li>
         * <li>集成告警机制，同步失败时发送通知</li>
         * </ul>
         * </li>
         * <li><strong>运维支持：</strong>
         * <ul>
         * <li>提供 REST API 支持手动触发同步</li>
         * <li>添加同步历史记录和审计日志</li>
         * <li>支持同步任务的暂停和恢复</li>
         * </ul>
         * </li>
         * </ol>
         * 
         * <p>
         * <strong>数据流架构：</strong>
         * 
         * <pre>
         * InfiniteSource (每秒产生信号)
         *        ↓
         * keyBy(固定key) - 确保单实例处理
         *        ↓
         * NewKeyTableSyncProcessFunction
         *   - 注册定时器 (间隔 = addColumnsInterval)
         *   - 定期扫描 new_key 表
         *   - 执行 ALTER TABLE 添加新列
         *   - 更新 new_key.isCreated 状态
         *        ↓
         * DiscardingSink (丢弃输出)
         * </pre>
         * 
         * <p>
         * <strong>与其他组件的集成：</strong>
         * <ul>
         * <li><strong>SimpleClickHouseSink：</strong>主数据写入时可能会发现新字段</li>
         * <li><strong>SegmentObjectMapper：</strong>字段映射时会向 new_key 表插入新发现的字段</li>
         * <li><strong>DatabaseService：</strong>提供 ClickHouse 连接和类型转换支持</li>
         * <li><strong>监控系统：</strong>可以监控同步任务的执行状态和性能指标</li>
         * </ul>
         * 
         * <p>
         * <strong>故障处理和恢复：</strong>
         * <ul>
         * <li><strong>网络异常：</strong>依赖 Flink checkpoint 机制自动恢复</li>
         * <li><strong>数据库异常：</strong>任务会在下个周期自动重试</li>
         * <li><strong>DDL 冲突：</strong>使用 IF NOT EXISTS 确保操作幂等性</li>
         * <li><strong>任务重启：</strong>无状态设计，重启后立即恢复正常运行</li>
         * </ul>
         */
        private void startNewKeySyncTask() {
                long addColumnsInterval = ((Number) config.get("add_columns_interval")).longValue();
                env.addSource(new InfiniteSource())
                                .keyBy(x -> x)
                                .process(new NewKeyTableSyncProcessFunction(
                                                clickhouseConfig.get("url"),
                                                clickhouseConfig.get("schema_name"),
                                                clickhouseConfig.get("table_name"),
                                                clickhouseConfig.get("username"),
                                                clickhouseConfig.get("password"),
                                                addColumnsInterval))
                                .setParallelism(1)
                                .name("NewKeyTableSyncProcessFunction")
                                .addSink(new org.apache.flink.streaming.api.functions.sink.DiscardingSink<>())
                                .name("DiscardingSink-NewKeyTableSyncProcessFunction");
                LOG.warn("NewKeyTableSyncProcessFunction started as Flink operator");
        }

        /**
         * 启动小时级规则下发任务
         * 
         * <p>
         * 基于Flink定时机制，每小时整点自动从hourly_alarm_rules表中读取当前小时的规则，
         * 并下发到Kafka实现热更新。
         * 
         * <p>
         * <strong>配置要求：</strong>
         * <ul>
         * <li>hourly_rule_publish_interval: 检查间隔配置（毫秒）</li>
         * <li>kafka.alarm_rule_topic: Kafka主题配置</li>
         * <li>clickhouse.*: ClickHouse连接配置</li>
         * </ul>
         * 
         * <p>
         * <strong>工作原理：</strong>
         * <ol>
         * <li>使用InfiniteSource产生触发信号</li>
         * <li>通过keyBy确保单实例处理</li>
         * <li>HourlyRulePublishProcessFunction处理定时逻辑</li>
         * <li>每小时整点从hourly_alarm_rules表读取规则</li>
         * <li>反序列化JSON为Map<String, AlarmRule></li>
         * <li>推送到Kafka的alarm_rule_topic</li>
         * </ol>
         */
        private void startHourlyRulePublishTask() {
                // 从配置中读取检查间隔，默认1分钟（测试用）
                long checkInterval = ((Number) config.get("hourly_rule_publish_interval")).longValue();
                // 从配置中读取Kafka主题，默认为alarm_rule_topic
                String kafkaTopicName = kafkaConfig.getOrDefault("alarm_rule_topic", "alarm_rule_topic");

                env.addSource(new InfiniteSource())
                                .keyBy(x -> x)
                                .process(new HourlyRulePublishProcessFunction(
                                                clickhouseConfig.get("url"),
                                                clickhouseConfig.get("schema_name"),
                                                clickhouseConfig.get("username"),
                                                clickhouseConfig.get("password"),
                                                kafkaConfig.get("bootstrap_servers"),
                                                kafkaTopicName,
                                                checkInterval))
                                .setParallelism(1)
                                .name("HourlyRulePublishProcessFunction")
                                .addSink(new org.apache.flink.streaming.api.functions.sink.DiscardingSink<>())
                                .name("DiscardingSink-HourlyRulePublishProcessFunction");

                LOG.warn("HourlyRulePublishProcessFunction started as Flink operator, topic: {}, interval: {} ms",
                                kafkaTopicName, checkInterval);
        }

        /**
         * 遍历注册的所有 FlinkOperator，自动加载参数，聚合输出与规则流 connect，告警流写入告警网关。
         * 支持多种业务聚合和扩展。
         *
         * @param broadcastRuleStream 规则流广播流
         * @param ruleStateDescriptor 规则流状态描述符
         * @throws Exception 参数加载或 sink 初始化异常
         */
        private void setupOperatorSinks(
                        BroadcastStream<Map<String, AlarmRule>> broadcastRuleStream,
                        MapStateDescriptor<String, Map<String, AlarmRule>> ruleStateDescriptor)
                        throws Exception {
                for (FlinkOperator op : OperatorRegistry.getOperators()) {
                        Map<String, List<String>> params = OperatorParamLoader.loadParamList(dbService,
                                        op.getClass().getSimpleName());
                        DataStream<ServiceAggResult> aggStream = op.apply(stream, params);
                        // 先对聚合流进行 keyBy，再与规则流 connect，输出告警流
                        DataStream<AlertMessage> alertStream = aggStream
                                        .keyBy(ServiceAggResult::getKey)
                                        .connect(broadcastRuleStream)
                                        .process(new AggAlertBroadcastFunction(
                                                        ruleStateDescriptor))
                                        .name(op.getClass().getSimpleName());
                        // 告警流写入告警网关
                        alertStream
                                        .addSink(new AlarmGatewaySink())
                                        .name(op.getClass().getSimpleName());
                        // 聚合流写入 ClickHouse
                        aggStream.addSink(new AggResultClickHouseSink(clickhouseConfig))
                                        .name(AggResultClickHouseSink.class.getSimpleName());
                }
        }

        /**
         * 启动作业执行，提交到 Flink 集群。
         *
         * @throws Exception 作业提交异常
         */
        private void execute() throws Exception {
                env.execute("FlinkKafkaToClickHouseJob");
        }
}
