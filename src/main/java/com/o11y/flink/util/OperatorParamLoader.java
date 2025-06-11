package com.o11y.flink.util;

import com.o11y.DatabaseService;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class OperatorParamLoader {

    private static final String sql = "SELECT paramKey, paramValue FROM param_config WHERE namespace='flinkParam' AND operatorClass=?";
    private static final String PARAM_UPDATE_TOPIC = "flink-operator-param-update";

    /**
     * 通用参数加载方法：从 param_config 表按命名空间和算子类名加载所有参数（单值Map）
     */
    public static Map<String, String> loadParams(DatabaseService db, String operatorClass) {
        Map<String, String> params = new HashMap<>();
        try (var pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, operatorClass);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                params.put(rs.getString("paramKey"), rs.getString("paramValue"));
            }
            rs.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return params;
    }

    /**
     * 支持同一个 paramKey 有多行的情况，返回 Map<String, List<String>>
     */
    public static Map<String, List<String>> loadParamList(DatabaseService db, String operatorClass) {
        Map<String, List<String>> params = new HashMap<>();
        try (var pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, operatorClass);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String key = rs.getString("paramKey");
                String value = rs.getString("paramValue");
                params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
            rs.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return params;
    }

    // 新增参数
    public static void newParams(DatabaseService db, String operatorClass, Map<String, List<String>> params,
            KafkaProducer<String, String> producer) {
        try {
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                for (String value : entry.getValue()) {
                    String sql = "INSERT INTO param_config(namespace, operatorClass, paramKey, paramValue) VALUES('flinkParam', ?, ?, ?)";
                    try (var pstmt = db.getConnection().prepareStatement(sql)) {
                        pstmt.setString(1, operatorClass);
                        pstmt.setString(2, entry.getKey());
                        pstmt.setString(3, value);
                        pstmt.executeUpdate();
                    }
                }
            }
            // 发送Kafka消息通知算子
            String msg = operatorClass + ":new";
            ProducerRecord<String, String> record = new ProducerRecord<>(PARAM_UPDATE_TOPIC, operatorClass, msg);
            try {
                var future = producer.send(record);
                var metadata = future.get(); // 等待发送完成
                System.out.println("[OperatorParamLoader] send kafka: topic=" + record.topic() + ", key=" + record.key()
                        + ", value=" + record.value() + ", meta=" + metadata);
            } catch (Exception ex) {
                System.err.println("[OperatorParamLoader] send kafka error: " + ex.getMessage());
                ex.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 更新参数
    public static void updateParams(DatabaseService db, String operatorClass, Map<String, List<String>> params,
            KafkaProducer<String, String> producer) {
        try {
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                // 先删除旧参数
                String delSql = "DELETE FROM param_config WHERE namespace='flinkParam' AND operatorClass=? AND paramKey=?";
                try (var pstmt = db.getConnection().prepareStatement(delSql)) {
                    pstmt.setString(1, operatorClass);
                    pstmt.setString(2, entry.getKey());
                    pstmt.executeUpdate();
                }
                // 再插入新参数
                for (String value : entry.getValue()) {
                    String insSql = "INSERT INTO param_config(namespace, operatorClass, paramKey, paramValue) VALUES('flinkParam', ?, ?, ?)";
                    try (var pstmt = db.getConnection().prepareStatement(insSql)) {
                        pstmt.setString(1, operatorClass);
                        pstmt.setString(2, entry.getKey());
                        pstmt.setString(3, value);
                        pstmt.executeUpdate();
                    }
                }
            }
            // 发送Kafka消息通知算子
            String msg = operatorClass + ":update";
            ProducerRecord<String, String> record = new ProducerRecord<>(PARAM_UPDATE_TOPIC, operatorClass, msg);
            try {
                var future = producer.send(record);
                var metadata = future.get(); // 等待发送完成
                System.out.println("[OperatorParamLoader] send kafka: topic=" + record.topic() + ", key=" + record.key()
                        + ", value=" + record.value() + ", meta=" + metadata);
            } catch (Exception ex) {
                System.err.println("[OperatorParamLoader] send kafka error: " + ex.getMessage());
                ex.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
