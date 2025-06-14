package com.o11y.flink;

import com.o11y.flink.operator.aggregate.ServiceDelayAggregateOperator;
import com.o11y.flink.operator.aggregate.ServiceSuccessRateAggregateOperator;
import com.o11y.flink.operator.aggregate.ServiceThroughputAggregateOperator;
import com.o11y.flink.operator.base.FlinkOperator;
import com.o11y.flink.operator.model.ServiceAggAndAlarm;
import com.o11y.flink.operator.model.ServiceAggResult;
import com.o11y.flink.registry.OperatorRegistry;
import com.o11y.flink.sink.AlarmGatewaySink;
import com.o11y.flink.sink.AggResultClickHouseSink;
import com.o11y.flink.sink.SimpleClickHouseSink;
import com.o11y.flink.task.NewKeyTableSyncTask;
import com.o11y.DatabaseService;
import com.o11y.flink.serde.SegmentDeserializationSchema;
import com.o11y.flink.util.OperatorParamLoader;
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

    public FlinkService(Map<String, Object> config) {
        this.config = config;
        this.kafkaConfig = (Map<String, String>) config.get("kafka");
        this.clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        this.batchConfig = (Map<String, Integer>) config.get("batch");
        this.flinkConfig = (Map<String, Object>) config.get("flink");
    }

    public void run() throws Exception {
        initEnv();
        buildSource();
        registerOperators();
        setupSimpleSink();
        setupDatabaseService();
        cleanNewKeyTable();
        startNewKeySyncTask();
        setupOperatorSinks();
        execute();
    }

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

    private void registerOperators() {
        OperatorRegistry.register(new ServiceDelayAggregateOperator());
        OperatorRegistry.register(new ServiceSuccessRateAggregateOperator());
        OperatorRegistry.register(new ServiceThroughputAggregateOperator());
        LOG.warn("所有算子已注册");
    }

    private void setupSimpleSink() {
        stream.addSink(new SimpleClickHouseSink(clickhouseConfig, batchConfig))
                .name(SimpleClickHouseSink.class.getSimpleName());
    }

    private void setupDatabaseService() throws Exception {
        dbService = new DatabaseService(
                clickhouseConfig.get("url"),
                clickhouseConfig.get("schema_name"),
                clickhouseConfig.get("table_name"),
                clickhouseConfig.get("username"),
                clickhouseConfig.get("password")).initConnection();
    }

    private void cleanNewKeyTable() throws Exception {
        String sql = "DELETE FROM new_key WHERE keyName NOT IN (SELECT name FROM system.columns WHERE table='"
                + clickhouseConfig.get("table_name") + "')";
        try (Statement stmt = dbService.getConnection().createStatement()) {
            stmt.execute(sql);
            LOG.info("Cleaned up new_key table for non-existing columns in events table");
        }
    }

    private void startNewKeySyncTask() {
        long addColumnsInterval = ((Number) config.get("add_columns_interval")).longValue();
        new NewKeyTableSyncTask(dbService, addColumnsInterval).start();
        LOG.warn("NewKeyTableSyncTask started");
    }

    private void setupOperatorSinks() throws Exception {
        for (FlinkOperator op : OperatorRegistry.getOperators()) {
            Map<String, List<String>> params = OperatorParamLoader.loadParamList(dbService,
                    op.getClass().getSimpleName());
            ServiceAggAndAlarm result = op.apply(stream, params);
            if (result != null) {
                if (result.aggStream != null) {
                    DataStream<ServiceAggResult> castedStream = (DataStream<ServiceAggResult>) result.aggStream;
                    castedStream.addSink(new AggResultClickHouseSink(
                            clickhouseConfig))
                            .name(AggResultClickHouseSink.class.getSimpleName());
                }
                if (result.alarmStream != null) {
                    result.alarmStream.addSink(new AlarmGatewaySink())
                            .name(AlarmGatewaySink.class.getSimpleName());
                }
            }
        }
    }

    private void execute() throws Exception {
        env.execute("FlinkKafkaToClickHouseJob");
    }
}
