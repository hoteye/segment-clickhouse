package com.o11y.shared.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * 配置和验证工具类。
 * 
 * <p>
 * 提供项目中常用的配置工具方法，包括配置文件加载、字段名验证、
 * 数据验证等功能。作为共享组件为其他模块提供基础服务。
 * 
 * <p>
 * <strong>主要功能：</strong>
 * <ul>
 * <li>YAML 配置文件加载和解析</li>
 * <li>ClickHouse 字段名验证</li>
 * <li>数据完整性检查</li>
 * <li>通用验证规则</li>
 * </ul>
 * 
 * <p>
 * <strong>设计原则：</strong>
 * <ul>
 * <li>静态方法提供无状态的工具功能</li>
 * <li>异常处理和边界情况考虑</li>
 * <li>高内聚低耦合的设计</li>
 * </ul>
 * 
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class ConfigurationUtils {

    /**
     * Check if the field name conforms to ClickHouse field naming requirements.
     * <p>
     * ClickHouse identifiers must:
     * <ul>
     * <li>Start with a letter or underscore</li>
     * <li>Contain only letters, digits, or underscores</li>
     * <li>Not be a reserved keyword</li>
     * <li>Length should not exceed 255 characters</li>
     * </ul>
     * 
     * @param fieldName Field name
     * @return true if valid, false otherwise
     */
    public static boolean isValidFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false; // Field name cannot be empty
        }
        // Check length (ClickHouse limit is 255)
        if (fieldName.length() > 255) {
            return false;
        }
        // Check allowed characters and start
        String regex = "^[a-zA-Z_][a-zA-Z0-9_]*$";
        if (!fieldName.matches(regex)) {
            return false;
        }
        // Check reserved keywords (partial, can be extended)
        String[] reserved = {
                "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "TABLE", "DATABASE", "AND", "OR",
                "NOT", "NULL", "IN", "AS", "BY", "ORDER", "GROUP", "LIMIT", "OFFSET", "JOIN", "ON", "USING", "CASE",
                "WHEN", "THEN", "ELSE", "END", "DISTINCT", "UNION", "ALL", "ANY", "SOME", "EXISTS", "BETWEEN", "LIKE",
                "ILIKE", "INTO", "VALUES", "PRIMARY", "KEY", "DEFAULT", "ENGINE", "PARTITION", "SET", "SHOW",
                "DESCRIBE", "OPTIMIZE", "ALTER", "DROP", "TRUNCATE", "RENAME", "GRANT", "REVOKE", "ATTACH", "DETACH",
                "SYSTEM", "FORMAT", "PREWHERE", "WITH", "TOP", "ARRAY", "MAP", "TUPLE", "ENUM", "DATE", "DATETIME",
                "UUID", "INT", "FLOAT", "STRING", "BOOLEAN"
        };
        for (String kw : reserved) {
            if (fieldName.equalsIgnoreCase(kw)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Loads a YAML configuration file from the given file path.
     * 
     * <p>
     * 支持两种加载方式：
     * <ul>
     * <li>优先从 classpath 读取资源文件</li>
     * <li>其次从文件系统读取指定路径的文件</li>
     * </ul>
     * 
     * @param filePath The path to the configuration file.
     * @return A map containing the configuration data.
     * @throws RuntimeException if loading fails.
     */
    public static Map<String, Object> loadConfig(String filePath) {
        // 优先从 classpath 读取
        try (InputStream input = ConfigurationUtils.class.getClassLoader().getResourceAsStream(filePath)) {
            if (input != null) {
                Yaml yaml = new Yaml();
                return yaml.load(input);
            }
        } catch (Exception ignore) {
            // 忽略异常，尝试下一种方式
        }

        // 其次从文件系统读取
        try (InputStream input = new FileInputStream(filePath)) {
            Yaml yaml = new Yaml();
            return yaml.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration file: " + filePath, e);
        }
    }

    /**
     * Sleeps for the specified number of milliseconds.
     * 
     * @param milliseconds The number of milliseconds to sleep.
     */
    public static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
        }
    }

}
