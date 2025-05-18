package com.psbc.utilities;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public class Tools {

    /**
     * Check if the field name conforms to the database field naming convention
     *
     * @param fieldName Field name
     * @return true if valid, false otherwise
     */
    public static boolean isValidFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false; // Field name cannot be empty
        }

        // Regular expression: starts with a letter or underscore, followed by letters,
        // numbers, or underscores
        String regex = "^[a-zA-Z_][a-zA-Z0-9_]*$";
        return fieldName.matches(regex);
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

    /**
     * Main method for testing utility functions.
     * 
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        // Test cases
        System.out.println(isValidFieldName("valid_Field_name")); // true
        System.out.println(isValidFieldName("_validFieldName123")); // true
        System.out.println(isValidFieldName("123invalid")); // false
        System.out.println(isValidFieldName("invalid-field-name")); // false
        System.out.println(isValidFieldName("invalid field name")); // false
        System.out.println(isValidFieldName("")); // false
        System.out.println(isValidFieldName(null)); // false
    }
}