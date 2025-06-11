package com.o11y.flink.operator;

import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.ConfigLoader;
import com.o11y.DatabaseService;
import com.o11y.flink.util.OperatorParamLoader;

/**
 * 通用参数热更新基类，所有需要参数热更新的算子继承本类即可。
 */
public abstract class AbstractParamUpdatableOperator<T, O> extends RichFlatMapFunction<T, O> implements FlinkOperator {
    protected volatile Map<String, List<String>> params;
    private transient Thread paramUpdateThread;
    protected Logger LOG = LoggerFactory.getLogger(getClass());

    protected abstract DatabaseService getDbService();

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // 读取 application.yaml 配置
        Map<String, Object> config = ConfigLoader.loadConfig("application.yaml");
        Map<String, String> kafkaConfig = (Map<String, String>) config.get("kafka");
        String kafkaBootstrapServers = kafkaConfig.get("bootstrap_servers");
        String paramUpdateTopic = kafkaConfig.get("param_update_topic");
        // 启动 KafkaConsumer 监听参数变更
        paramUpdateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Properties props = new Properties();
                props.put("bootstrap.servers", kafkaBootstrapServers);
                props.put("group.id", getName() + "-param-update");
                props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
                props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
                KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
                consumer.subscribe(Collections.singletonList(paramUpdateTopic));
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        ConsumerRecords<String, String> records = consumer.poll(java.time.Duration.ofSeconds(1));
                        for (ConsumerRecord<String, String> record : records) {
                            if (record.key().equals(getName())) {
                                // 重新加载参数
                                Map<String, List<String>> newParams = OperatorParamLoader.loadParamList(getDbService(),
                                        getName());
                                onParamUpdate(newParams);
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("KafkaConsumer error, will retry", e);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                }
                consumer.close();

            }
        });
        paramUpdateThread.setDaemon(true);
        paramUpdateThread.start();
    }

    @Override
    public void close() throws Exception {
        if (paramUpdateThread != null) {
            paramUpdateThread.interrupt();
        }
        super.close();
    }

    @Override
    public void onParamUpdate(Map<String, List<String>> newParams) {
        this.params = newParams;
        LOG.info("参数热更新: {}", newParams);
    }
}
