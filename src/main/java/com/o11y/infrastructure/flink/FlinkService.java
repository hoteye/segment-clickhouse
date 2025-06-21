package com.o11y.infrastructure.flink;

import com.o11y.stream.operator.aggregate.AggregateOperator;
import com.o11y.stream.operator.base.FlinkOperator;
import com.o11y.domain.model.aggregation.ServiceAggResult;
import com.o11y.shared.util.OperatorRegistry;
import com.o11y.stream.sink.AlarmGatewaySink;
import com.o11y.stream.sink.AggResultClickHouseSink;
import com.o11y.stream.sink.SimpleClickHouseSink;
import com.o11y.stream.task.NewKeyTableSyncProcessFunction;
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
 * Flink 相关操作服务类，将环境初始化、流定义、算子注册、sink 配置等操作独立封装。
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
         * @throws Exception SQL 执行异常
         */
        private void cleanNewKeyTable() throws Exception {
                String sql = "DELETE FROM new_key WHERE keyName NOT IN (SELECT name FROM system.columns WHERE table='"
                                + clickhouseConfig.get("table_name") + "')";
                try (Statement stmt = dbService.getConnection().createStatement()) {
                        stmt.execute(sql);
                        LOG.info("Cleaned up new_key table for non-existing columns in events table");
                }
        }

        /**
         * 启动新字段同步任务，定时将 new_key 表中的新字段同步到主表。
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
                                .addSink(new org.apache.flink.streaming.api.functions.sink.DiscardingSink<>());
                LOG.warn("NewKeyTableSyncProcessFunction started as Flink operator");
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
                        BroadcastStream<java.util.Map<String, com.o11y.domain.model.alarm.AlarmRule>> broadcastRuleStream,
                        MapStateDescriptor<String, java.util.Map<String, com.o11y.domain.model.alarm.AlarmRule>> ruleStateDescriptor)
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
                                        .name(AggResultClickHouseSink.class.getSimpleName() + "-"
                                                        + op.getClass().getSimpleName());
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
