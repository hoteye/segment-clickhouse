package com.o11y.infrastructure.flink;

import com.o11y.domain.model.alarm.AlarmRule;
import com.o11y.stream.source.AlarmRuleDeserializationSchema;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import java.util.Map;

/**
 * 告警规则广播流工厂类，负责构建和配置告警规则的 Kafka 数据源和广播流。
 * 
 * <p>
 * <strong>核心功能：</strong>
 * <ul>
 * <li>从 Kafka alarm_rule_topic 消费告警规则配置</li>
 * <li>支持规则的批量更新和实时分发</li>
 * <li>确保规则在集群中的一致性广播</li>
 * <li>处理规则反序列化异常和数据清洗</li>
 * </ul>
 * 
 * <p>
 * <strong>Kafka Topic 数据保留策略配置：</strong><br>
 * 为了确保 alarm_rule_topic 至少保留一条最新的告警规则数据，需要配置以下 Kafka 参数：
 * 
 * <pre>{@code
 * # 创建或修改 alarm_rule_topic，启用 log compaction
 * kafka-topics.sh --bootstrap-server localhost:9092 \
 *   --alter --topic alarm_rule_topic \
 *   --config cleanup.policy=compact \
 *   --config min.cleanable.dirty.ratio=0.1 \
 *   --config segment.ms=60000 \
 *   --config delete.retention.ms=86400000
 * 
 * # 或者在创建时直接配置
 * kafka-topics.sh --bootstrap-server localhost:9092 \
 *   --create --topic alarm_rule_topic \
 *   --partitions 1 --replication-factor 1 \
 *   --config cleanup.policy=compact \
 *   --config min.cleanable.dirty.ratio=0.1 \
 *   --config segment.ms=60000
 * }</pre>
 * 
 * <p>
 * <strong>关键配置参数说明：</strong>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr>
 * <th>参数</th>
 * <th>推荐值</th>
 * <th>说明</th>
 * </tr>
 * <tr>
 * <td>cleanup.policy</td>
 * <td>compact</td>
 * <td>启用日志压缩，保留每个 key 的最新值</td>
 * </tr>
 * <tr>
 * <td>min.cleanable.dirty.ratio</td>
 * <td>0.1</td>
 * <td>当脏数据比例达到10%时触发压缩</td>
 * </tr>
 * <tr>
 * <td>segment.ms</td>
 * <td>60000</td>
 * <td>每60秒创建新的日志段，便于及时压缩</td>
 * </tr>
 * <tr>
 * <td>delete.retention.ms</td>
 * <td>86400000</td>
 * <td>删除标记保留24小时</td>
 * </tr>
 * <tr>
 * <td>partitions</td>
 * <td>1</td>
 * <td>单分区确保消费顺序和规则一致性</td>
 * </tr>
 * </table>
 * 
 * <p>
 * <strong>数据生产端配置要求：</strong>
 * <ul>
 * <li><strong>固定 Key：</strong>所有规则数据使用相同的 key（如 "global_rules"）</li>
 * <li><strong>完整规则集：</strong>每次发送完整的规则映射，而非增量更新</li>
 * <li><strong>序列化格式：</strong>使用 JSON 或 Avro 确保数据一致性</li>
 * <li><strong>幂等生产：</strong>启用生产者幂等性，避免重复数据</li>
 * </ul>
 * 
 * <p>
 * <strong>示例生产代码：</strong>
 * 
 * <pre>{@code
 * // 生产端示例
 * Properties props = new Properties();
 * props.put("bootstrap.servers", "localhost:9092");
 * props.put("key.serializer", StringSerializer.class);
 * props.put("value.serializer", StringSerializer.class);
 * props.put("enable.idempotence", true);
 * 
 * KafkaProducer<String, String> producer = new KafkaProducer<>(props);
 * 
 * // 使用固定 key 确保压缩生效
 * String key = "global_alarm_rules";
 * String value = JsonUtils.toJson(allAlarmRules);
 * 
 * ProducerRecord<String, String> record = new ProducerRecord<>("alarm_rule_topic", key, value);
 * producer.send(record);
 * }</pre>
 * 
 * @see AlarmRuleDeserializationSchema 规则反序列化器
 * @see AggAlertBroadcastFunction 规则广播处理函数
 * @author DDD Architecture Team
 * @since 1.0.0
 */

