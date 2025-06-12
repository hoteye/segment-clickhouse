package com.o11y.flink;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;

import java.util.List;
import java.util.Map;
import com.twitter.chill.protobuf.ProtobufSerializer;

import segment.v3.Segment.SegmentObject;
import segment.v3.Segment.SpanObject;
import segment.v3.Segment.SpanType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.flink.serde.SegmentDeserializationSchema;
import com.o11y.flink.util.OperatorParamLoader;
import com.o11y.flink.registry.OperatorRegistry;
import com.o11y.DatabaseService;
import com.o11y.flink.operator.DubboEntryAvgDurationAggregateFunctionOperator;
import com.o11y.flink.operator.FlinkOperator;
import com.o11y.flink.operator.ServiceAggAndAlarm;
import com.o11y.flink.operator.ServiceAvgDurationAggregateFunctionOperator;
import com.o11y.flink.sink.OperatorAggResultClickHouseSink;
import com.o11y.flink.sink.SimpleClickHouseSink;
import com.o11y.flink.task.NewKeyTableSyncTask;
import com.o11y.flink.sink.OperatorAggResultTuple5ClickHouseSink;
import com.o11y.flink.operator.AlarmGatewaySink;

import java.sql.Statement;
import java.time.Duration;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.tuple.Tuple5;

public class FlinkKafkaToClickHouseJob {
        private static final Logger LOG = LoggerFactory.getLogger(FlinkKafkaToClickHouseJob.class);

        public static void main(String[] args) throws Exception {
                LOG.warn("FlinkKafkaToClickHouseJob starting, preparing to initialize environment");
                Map<String, Object> config = com.o11y.ConfigLoader.loadConfig("application.yaml");
                Map<String, String> kafkaConfig = (Map<String, String>) config.get("kafka");
                Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
                Map<String, Integer> batchConfig = (Map<String, Integer>) config.get("batch");
                Map<String, Object> flinkConfig = (Map<String, Object>) config.get("flink");

                StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
                // 设置并行度
                env.setParallelism((Integer) flinkConfig.get("parallelism"));
                // 启用 checkpoint
                env.enableCheckpointing(((Number) flinkConfig.get("checkpoint_interval")).longValue());
                // 设置 checkpoint 超时时间
                env.getCheckpointConfig().setCheckpointTimeout(
                                ((Number) flinkConfig.get("checkpoint_timeout")).longValue());
                // Kryo序列化配置
                env.getConfig().addDefaultKryoSerializer(
                                segment.v3.Segment.SegmentObject.class,
                                ProtobufSerializer.class);

                LOG.warn("Kafka configuration: {}", kafkaConfig);
                LOG.warn("ClickHouse configuration: {}", clickhouseConfig);
                KafkaSource<SegmentObject> kafkaSource = KafkaSource.<SegmentObject>builder()
                                .setBootstrapServers(kafkaConfig.get("bootstrap_servers"))
                                .setTopics(kafkaConfig.get("topic"))
                                .setGroupId(kafkaConfig.get("group_id"))
                                .setStartingOffsets(OffsetsInitializer.committedOffsets())
                                .setValueOnlyDeserializer(new SegmentDeserializationSchema())
                                .build();
                LOG.warn("KafkaSource built, preparing to create DataStream");
                DataStream<SegmentObject> stream = env.fromSource(
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
                LOG.warn("DataStream created, preparing to add Sink");
                // 注册所有算子
                OperatorRegistry.register(new DubboEntryAvgDurationAggregateFunctionOperator());
                OperatorRegistry.register(new ServiceAvgDurationAggregateFunctionOperator());

                // 独立添加 SimpleClickHouseSink
                stream.addSink(new SimpleClickHouseSink(clickhouseConfig, batchConfig))
                                .name(SimpleClickHouseSink.class.getSimpleName());

                // 自动组装所有算子链（不再自动 apply SimpleClickHouseSinkOperator）
                DatabaseService dbService = new DatabaseService(
                                clickhouseConfig.get("url"),
                                clickhouseConfig.get("schema_name"),
                                clickhouseConfig.get("table_name"),
                                clickhouseConfig.get("username"),
                                clickhouseConfig.get("password")).initConnection();

                // 启动Flink前清理new_key表中不存在于 events 表的字段
                String sql = "DELETE FROM new_key WHERE keyName NOT IN (SELECT name FROM system.columns WHERE table='"
                                + clickhouseConfig.get("table_name") + "')";
                try (Statement stmt = dbService.getConnection().createStatement()) {
                        stmt.execute(sql);
                        LOG.info("Cleaned up new_key table for non-existing columns in events table");
                }

                // 启动定时任务同步 new_key 表到 events 表
                long addColumnsInterval = ((Number) config.get("add_columns_interval")).longValue();
                new NewKeyTableSyncTask(dbService, addColumnsInterval).start();

                for (FlinkOperator op : OperatorRegistry.getOperators()) {
                        Map<String, List<String>> params = OperatorParamLoader.loadParamList(dbService,
                                        op.getClass().getSimpleName());
                        ServiceAggAndAlarm result = op.apply(stream, params);
                        if (result != null) {
                                if (result.aggStream != null) {
                                        String typeStr = result.aggStream.getType().toString();
                                        if (typeStr.contains("Tuple4<String, Double, Long, Long>")) {
                                                @SuppressWarnings("unchecked")
                                                DataStream<Tuple4<String, Double, Long, Long>> castedStream = (DataStream<Tuple4<String, Double, Long, Long>>) result.aggStream;
                                                castedStream.addSink(
                                                                new OperatorAggResultClickHouseSink(
                                                                                clickhouseConfig))
                                                                .name("OperatorAggResultClickHouseSink");
                                        } else if (typeStr.contains("Tuple5<String, String, Double, Long, Long>")) {
                                                @SuppressWarnings("unchecked")
                                                DataStream<Tuple5<String, String, Double, Long, Long>> castedStream = (DataStream<Tuple5<String, String, Double, Long, Long>>) result.aggStream;
                                                castedStream.addSink(
                                                                new OperatorAggResultTuple5ClickHouseSink(
                                                                                clickhouseConfig))
                                                                .name("OperatorAggResultTuple5ClickHouseSink");
                                        }
                                        // 可扩展更多 Tuple 类型 sink
                                }
                                if (result.alarmStream != null) {
                                        result.alarmStream.addSink(new AlarmGatewaySink())
                                                        .name("AlarmGatewaySink");
                                }
                        }
                }
                env.execute("FlinkKafkaToClickHouseJob");
        }
}