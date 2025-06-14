package com.o11y.flink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.ConfigLoader;

import java.util.Map;

public class FlinkServiceLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkServiceLauncher.class);

    public static void main(String[] args) {
        try {
            Map<String, Object> config = ConfigLoader.loadConfig("application.yaml");
            FlinkService flinkService = new FlinkService(config);
            flinkService.run();
        } catch (Exception e) {
            LOG.error("FlinkService 启动失败", e);
            System.exit(1);
        }
    }
}
