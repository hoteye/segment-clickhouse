package com.o11y.integration;

import com.o11y.shared.util.ConfigurationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ClickHouseContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * 集成测试基类
 * 
 * 提供TestContainers环境、数据库连接、Kafka客户端等通用测试基础设施
 * 
 * @author Integration Test Team
 * @since 1.0.0
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class BaseIntegrationTest {

    protected static final Logger LOG = LoggerFactory.getLogger(BaseIntegrationTest.class);

    // TestContainers
    @Container
    protected static final ClickHouseContainer clickhouse = new ClickHouseContainer("yandex/clickhouse-server:22.8")
            .withInitScript("test-schema.sql")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    protected static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withStartupTimeout(Duration.ofMinutes(2));

    // 测试工具
    protected static ObjectMapper objectMapper;
    protected static Map<String, Object> testConfig;
    protected static Connection clickhouseConnection;
    protected static KafkaProducer<String, String> kafkaProducer;
    protected static KafkaConsumer<String, String> kafkaConsumer;

    @BeforeAll
    static void setUpContainers() throws Exception {
        LOG.info("=== 开始初始化集成测试环境 ===");

        // 等待容器启动
        await().atMost(Duration.ofMinutes(3)).until(() -> clickhouse.isRunning() && kafka.isRunning());
        LOG.info("TestContainers启动完成");

        // 初始化工具
        objectMapper = new ObjectMapper();

        // 更新测试配置
        updateTestConfiguration();

        // 初始化ClickHouse连接
        initClickHouseConnection();

        // 初始化Kafka客户端
        initKafkaClients();

        // 创建测试数据
        setupTestData();

        LOG.info("=== 集成测试环境初始化完成 ===");
    }

    @AfterAll
    static void tearDownContainers() throws Exception {
        LOG.info("=== 开始清理集成测试环境 ===");

        if (kafkaProducer != null)
            kafkaProducer.close();
        if (kafkaConsumer != null)
            kafkaConsumer.close();
        if (clickhouseConnection != null)
            clickhouseConnection.close();

        LOG.info("=== 集成测试环境清理完成 ===");
    }

    @BeforeEach
    void setUp() {
        LOG.info("开始执行测试用例: {}", getTestMethodName());
    }

    @AfterEach
    void tearDown() {
        LOG.info("完成执行测试用例: {}", getTestMethodName());
        // 清理测试数据
        cleanupTestData();
    }

    /**
     * 更新测试配置，使用TestContainers的动态端口
     */
    private static void updateTestConfiguration() throws Exception {
        testConfig = ConfigurationUtils.loadConfig("application-test.yaml");

        // 更新ClickHouse配置
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) testConfig.get("clickhouse");
        clickhouseConfig.put("url", clickhouse.getJdbcUrl());

        // 更新Kafka配置
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) testConfig.get("kafka");
        kafkaConfig.put("bootstrap_servers", kafka.getBootstrapServers());

        LOG.info("测试配置更新完成 - ClickHouse: {}, Kafka: {}",
                clickhouse.getJdbcUrl(), kafka.getBootstrapServers());
    }

    /**
     * 初始化ClickHouse连接
     */
    private static void initClickHouseConnection() throws Exception {
        clickhouseConnection = DriverManager.getConnection(
                clickhouse.getJdbcUrl(),
                clickhouse.getUsername(),
                clickhouse.getPassword());

        // 验证连接
        Statement stmt = clickhouseConnection.createStatement();
        var rs = stmt.executeQuery("SELECT 1");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(1);

        LOG.info("ClickHouse连接初始化完成");
    }

    /**
     * 初始化Kafka客户端
     */
    private static void initKafkaClients() {
        // Kafka Producer配置
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        kafkaProducer = new KafkaProducer<>(producerProps);

        // Kafka Consumer配置
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaConsumer = new KafkaConsumer<>(consumerProps);

        LOG.info("Kafka客户端初始化完成");
    }

    /**
     * 设置测试数据
     */
    private static void setupTestData() throws Exception {
        // 创建测试表
        createTestTables();

        // 插入基础测试数据
        insertBaseTestData();

        LOG.info("测试数据设置完成");
    }

    /**
     * 创建测试表
     */
    private static void createTestTables() throws Exception {
        Statement stmt = clickhouseConnection.createStatement();

        // 创建聚合结果表
        stmt.execute("CREATE TABLE IF NOT EXISTS flink_operator_agg_result (" +
                "window_start DateTime64(3), " +
                "windowSize Int32, " +
                "operator_class String, " +
                "operator_name String, " +
                "service String, " +
                "avg_duration Float64, " +
                "max_duration Int64, " +
                "error_rate Float64, " +
                "total_count Int64, " +
                "error_count Int64, " +
                "success_count Int64" +
                ") ENGINE = MergeTree() ORDER BY window_start");

        // 创建小时级规则表
        stmt.execute("CREATE TABLE IF NOT EXISTS hourly_alarm_rules (" +
                "hour_of_day UInt8, " +
                "service String, " +
                "operator_name String, " +
                "operator_class String, " +
                "avg_duration_low Float64, " +
                "avg_duration_mid Float64, " +
                "avg_duration_high Float64, " +
                "max_duration_low Float64, " +
                "max_duration_mid Float64, " +
                "max_duration_high Float64, " +
                "success_rate_low Float64, " +
                "success_rate_mid Float64, " +
                "success_rate_high Float64, " +
                "traffic_volume_low Float64, " +
                "traffic_volume_mid Float64, " +
                "traffic_volume_high Float64, " +
                "alarm_template String, " +
                "analysis_days UInt8, " +
                "sample_count UInt32, " +
                "generated_time DateTime, " +
                "last_updated DateTime, " +
                "version UInt32, " +
                "PRIMARY KEY (hour_of_day, service, operator_name)" +
                ") ENGINE = MergeTree() ORDER BY (hour_of_day, service, operator_name)");

        LOG.info("测试表创建完成");
    }

    /**
     * 插入基础测试数据
     */
    private static void insertBaseTestData() throws Exception {
        Statement stmt = clickhouseConnection.createStatement();

        // 插入7天历史聚合数据
        for (int day = 0; day < 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                double baseAvgDuration = getBaseAvgDuration(hour);
                double baseMaxDuration = getBaseMaxDuration(hour);
                double errorRate = getBaseErrorRate(hour);
                int totalCount = getBaseTotalCount(hour);

                stmt.execute(String.format(
                        "INSERT INTO flink_operator_agg_result VALUES " +
                                "(toDateTime64(now() - INTERVAL %d DAY - INTERVAL %d HOUR, 3), " +
                                "22, 'TestOperator', 'testMethod', 'test-service', " +
                                "%.2f, %d, %.4f, %d, %d, %d)",
                        day, hour, baseAvgDuration, (long) baseMaxDuration, errorRate,
                        totalCount, (int) (totalCount * errorRate), (int) (totalCount * (1 - errorRate))));
            }
        }

        LOG.info("基础测试数据插入完成");
    }

    /**
     * 清理测试数据
     */
    private void cleanupTestData() {
        try {
            // 清理测试数据 - 使用测试专用表，避免影响生产数据
            Statement stmt = clickhouseConnection.createStatement();

            // 创建测试专用表（如果不存在）
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS test_hourly_alarm_rules (" +
                            "hour_of_day Int8, service String, operator_name String, operator_class String, " +
                            "avg_duration_low Float64, avg_duration_mid Float64, avg_duration_high Float64, " +
                            "max_duration_low Float64, max_duration_mid Float64, max_duration_high Float64, " +
                            "success_rate_low Float64, success_rate_mid Float64, success_rate_high Float64, " +
                            "traffic_volume_low Float64, traffic_volume_mid Float64, traffic_volume_high Float64, " +
                            "alarm_template String, analysis_days Int8, sample_count Int32, " +
                            "generated_time DateTime, last_updated DateTime, version Int32" +
                            ") ENGINE = MergeTree() ORDER BY (hour_of_day, service, operator_name)");

            // 清空测试表而不是生产表
            stmt.execute("TRUNCATE TABLE test_hourly_alarm_rules");

            LOG.debug("测试数据清理完成");
        } catch (Exception e) {
            LOG.warn("清理测试数据失败", e);
        }
    }

    /**
     * 获取测试方法名
     */
    private String getTestMethodName() {
        return Thread.currentThread().getStackTrace()[3].getMethodName();
    }

    // 辅助方法：生成不同时段的基础数据
    private static double getBaseAvgDuration(int hour) {
        // 高峰期(9-12, 14-18): 较高延迟
        if ((hour >= 9 && hour <= 12) || (hour >= 14 && hour <= 18)) {
            return 150.0 + Math.random() * 50;
        }
        // 低峰期(0-6, 22-24): 较低延迟
        else if (hour <= 6 || hour >= 22) {
            return 80.0 + Math.random() * 30;
        }
        // 平峰期: 中等延迟
        else {
            return 120.0 + Math.random() * 40;
        }
    }

    private static double getBaseMaxDuration(int hour) {
        return getBaseAvgDuration(hour) * (3.0 + Math.random() * 2.0);
    }

    private static double getBaseErrorRate(int hour) {
        return 0.01 + Math.random() * 0.02; // 1-3%错误率
    }

    private static int getBaseTotalCount(int hour) {
        // 高峰期更多交易
        if ((hour >= 9 && hour <= 12) || (hour >= 14 && hour <= 18)) {
            return 800 + (int) (Math.random() * 400);
        }
        // 低峰期较少交易
        else if (hour <= 6 || hour >= 22) {
            return 100 + (int) (Math.random() * 100);
        }
        // 平峰期中等交易
        else {
            return 400 + (int) (Math.random() * 200);
        }
    }

    // Getter方法供子类使用
    protected Map<String, Object> getTestConfig() {
        return testConfig;
    }

    protected Connection getClickHouseConnection() {
        return clickhouseConnection;
    }

    protected KafkaProducer<String, String> getKafkaProducer() {
        return kafkaProducer;
    }

    protected KafkaConsumer<String, String> getKafkaConsumer() {
        return kafkaConsumer;
    }

    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}