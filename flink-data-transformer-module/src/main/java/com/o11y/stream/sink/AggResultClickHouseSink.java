package com.o11y.stream.sink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.domain.model.aggregation.ServiceAggResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;

/**
 * 聚合结果 ClickHouse Sink 实现。
 * 
 * <p>
 * 负责将 Flink 流处理产生的服务聚合结果写入到 ClickHouse 的
 * flink_operator_agg_result 表中，用于后续的数据分析和监控展示。
 * 
 * <p>
 * <strong>主要功能：</strong>
 * <ul>
 * <li>聚合数据的批量写入</li>
 * <li>数据格式化和类型转换</li>
 * <li>连接管理和异常处理</li>
 * <li>性能指标记录</li>
 * </ul>
 * 
 * <p>
 * <strong>表结构映射：</strong>
 * ServiceAggResult 对象会被映射到包含以下字段的表：
 * window_start, windowSize, operator_name, operator_class, service,
 * instance, method, avg_duration, max_duration, error_rate 等。
 * 
 * <p>
 * <strong>性能考虑：</strong>
 * <ul>
 * <li>使用 PreparedStatement 提高执行效率</li>
 * <li>连接复用减少连接开销</li>
 * <li>异常处理保证数据一致性</li>
 * </ul>
 * 
 * @see ServiceAggResult 服务聚合结果模型
 * @see RichSinkFunction Flink Rich Sink 基类
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class AggResultClickHouseSink extends RichSinkFunction<ServiceAggResult> {
    private static final Logger LOG = LoggerFactory.getLogger(AggResultClickHouseSink.class);
    private final Map<String, String> clickhouseConfig;
    private transient Connection connection;
    private transient PreparedStatement insertStmt;

    /**
     * 构造函数，初始化 ClickHouse 连接配置。
     * 
     * @param clickhouseConfig ClickHouse 连接参数（url、username、password等）
     */
    public AggResultClickHouseSink(Map<String, String> clickhouseConfig) {
        this.clickhouseConfig = clickhouseConfig;
    }

    /**
     * 初始化 ClickHouse 连接和预编译 SQL。
     * 
     * @param parameters Flink 配置参数
     * @throws Exception 连接异常
     */
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        String url = clickhouseConfig.get("url");
        String username = clickhouseConfig.get("username");
        String password = clickhouseConfig.get("password");
        connection = DriverManager.getConnection(url, username, password);
        // 表结构: window_start, windowSize, operator_name, operator_class, service,
        // instance, method, avg_duration, max_duration, error_rate, data_center,
        // region, env, total_count, error_count, success_count
        String sql = "INSERT INTO flink_operator_agg_result (window_start, windowSize, operator_name, operator_class, service, instance, method, avg_duration, max_duration, error_rate, data_center, region, env, total_count, error_count, success_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        insertStmt = connection.prepareStatement(sql);
        LOG.info("ServiceAggResultClickHouseSink connected to ClickHouse: {}", url);
    }

    /**
     * 每条聚合结果写入 ClickHouse。
     * 
     * @param value   聚合结果对象
     * @param context Flink sink 上下文
     * @throws Exception 写入异常
     */
    @Override
    public void invoke(ServiceAggResult value, Context context) throws Exception {
        insertStmt.setLong(1, value.windowStart);
        insertStmt.setInt(2, value.windowSize);
        insertStmt.setString(3, value.operatorName);
        insertStmt.setString(4, value.operatorClass);
        insertStmt.setString(5, value.service);
        insertStmt.setString(6, value.instance);
        insertStmt.setString(7, value.method);
        if (value.avgDuration != null)
            insertStmt.setDouble(8, value.avgDuration);
        else
            insertStmt.setNull(8, java.sql.Types.DOUBLE);
        if (value.maxDuration != null)
            insertStmt.setLong(9, value.maxDuration);
        else
            insertStmt.setNull(9, java.sql.Types.BIGINT);
        if (value.errorRate != null)
            insertStmt.setDouble(10, value.errorRate);
        else
            insertStmt.setNull(10, java.sql.Types.DOUBLE);
        insertStmt.setString(11, value.dataCenter);
        insertStmt.setString(12, value.region);
        insertStmt.setString(13, value.env);
        if (value.totalCount != null)
            insertStmt.setLong(14, value.totalCount);
        else
            insertStmt.setNull(14, java.sql.Types.BIGINT);
        if (value.errorCount != null)
            insertStmt.setLong(15, value.errorCount);
        else
            insertStmt.setNull(15, java.sql.Types.BIGINT);
        if (value.successCount != null)
            insertStmt.setLong(16, value.successCount);
        else
            insertStmt.setNull(16, java.sql.Types.BIGINT);
        insertStmt.executeUpdate();
        LOG.debug("Inserted ServiceAggResult into ClickHouse: windowSize:{}, operatorName:{}, service:{}",
                value.windowSize, value.operatorName, value.service);
    }

    /**
     * 关闭 ClickHouse 连接和资源。
     * 
     * @throws Exception 关闭异常
     */
    @Override
    public void close() throws Exception {
        if (insertStmt != null)
            insertStmt.close();
        if (connection != null)
            connection.close();
        super.close();
    }
}
