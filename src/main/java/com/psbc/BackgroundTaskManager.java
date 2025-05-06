package com.psbc;

import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackgroundTaskManager {

    static final Logger logger = LoggerFactory.getLogger(BackgroundTaskManager.class);

    public static void startAddColumnsTask(DatabaseService databaseService, ConcurrentSkipListSet<String> missingFields,
            int interval) {
        new Thread(() -> {
            while (true) {
                logger.info("missingFields: {}.", missingFields);
                try {
                    databaseService.addColumns(missingFields);
                    Thread.sleep(interval);
                } catch (Exception e) {
                    logger.error("Error in background task: {}", e.getMessage(), e); // Improved error logging
                }
            }
        }).start();
    }
}