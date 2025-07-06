package com.o11y.infrastructure.flink;

import com.o11y.stream.source.SegmentDeserializationSchema;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多Kafka源管理器
 * 
 * <p>
 * 专门管理多个数据源Kafka连接，用于消费不同生产环境的segmentObject数据。
 * 主Kafka源（用于控制消息）由FlinkService单独管理。
 * 
 * <p>
 * <strong>主要功能：</strong>
 * <ul>
 * <li>管理多个数据源Kafka连接</li>
 * <li>为每个源设置独立的水位线和时间戳分配</li>
 * <li>合并多个数据流</li>
 * <li>支持源级别的监控和错误处理</li>
 * <li>支持动态启用/禁用数据源</li>
 * </ul>
 * 
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class MultiKafkaSourceManager {

    private static final Logger LOG = LoggerFactory.getLogger(MultiKafkaSourceManager.class);
    private final StreamExecutionEnvironment env;
    private final Map<String, Object> kafkaConfig;

    public MultiKafkaSourceManager(StreamExecutionEnvironment env, Map<String, Object> kafkaConfig) {
        this.env = env;
        this.kafkaConfig = kafkaConfig;
    }

    /**
     * 构建多个数据源Kafka连接并合并成一个统一的DataStream
     * 
     * @return 合并后的SegmentObject DataStream
     */
    public DataStream<SegmentObject> buildMultiSourceStream() {
        @SuppressWarnings("unchecked")
        Map<String, Object> dataSourcesConfig = (Map<String, Object>) kafkaConfig.get("data_sources");

        if (dataSourcesConfig == null || dataSourcesConfig.isEmpty()) {
            LOG.warn("未配置多Kafka数据源，使用默认单源配置");
            return buildDefaultSourceStream();
        }

        List<DataStream<SegmentObject>> sourceStreams = new ArrayList<>();

        // 为每个配置的数据源创建独立的DataStream
        for (Map.Entry<String, Object> entry : dataSourcesConfig.entrySet()) {
            String sourceName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceConfig = (Map<String, Object>) entry.getValue();

            // 检查源是否启用
            Boolean enabled = (Boolean) sourceConfig.get("enabled");
            if (enabled == null || !enabled) {
                LOG.info("数据源 {} 未启用，跳过", sourceName);
                continue;
            }

            try {
                DataStream<SegmentObject> sourceStream = buildKafkaSource(sourceName, sourceConfig);
                sourceStreams.add(sourceStream);
                LOG.info("成功创建数据源Kafka连接: {}", sourceName);
            } catch (Exception e) {
                LOG.error("创建数据源Kafka连接失败: {}, 错误: {}", sourceName, e.getMessage(), e);
            }
        }

        if (sourceStreams.isEmpty()) {
            LOG.error("没有成功创建任何数据源Kafka连接，使用默认配置");
            return buildDefaultSourceStream();
        }

        // 合并所有数据流
        DataStream<SegmentObject> mergedStream = sourceStreams.get(0);
        for (int i = 1; i < sourceStreams.size(); i++) {
            mergedStream = mergedStream.union(sourceStreams.get(i));
        }

        LOG.info("成功合并 {} 个数据源Kafka连接", sourceStreams.size());
        return mergedStream;
    }

    /**
     * 构建默认数据源（兼容原有配置）
     */
    private DataStream<SegmentObject> buildDefaultSourceStream() {
        KafkaSource<SegmentObject> kafkaSource = KafkaSource.<SegmentObject>builder()
                .setBootstrapServers((String) kafkaConfig.get("bootstrap_servers"))
                .setTopics((String) kafkaConfig.get("topic"))
                .setGroupId((String) kafkaConfig.get("group_id"))
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
                .setValueOnlyDeserializer(new SegmentDeserializationSchema())
                .build();

        return env.fromSource(
                kafkaSource,
                createWatermarkStrategy(),
                "DefaultKafkaSource-SegmentObject");
    }

    /**
     * 为指定的数据源配置创建DataStream
     */
    private DataStream<SegmentObject> buildKafkaSource(String sourceName, Map<String, Object> sourceConfig) {
        KafkaSource<SegmentObject> kafkaSource = KafkaSource.<SegmentObject>builder()
                .setBootstrapServers((String) sourceConfig.get("bootstrap_servers"))
                .setTopics((String) sourceConfig.get("topic"))
                .setGroupId((String) sourceConfig.get("group_id"))
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
                .setValueOnlyDeserializer(new SegmentDeserializationSchema())
                .build();

        return env.fromSource(
                kafkaSource,
                createWatermarkStrategy(),
                sourceName + "-SegmentObject");
    }

    /**
     * 创建水位线策略
     */
    private WatermarkStrategy<SegmentObject> createWatermarkStrategy() {
        return WatermarkStrategy.<SegmentObject>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                .withTimestampAssigner((segment, ts) -> {
                    for (SpanObject span : segment.getSpansList()) {
                        if (span.getSpanType() == SpanType.Entry) {
                            return span.getEndTime();
                        }
                    }
                    return System.currentTimeMillis();
                });
    }

    /**
     * 获取所有启用的数据源名称列表
     */
    public List<String> getEnabledSourceNames() {
        @SuppressWarnings("unchecked")
        Map<String, Object> dataSourcesConfig = (Map<String, Object>) kafkaConfig.get("data_sources");

        if (dataSourcesConfig == null) {
            return new ArrayList<>();
        }

        List<String> enabledSources = new ArrayList<>();
        for (Map.Entry<String, Object> entry : dataSourcesConfig.entrySet()) {
            String sourceName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceConfig = (Map<String, Object>) entry.getValue();

            Boolean enabled = (Boolean) sourceConfig.get("enabled");
            if (enabled != null && enabled) {
                enabledSources.add(sourceName);
            }
        }

        return enabledSources;
    }

    /**
     * 获取所有配置的数据源名称列表（包括未启用的）
     */
    public List<String> getAllSourceNames() {
        @SuppressWarnings("unchecked")
        Map<String, Object> dataSourcesConfig = (Map<String, Object>) kafkaConfig.get("data_sources");

        if (dataSourcesConfig == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(dataSourcesConfig.keySet());
    }

    /**
     * 验证数据源配置的有效性
     */
    public boolean validateSourceConfig(String sourceName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> dataSourcesConfig = (Map<String, Object>) kafkaConfig.get("data_sources");

        if (dataSourcesConfig == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) dataSourcesConfig.get(sourceName);

        if (sourceConfig == null) {
            return false;
        }

        // 检查必需的配置项
        String[] requiredFields = { "bootstrap_servers", "topic", "group_id" };
        for (String field : requiredFields) {
            if (sourceConfig.get(field) == null || sourceConfig.get(field).toString().trim().isEmpty()) {
                LOG.error("数据源 {} 缺少必需配置: {}", sourceName, field);
                return false;
            }
        }

        return true;
    }

    /**
     * 获取数据源描述信息
     */
    public String getSourceDescription(String sourceName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> dataSourcesConfig = (Map<String, Object>) kafkaConfig.get("data_sources");

        if (dataSourcesConfig == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) dataSourcesConfig.get(sourceName);

        if (sourceConfig == null) {
            return null;
        }

        return (String) sourceConfig.get("description");
    }

    /**
     * 检查数据源是否启用
     */
    public boolean isSourceEnabled(String sourceName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> dataSourcesConfig = (Map<String, Object>) kafkaConfig.get("data_sources");

        if (dataSourcesConfig == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) dataSourcesConfig.get(sourceName);

        if (sourceConfig == null) {
            return false;
        }

        Boolean enabled = (Boolean) sourceConfig.get("enabled");
        return enabled != null && enabled;
    }
}