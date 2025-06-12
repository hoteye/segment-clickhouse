package com.o11y.flink.sink;

import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;

/**
 * 将 Tuple5<String, String, Double, Long, Long> 写入 ClickHouse 表
 * flink_operator_agg_result (service, operator_name, avg_duration, max_duration, window_start)
 */
public class OperatorAggResultTuple5ClickHouseSink extends RichSinkFunction<Tuple5<String, String, Double, Long, Long>> {
    private static final Logger LOG = LoggerFactory.getLogger(OperatorAggResultTuple5ClickHouseSink.class);
    private final Map<String, String> clickhouseConfig;
    private transient Connection connection;
    private transient PreparedStatement insertStmt;

    public OperatorAggResultTuple5ClickHouseSink(Map<String, String> clickhouseConfig) {
        this.clickhouseConfig = clickhouseConfig;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        String url = clickhouseConfig.get("url");
        String username = clickhouseConfig.get("username");
        String password = clickhouseConfig.get("password");
        connection = DriverManager.getConnection(url, username, password);
        // 假设表结构: service String, operator_name String, avg_duration Float64, max_duration Int64, window_start Int64
        String sql = "INSERT INTO flink_operator_agg_result (service, operator_name, avg_duration, max_duration, window_start) VALUES (?, ?, ?, ?, ?)";
        insertStmt = connection.prepareStatement(sql);
        LOG.info("OperatorAggResultTuple5ClickHouseSink connected to ClickHouse: {}", url);
    }

    @Override
    public void invoke(Tuple5<String, String, Double, Long, Long> value, Context context) throws Exception {
        insertStmt.setString(1, value.f0); // service
        insertStmt.setString(2, value.f1); // operator_name
        insertStmt.setDouble(3, value.f2); // avg_duration
        insertStmt.setLong(4, value.f3); // max_duration
        insertStmt.setLong(5, value.f4); // window_start
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
