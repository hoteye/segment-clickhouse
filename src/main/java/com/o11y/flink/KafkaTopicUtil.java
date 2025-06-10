package com.o11y.flink;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class KafkaTopicUtil {
    public static void recreateTopic(String bootstrapServers, String topic, int partitions, short replicationFactor)
            throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(props)) {
            // 删除topic
            DeleteTopicsResult deleteResult = adminClient.deleteTopics(Collections.singletonList(topic));
            try {
                deleteResult.all().get();
                System.out.println("Topic '" + topic + "' deleted.");
            } catch (ExecutionException e) {
                if (e.getCause() != null && e.getCause().getMessage().contains("UnknownTopicOrPartitionException")) {
                    System.out.println("Topic '" + topic + "' does not exist, skip delete.");
                } else {
                    throw e;
                }
            }
            // 等待topic真正被删除
            int retry = 0;
            while (adminClient.listTopics().names().get().contains(topic) && retry < 20) {
                Thread.sleep(1000);
                retry++;
                System.out.println("Waiting for topic '" + topic + "' to be deleted...");
            }
            // 再次检查是否还存在，存在则直接返回或抛异常
            if (adminClient.listTopics().names().get().contains(topic)) {
                System.err.println("Topic '" + topic + "' still exists after waiting, abort create.");
                return;
            }
            // 创建topic，带重试
            int createRetry = 0;
            while (true) {
                try {
                    NewTopic newTopic = new NewTopic(topic, partitions, replicationFactor);
                    adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
                    System.out.println("Topic '" + topic + "' created with " + partitions + " partitions.");
                    break;
                } catch (ExecutionException ce) {
                    if (ce.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException
                            && createRetry < 10) {
                        createRetry++;
                        System.out.println(
                                "Topic still exists when creating, retrying in 1s... (" + createRetry + "/10)");
                        Thread.sleep(1000);
                    } else {
                        throw ce;
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String bootstrapServers = "localhost:9092";
        String topic = "test_flink_task";
        int partitions = 2;
        short replicationFactor = 1;
        recreateTopic(bootstrapServers, topic, partitions, replicationFactor);
    }
}
