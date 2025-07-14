package com.o11y.integration;

import com.o11y.shared.util.ConfigurationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 提供数据库连接、Kafka客户端等通用测试基础设施
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class BaseIntegrationTest {

    protected static final Logger LOG = LoggerFactory.getLogger(BaseIntegrationTest.class);

    // 测试工具
    protected static ObjectMapper objectMapper;
    protected static Map<String, Object> testConfig;
    protected static Connection clickhouseConnection;
    protected static KafkaProducer<String, String> kafkaProducer;
    protected static KafkaConsumer<String, String> kafkaConsumer;

    @BeforeAll
    static void setUpClass() throws Exception {
        LOG.info("=== 开始初始化集成测试环境 ===");

        // 初始化工具
        objectMapper = new ObjectMapper();

        // 加载测试配置
        testConfig = ConfigurationUtils.loadConfig("application-test.yaml");

        // 安全检查：确保不是生产环境
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) testConfig.get("clickhouse");
        String url = clickhouseConfig.get("url");
        if (url.contains("192.168.100.6") || url.contains("production") || url.contains("prod")) {
            throw new RuntimeException("禁止在生产环境运行集成测试！当前URL: " + url);
        }

        // 初始化ClickHouse连接
        initClickHouseConnection();

        // 初始化Kafka客户端
        initKafkaClients();

        // 创建测试数据
        setupTestData();

        LOG.info("=== 集成测试环境初始化完成 ===");
    }

    @AfterAll
    static void tearDownClass() throws Exception {
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
     * 初始化ClickHouse连接
     */
    private static void initClickHouseConnection() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) testConfig.get("clickhouse");
        String url = clickhouseConfig.get("url").replace("/default", "/integration");
        String username = clickhouseConfig.get("username");
        String password = clickhouseConfig.get("password");

        // 创建integration数据库
        try (Connection tempConn = DriverManager.getConnection(
                clickhouseConfig.get("url"), username, password)) {
            Statement stmt = tempConn.createStatement();
            stmt.execute("CREATE DATABASE IF NOT EXISTS integration");
        }

        // 连接到integration数据库
        clickhouseConnection = DriverManager.getConnection(url, username, password);

        // 验证连接
        Statement stmt = clickhouseConnection.createStatement();
        var rs = stmt.executeQuery("SELECT 1");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(1);

        LOG.info("ClickHouse连接初始化完成: {}", url);
    }

    /**
     * 初始化Kafka客户端
     */
    private static void initKafkaClients() {
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) testConfig.get("kafka");
        String bootstrapServers = kafkaConfig.get("bootstrap_servers");

        // Kafka Producer配置
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        kafkaProducer = new KafkaProducer<>(producerProps);

        // Kafka Consumer配置
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaConsumer = new KafkaConsumer<>(consumerProps);

        LOG.info("Kafka客户端初始化完成: {}", bootstrapServers);
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

        // 创建events表
        stmt.execute("CREATE TABLE IF NOT EXISTS integration.events (" +
                "trace_id String, " +
                "trace_segment_id String, " +
                "service Nullable(String), " +
                "service_instance Nullable(String), " +
                "is_size_limited Nullable(UInt8), " +
                "span_id Int32, " +
                "parent_span_id Int32, " +
                "start_time DateTime64(3), " +
                "end_time DateTime64(3), " +
                "operation_name Nullable(String), " +
                "peer Nullable(String), " +
                "span_type Nullable(String), " +
                "span_layer Nullable(String), " +
                "component_id Nullable(Int32), " +
                "is_error Nullable(UInt8), " +
                "skip_analysis Nullable(UInt8), " +
                "refs_ref_type Nullable(String), " +
                "refs_trace_id Nullable(String), " +
                "refs_parent_trace_segment_id Nullable(String), " +
                "refs_parent_span_id Nullable(Int32), " +
                "refs_parent_service Nullable(String), " +
                "refs_parent_service_instance Nullable(String), " +
                "refs_parent_endpoint Nullable(String), " +
                "refs_network_address_used_at_peer Nullable(String), " +
                "tag_status_code Nullable(String), " +
                "tag_method_access_count_type_int64 Nullable(Int64), " +
                "log_error_kind Nullable(String), " +
                "log_event Nullable(String), " +
                "log_forward_url Nullable(String), " +
                "log_message Nullable(String), " +
                "log_stack Nullable(String), " +
                "tag_Available_Memory_type_Int64 Nullable(Int64), " +
                "tag_Processor_Name Nullable(String), " +
                "tag_Total_Memory_type_Int64 Nullable(Int64), " +
                "tag_application_name Nullable(String), " +
                "tag_db_instance Nullable(String), " +
                "tag_db_statement Nullable(String), " +
                "tag_db_type Nullable(String), " +
                "tag_dc Nullable(String), " +
                "tag_dubbo_local_host Nullable(String), " +
                "tag_dubbo_remote_host Nullable(String), " +
                "tag_dubbo_remote_port Nullable(String), " +
                "tag_env_APP_VERSION Nullable(String), " +
                "tag_env_HOME Nullable(String), " +
                "tag_env_HOSTNAME Nullable(String), " +
                "tag_env_JAVA_HOME Nullable(String), " +
                "tag_env_JAVA_VERSION Nullable(String), " +
                "tag_env_LANG Nullable(String), " +
                "tag_env_PATH Nullable(String), " +
                "tag_env_TZ Nullable(String), " +
                "tag_error_context Nullable(String), " +
                "tag_error_message Nullable(String), " +
                "tag_error_method Nullable(String), " +
                "tag_error_parameters Nullable(String), " +
                "tag_error_stack_trace Nullable(String), " +
                "tag_error_type Nullable(String), " +
                "tag_http_method Nullable(String), " +
                "tag_http_status_code Nullable(String), " +
                "tag_http_url Nullable(String), " +
                "tag_jvm_heap_committed_type_Int64 Nullable(Int64), " +
                "tag_jvm_heap_init_type_Int64 Nullable(Int64), " +
                "tag_jvm_heap_max_type_Int64 Nullable(Int64), " +
                "tag_jvm_heap_used_type_Int64 Nullable(Int64), " +
                "tag_jvm_name Nullable(String), " +
                "tag_jvm_nonheap_committed_type_Int64 Nullable(Int64), " +
                "tag_jvm_nonheap_init_type_Int64 Nullable(Int64), " +
                "tag_jvm_nonheap_max_type_Int64 Nullable(Int64), " +
                "tag_jvm_nonheap_used_type_Int64 Nullable(Int64), " +
                "tag_jvm_start_time Nullable(String), " +
                "tag_jvm_uptime Nullable(String), " +
                "tag_jvm_vendor Nullable(String), " +
                "tag_jvm_version Nullable(String), " +
                "tag_methodName Nullable(String), " +
                "tag_method_result Nullable(String), " +
                "tag_os_arch Nullable(String), " +
                "tag_os_name Nullable(String), " +
                "tag_os_version Nullable(String), " +
                "tag_rpc_context_input Nullable(String), " +
                "tag_rpc_context_remote_application Nullable(String), " +
                "tag_rpc_context_sw8 Nullable(String), " +
                "tag_rpc_context_sw8_correlation Nullable(String), " +
                "tag_rpc_context_sw8_x Nullable(String), " +
                "tag_rpc_method_name Nullable(String), " +
                "tag_rpc_object_attachment_input Nullable(String), " +
                "tag_rpc_object_attachment_remote_application Nullable(String), " +
                "tag_rpc_object_attachment_sw8 Nullable(String), " +
                "tag_rpc_object_attachment_sw8_correlation Nullable(String), " +
                "tag_rpc_object_attachment_sw8_x Nullable(String), " +
                "tag_rpc_protocol Nullable(String), " +
                "tag_rpc_role Nullable(String), " +
                "tag_rpc_service_interface Nullable(String), " +
                "tag_rpc_service_url Nullable(String), " +
                "tag_sys Nullable(String), " +
                "tag_thread_count_type_Int64 Nullable(Int64), " +
                "tag_thread_current_cpu_time_type_Int64 Nullable(Int64), " +
                "tag_thread_current_user_time_type_Int64 Nullable(Int64), " +
                "tag_thread_daemon_count_type_Int64 Nullable(Int64), " +
                "tag_thread_peak_count_type_Int64 Nullable(Int64), " +
                "tag_thread_total_started_count_type_Int64 Nullable(Int64), " +
                "tag_url Nullable(String)" +
                ") ENGINE = MergeTree() " +
                "PARTITION BY toDate(start_time) " +
                "ORDER BY (start_time, trace_id)");

        // 创建聚合结果表
        stmt.execute("CREATE TABLE IF NOT EXISTS integration.flink_operator_agg_result (" +
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
        stmt.execute("CREATE TABLE IF NOT EXISTS integration.hourly_alarm_rules (" +
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

        // 插入events表测试数据 - 每个小时插入足够的样本
        // 为了提高测试效率，我们只插入最近2天的数据，每小时20条记录
        for (int day = 0; day < 2; day++) {
            for (int hour = 0; hour < 24; hour++) {
                // 批量插入以提高效率
                StringBuilder batchInsert = new StringBuilder();
                batchInsert.append("INSERT INTO integration.events (" +
                        "trace_id, trace_segment_id, service, service_instance, " +
                        "span_id, parent_span_id, start_time, end_time, " +
                        "operation_name, is_error, span_type) VALUES ");

                // 每个服务插入20条记录（减少数据量但保持统计意义）
                for (int serviceId = 0; serviceId < 3; serviceId++) {
                    String service = String.format("test-service-%d", serviceId);
                    
                    // 每个操作插入20条记录
                    for (int opId = 0; opId < 5; opId++) {
                        String operationName = String.format("operation-%d", opId);
                        
                        for (int i = 0; i < 20; i++) {
                            if (serviceId > 0 || opId > 0 || i > 0) {
                                batchInsert.append(",");
                            }

                            // 根据小时计算基准响应时间（模拟真实场景的波动）
                            double baseDuration;
                            if (hour >= 0 && hour <= 6) { // 凌晨时段
                                baseDuration = 100.0; // 较快响应
                            } else if (hour >= 7 && hour <= 9 || hour >= 17 && hour <= 19) { // 高峰时段
                                baseDuration = 300.0; // 较慢响应
                            } else { // 普通时段
                                baseDuration = 200.0; // 正常响应
                            }
                            
                            // 在基准值上下浮动20%
                            double duration = baseDuration * (0.8 + Math.random() * 0.4);
                            
                            // 计算开始和结束时间，确保在合理范围内
                            String startTime = String.format(
                                "now() - INTERVAL %d DAY - INTERVAL %d HOUR - INTERVAL %d MINUTE",
                                day, hour, i % 60);
                            String endTime = String.format(
                                "now() - INTERVAL %d DAY - INTERVAL %d HOUR - INTERVAL %d MINUTE + INTERVAL %.3f SECOND",
                                day, hour, i % 60, duration / 1000.0);
                            
                            batchInsert.append(String.format(
                                "('trace-%d-%d-%d-%d-%d', 'segment-%d-%d-%d-%d-%d', " +
                                "'%s', 'instance-%d', " +
                                "%d, 0, " +
                                "%s, %s, " +
                                "'%s', 0, 'Entry')",
                                day, hour, serviceId, opId, i,
                                day, hour, serviceId, opId, i,
                                service,
                                i % 2, // 2个实例
                                i,
                                startTime, endTime,
                                operationName
                            ));
                        }
                    }
                }

                // 执行批量插入
                stmt.execute(batchInsert.toString());
            }
            LOG.info("完成插入第{}天的测试数据", day + 1);
        }

        // 验证数据插入
        var rs = stmt.executeQuery("SELECT count(*) FROM integration.events");
        rs.next();
        long count = rs.getLong(1);
        LOG.info("成功插入{}条测试数据", count);

        // 验证数据是否满足规则生成条件
        rs = stmt.executeQuery(
            "SELECT count(*) FROM integration.events " +
            "WHERE start_time >= now() - INTERVAL 7 DAY " +
            "AND start_time < now() " +
            "AND service IS NOT NULL " +
            "AND operation_name IS NOT NULL " +
            "AND is_error = 0 " +
            "AND (end_time - start_time) * 1000 < 60000 " +
            "AND (end_time - start_time) > 0");
        rs.next();
        count = rs.getLong(1);
        LOG.info("其中{}条数据满足规则生成条件", count);

        // 验证每个小时的数据分布
        rs = stmt.executeQuery(
            "SELECT toHour(start_time) as hour_of_day, " +
            "service, operation_name, count(*) as sample_count " +
            "FROM integration.events " +
            "WHERE start_time >= now() - INTERVAL 7 DAY " +
            "AND start_time < now() " +
            "AND service IS NOT NULL " +
            "AND operation_name IS NOT NULL " +
            "AND is_error = 0 " +
            "AND (end_time - start_time) * 1000 < 60000 " +
            "AND (end_time - start_time) > 0 " +
            "GROUP BY hour_of_day, service, operation_name " +
            "HAVING sample_count >= 10 " +
            "ORDER BY hour_of_day, service, operation_name");

        int ruleCount = 0;
        while (rs.next()) {
            ruleCount++;
            if (ruleCount <= 5) { // 只打印前5条记录
                LOG.info("hour:{}, service:{}, operation:{}, samples:{}",
                    rs.getInt("hour_of_day"),
                    rs.getString("service"),
                    rs.getString("operation_name"),
                    rs.getLong("sample_count"));
            }
        }
        LOG.info("共有{}个服务/操作组合满足规则生成条件", ruleCount);

        LOG.info("基础测试数据插入完成");
    }

    /**
     * 清理测试数据
     */
    private void cleanupTestData() {
        try {
            Statement stmt = clickhouseConnection.createStatement();
            
            // 清空测试表
            stmt.execute("TRUNCATE TABLE IF EXISTS integration.events");
            stmt.execute("TRUNCATE TABLE IF EXISTS integration.flink_operator_agg_result");
            stmt.execute("TRUNCATE TABLE IF EXISTS integration.hourly_alarm_rules");

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