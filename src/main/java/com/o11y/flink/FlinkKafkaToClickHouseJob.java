package com.o11y.flink;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.twitter.chill.protobuf.ProtobufSerializer;

import segment.v3.Segment.SegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.flink.serde.SegmentDeserializationSchema;
import com.o11y.flink.util.OperatorParamLoader;
import com.o11y.flink.registry.OperatorRegistry;
import com.o11y.DatabaseService;
import com.o11y.flink.operator.AvgSpanDurationAggregateFunctionOperator;
import com.o11y.flink.operator.DubboEntryAvgDurationAggregateFunctionOperator;
import com.o11y.flink.operator.FlinkOperator;
import com.o11y.flink.operator.MaxSpanDurationAggregateFunctionOperator;
import com.o11y.flink.sink.SimpleClickHouseSink;
import com.o11y.flink.task.NewKeyTableSyncTask;

public class FlinkKafkaToClickHouseJob {
        private static final Logger LOG = LoggerFactory.getLogger(FlinkKafkaToClickHouseJob.class);

        public static void main(String[] args) throws Exception {
                LOG.warn("FlinkKafkaToClickHouseJob starting, preparing to initialize environment");
                StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
                env.getConfig().addDefaultKryoSerializer(SegmentObject.class, ProtobufSerializer.class);
                Map<String, Object> config = com.o11y.ConfigLoader.loadConfig("application.yaml");
                @SuppressWarnings("unchecked")
                Map<String, String> kafkaConfig = (Map<String, String>) config.get("kafka");
                @SuppressWarnings("unchecked")
                Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
                @SuppressWarnings("unchecked")
                Map<String, Integer> batchConfig = (Map<String, Integer>) config.get("batch");

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
                                WatermarkStrategy.noWatermarks(),
                                "KafkaSource-SegmentObject");
                LOG.warn("DataStream created, preparing to add Sink");

                // 自动注册所有算子（不再注册 SimpleClickHouseSinkOperator）
                OperatorRegistry.register(new AvgSpanDurationAggregateFunctionOperator());
                OperatorRegistry.register(new MaxSpanDurationAggregateFunctionOperator());
                OperatorRegistry.register(new DubboEntryAvgDurationAggregateFunctionOperator());

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

                // 启动定时任务同步 new_key 表到 events 表
                long addColumnsInterval = ((Number) config.get("add_columns_interval")).longValue();
                new NewKeyTableSyncTask(dbService, addColumnsInterval).start();

                for (FlinkOperator op : OperatorRegistry.getOperators()) {
                        Map<String, List<String>> params = OperatorParamLoader.loadParamList(dbService,
                                        op.getClass().getSimpleName());
                        op.apply(stream, params);
                }
                env.execute("FlinkKafkaToClickHouseJob");
        }
}
