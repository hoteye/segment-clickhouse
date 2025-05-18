package com.psbc;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

public class ConfigLoader {
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
}