package com.ibra.multithreaded.manager;

import com.ibra.multithreaded.monitor.SystemMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class StatisticsManager {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsManager.class);

    private final QueueManager queueManager;
    private final ThreadPoolManager threadManager;
    private SystemMonitor systemMonitor;

    public StatisticsManager(QueueManager queueManager, ThreadPoolManager threadManager) {
        this.queueManager = queueManager;
        this.threadManager = threadManager;
    }

    public void startMonitoring(AtomicBoolean systemRunning) {
        systemMonitor = new SystemMonitor(
                queueManager.getTaskQueue(),
                queueManager.getRetryQueue(),
                queueManager.getTaskStatusMap(),
                threadManager.getWorkerPool(),
                systemRunning,
                queueManager.getProcessedCount(),
                queueManager.getFailedCount()
        );
        threadManager.getMonitorPool().submit(systemMonitor);
    }

    public void printFinalStatistics() {
        if (systemMonitor != null) {
            SystemMonitor.SystemStats stats = systemMonitor.getCurrentStats();
            logger.info("=== FINAL SYSTEM STATISTICS ===");
            logger.info("Total tasks processed: {}", stats.processedCount());
            logger.info("Total tasks failed: {}", stats.failedCount());
            logger.info("Tasks remaining in queue: {}", stats.queueSize());
            logger.info("Tasks in retry queue: {}", stats.retryQueueSize());
            logger.info("Worker pool completed tasks: {}", stats.completedTasks());
        }
    }
}