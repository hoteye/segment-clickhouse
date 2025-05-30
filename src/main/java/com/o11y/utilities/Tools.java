package com.o11y.utilities;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public class Tools {

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
     * @param filePath The path to the configuration file.
     * @return A map containing the configuration data.
     * @throws RuntimeException if loading fails.
     */
    public static Map<String, Object> loadConfig(String filePath) {
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