public class RuleBroadcastStreamFactory {
        /**
         * 构建告警规则广播流，支持 Kafka Topic 压缩策略以确保至少保留一条最新规则数据。
         * 
         * <p>
         * <strong>方法功能：</strong>
         * <ul>
         * <li>从 Kafka alarm_rule_topic 构建规则数据源</li>
         * <li>配置消费者以支持压缩 Topic 的最佳实践</li>
         * <li>创建广播流确保规则在所有并行实例中一致</li>
         * <li>处理反序列化异常和数据质量保证</li>
         * </ul>
         * 
         * <p>
         * <strong>Kafka 消费策略详解：</strong>
         * <table border="1" cellpadding="5" cellspacing="0">
         * <tr>
         * <th>配置项</th>
         * <th>当前值</th>
         * <th>压缩 Topic 优化建议</th>
         * </tr>
         * <tr>
         * <td>setStartingOffsets</td>
         * <td>earliest()</td>
         * <td>✅ 适合压缩 Topic，确保读取到至少一条保留的数据</td>
         * </tr>
         * <tr>
         * <td>setGroupId</td>
         * <td>alarm-rule-consumer-group</td>
         * <td>✅ 固定消费组，支持 offset 管理</td>
         * </tr>
         * <tr>
         * <td>setParallelism</td>
         * <td>1</td>
         * <td>✅ 单并行度确保规则消费的顺序性</td>
         * </tr>
         * <tr>
         * <td>setTopics</td>
         * <td>alarm_rule_topic</td>
         * <td>✅ 单 Topic 单分区配置</td>
         * </tr>
         * </table>
         * 
         * <p>
         * <strong>压缩 Topic 消费特点：</strong>
         * <ul>
         * <li><strong>最新数据保证：</strong>Kafka Log Compaction 确保每个 key 的最新值被保留</li>
         * <li><strong>启动时读取：</strong>earliest() 策略确保消费者启动时能读取到压缩后的最新规则</li>
         * <li><strong>增量更新：</strong>新规则发布时，旧规则会被压缩，只保留最新版本</li>
         * <li><strong>故障恢复：</strong>消费者重启后能从最新的压缩数据开始消费</li>
         * </ul>
         * 
         * <p>
         * <strong>数据流处理链路：</strong>
         * 
         * <pre>
         * Kafka alarm_rule_topic (压缩策略)
         *        ↓ (earliest offset 策略)
         * KafkaSource (单并行度消费)
         *        ↓ (反序列化)
         * AlarmRuleDeserializationSchema
         *        ↓ (数据清洗)
         * filter(非 null 数据)
         *        ↓ (状态广播)
         * BroadcastStream (所有算子实例共享)
         * </pre>
         * 
         * <p>
         * <strong>运维建议：</strong>
         * <ul>
         * <li><strong>监控压缩效果：</strong>定期检查 Topic 的日志段数量和压缩比例</li>
         * <li><strong>规则版本管理：</strong>在规则数据中包含版本号和时间戳</li>
         * <li><strong>消费延迟监控：</strong>监控消费者的 lag，确保规则及时生效</li>
         * <li><strong>序列化兼容性：</strong>确保规则格式的向后兼容性</li>
         * </ul>
         * 
         * <p>
         * <strong>Kafka Topic 检查命令：</strong>
         * 
         * <pre>{@code
         * # 检查 Topic 配置
         * kafka-topics.sh --bootstrap-server localhost:9092 \
         *   --describe --topic alarm_rule_topic
         * 
         * # 检查压缩效果
         * kafka-run-class.sh kafka.tools.DumpLogSegments \
         *   --files /var/kafka-logs/alarm_rule_topic-0/*.log \
         *   --print-data-log
         * 
         * # 监控消费者状态
         * kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
         *   --describe --group alarm-rule-consumer-group
         * }</pre>
         * 
         * <p>
         * <strong>故障排查指南：</strong>
         * <ul>
         * <li><strong>规则未生效：</strong>检查 Kafka 消费者 lag 和广播状态</li>
         * <li><strong>规则冲突：</strong>验证压缩策略是否正确配置</li>
         * <li><strong>反序列化失败：</strong>检查规则数据格式和 schema 兼容性</li>
         * <li><strong>启动无规则：</strong>确认 Topic 中至少有一条压缩后的数据</li>
         * </ul>
         * 
         * @param env                 Flink 流执行环境
         * @param kafkaConfig         Kafka 配置映射，包含
         *                            bootstrap_servers、alarm_rule_topic、alarm_rule_group_id
         * @param ruleStateDescriptor 规则状态描述符，用于广播状态管理
         * @return 告警规则广播流，支持在所有算子实例间共享规则状态
         * 
         * @see OffsetsInitializer#earliest() 从最早可用 offset 开始消费，适合压缩 Topic
         * @see AlarmRuleDeserializationSchema 规则反序列化器，处理 JSON 到对象转换
         * @see DataStream#filter(FilterFunction) 数据质量保证，过滤无效规则
         * @see DataStream#broadcast(MapStateDescriptor) 创建广播流进行状态共享
         */
        public static BroadcastStream<Map<String, AlarmRule>> buildRuleBroadcastStream(
                        StreamExecutionEnvironment env,
                        Map<String, String> kafkaConfig,
                        MapStateDescriptor<String, Map<String, AlarmRule>> ruleStateDescriptor) {
                KafkaSource<Map<String, AlarmRule>> alarmRuleKafkaSource = KafkaSource.<Map<String, AlarmRule>>builder()
                                .setBootstrapServers(kafkaConfig.get("bootstrap_servers"))
                                .setTopics(kafkaConfig.get("alarm_rule_topic"))
                                .setGroupId(kafkaConfig.get("alarm_rule_group_id"))
                                .setStartingOffsets(OffsetsInitializer.earliest())
                                .setValueOnlyDeserializer(new AlarmRuleDeserializationSchema())
                                .build();
                DataStream<Map<String, AlarmRule>> ruleStream = env.fromSource(
                                alarmRuleKafkaSource,
                                WatermarkStrategy.noWatermarks(),
                                "KafkaSource-AlarmRule")
                                .setParallelism(1); // 设置规则流的并行度为1，确保只有一个实例消费规则
                // 增加 null 过滤，跳过反序列化失败的规则
                ruleStream = ruleStream.filter(ruleMap -> ruleMap != null);
                return ruleStream.broadcast(ruleStateDescriptor);
        }
}
