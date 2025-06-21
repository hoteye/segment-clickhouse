package com.o11y.shared.util;

import com.o11y.infrastructure.database.DatabaseService;

import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackgroundTaskManager {

    static final Logger logger = LoggerFactory.getLogger(BackgroundTaskManager.class);
    static final int DEFAULT_INTERVAL = 12000;

    /**
     * Starts a background task to periodically add columns to the database.
     * 
     * @param databaseService The database service instance.
     * @param missingFields   The set of missing fields.
     * @param interval        The interval in milliseconds between checks.
     */
    public static void startAddColumnsTask(DatabaseService databaseService, ConcurrentSkipListSet<String> missingFields,
            int interval) {
        new Thread(() -> {
            while (true) {
                logger.info("missingFields: {}.", missingFields);
                try {
                    Thread.sleep(DEFAULT_INTERVAL);
                    databaseService.addColumns(missingFields);
                    Thread.sleep(Math.abs(interval - DEFAULT_INTERVAL));
                } catch (Exception e) {
                    logger.error("Error in background task: {}", e.getMessage(), e); // Improved error logging
                }
            }
        }).start();
    }
}