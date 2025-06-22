package com.ibra.multithreaded.manager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class ShutdownManager {
    private static final Logger logger = LoggerFactory.getLogger(ShutdownManager.class);

    private final AtomicBoolean systemRunning;
    private final QueueManager queueManager;
    private final ThreadPoolManager threadManager;
    private final StatisticsManager statsManager;

    public ShutdownManager(QueueManager queueManager,
                           ThreadPoolManager threadManager,
                           StatisticsManager statsManager) {
        this.systemRunning = new AtomicBoolean(true);
        this.queueManager = queueManager;
        this.threadManager = threadManager;
        this.statsManager = statsManager;
    }

    public void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered");
            if (systemRunning.get()) {
                initiateShutdown();
            }
        }, "ShutdownHook"));
    }

    public void initiateShutdown() {
        logger.info("Initiating system shutdown...");
        systemRunning.set(false);

        logger.info("Processing remaining tasks in queue...");
        queueManager.drainQueue(50);

        threadManager.shutdownAll();

        statsManager.printFinalStatistics();

        logger.info("System shutdown complete");
    }

    public AtomicBoolean getSystemRunning() {
        return systemRunning;
    }
}
