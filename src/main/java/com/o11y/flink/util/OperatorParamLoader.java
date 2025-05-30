package com.o11y.flink.util;

import com.o11y.DatabaseService;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class OperatorParamLoader {

    private static final String sql = "SELECT paramKey, paramValue FROM param_config WHERE namespace='flinkParam' AND operatorClass=?";

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
}
