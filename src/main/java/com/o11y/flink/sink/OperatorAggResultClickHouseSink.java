package com.o11y.flink.sink;

import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;

/**
 * 将 Tuple4<String, Double, Long, Long> 写入 ClickHouse 表
 * flink_operator_agg_result
 */
public class OperatorAggResultClickHouseSink extends RichSinkFunction<Tuple4<String, Double, Long, Long>> {
    private static final Logger LOG = LoggerFactory.getLogger(OperatorAggResultClickHouseSink.class);
    private final Map<String, String> clickhouseConfig;
    private transient Connection connection;
    private transient PreparedStatement insertStmt;

    public OperatorAggResultClickHouseSink(Map<String, String> clickhouseConfig) {
        this.clickhouseConfig = clickhouseConfig;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        String url = clickhouseConfig.get("url");
        String username = clickhouseConfig.get("username");
        String password = clickhouseConfig.get("password");
        connection = DriverManager.getConnection(url, username, password);
        // 假设表结构: service String, avg_duration Float64, max_duration Int64, window_end
        // Int64
        String sql = "INSERT INTO flink_operator_agg_result (service, avg_duration, max_duration, window_end) VALUES (?, ?, ?, ?)";
        insertStmt = connection.prepareStatement(sql);
        LOG.info("OperatorAggResultClickHouseSink connected to ClickHouse: {}", url);
    }

    @Override
    public void invoke(Tuple4<String, Double, Long, Long> value, Context context) throws Exception {
        insertStmt.setString(1, value.f0); // service
        insertStmt.setDouble(2, value.f1); // avg_duration
        insertStmt.setLong(3, value.f2); // max_duration
        insertStmt.setLong(4, value.f3); // window_end
        insertStmt.executeUpdate();
    }

    @Override
    public void close() throws Exception {
        if (insertStmt != null) {
            insertStmt.close();
        }
        if (connection != null) {
            connection.close();
        }
        super.close();
    }
}
