package com.psbc.utilities;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public class Tools {

    /**
     * 检查字段名称是否符合数据库字段命名规范
     *
     * @param fieldName 字段名称
     * @return 如果符合规范返回 true，否则返回 false
     */
    public static boolean isValidFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false; // 字段名称不能为空
        }

        // 正则表达式：以字母或下划线开头，后面可以跟字母、数字或下划线
        String regex = "^[a-zA-Z_][a-zA-Z0-9_]*$";
        return fieldName.matches(regex);
    }

    public static Map<String, Object> loadConfig(String filePath) {
        try (InputStream input = new FileInputStream(filePath)) {
            Yaml yaml = new Yaml();
            return yaml.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration file: " + filePath, e);
        }
    }

    public static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
        }
    }

    public static void main(String[] args) {
        // 测试用例
        System.out.println(isValidFieldName("valid_Field_name")); // true
        System.out.println(isValidFieldName("_validFieldName123")); // true
        System.out.println(isValidFieldName("123invalid")); // false
        System.out.println(isValidFieldName("invalid-field-name")); // false
        System.out.println(isValidFieldName("invalid field name")); // false
        System.out.println(isValidFieldName("")); // false
        System.out.println(isValidFieldName(null)); // false
    }
}