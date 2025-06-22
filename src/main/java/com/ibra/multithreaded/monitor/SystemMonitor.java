package com.ibra.multithreaded.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibra.multithreaded.model.TaskStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * System monitor that tracks queue status, thread pool health,
 * and exports metrics to JSON files.
 */
public class SystemMonitor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SystemMonitor.class);

    private final BlockingQueue<?> taskQueue;
    private final BlockingQueue<?> retryQueue;
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap;
    private final ThreadPoolExecutor workerPool;
    private final AtomicBoolean running;
    private final AtomicInteger processedCount;
    private final AtomicInteger failedCount;
    private final ObjectMapper objectMapper;

    private long lastExportTime;
    private final long exportIntervalMs = 60000; // 1 minute
    private final long monitorIntervalMs = 5000; // 5 seconds

    public SystemMonitor(BlockingQueue<?> taskQueue,
                         BlockingQueue<?> retryQueue,
                         ConcurrentHashMap<String, TaskStatus> taskStatusMap,
                         ThreadPoolExecutor workerPool,
                         AtomicBoolean running,
                         AtomicInteger processedCount,
                         AtomicInteger failedCount) {
        this.taskQueue = taskQueue;
        this.retryQueue = retryQueue;
        this.taskStatusMap = taskStatusMap;
        this.workerPool = workerPool;
        this.running = running;
        this.processedCount = processedCount;
        this.failedCount = failedCount;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.lastExportTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        logger.info("System Monitor started");

        while (running.get()) {
            try {
                logSystemStatus();
                detectStalledTasks();

                long currentTime = System.currentTimeMillis();
                if (currentTime - lastExportTime >= exportIntervalMs) {
                    exportStatusToJson();
                    lastExportTime = currentTime;
                }

                Thread.sleep(monitorIntervalMs);

            } catch (InterruptedException e) {
                logger.info("System Monitor interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("System Monitor error: {}", e.getMessage(), e); // Pass exception as last argument
            }
        }

        exportStatusToJson();
        logger.info("System Monitor stopped");
    }

    private void logSystemStatus() {
        int queueSize = taskQueue.size();
        int retryQueueSize = retryQueue.size();
        int activeWorkers = workerPool.getActiveCount();
        int totalWorkers = workerPool.getPoolSize();
        long completedTasks = workerPool.getCompletedTaskCount();

        Map<TaskStatus, Long> statusCounts = getStatusCounts();

        logger.info(
                "=== SYSTEM STATUS === " +
                        "Queue: {} tasks, Retry Queue: {}, " +
                        "Workers: {}/{} active, Completed: {}, " +
                        "Processed: {}, Failed: {}, " +
                        "Status: [SUBMITTED: {}, PROCESSING: {}, COMPLETED: {}, FAILED: {}, RETRYING: {}]",
                queueSize, retryQueueSize, activeWorkers, totalWorkers, completedTasks,
                processedCount.get(), failedCount.get(),
                statusCounts.getOrDefault(TaskStatus.SUBMITTED, 0L),
                statusCounts.getOrDefault(TaskStatus.PROCESSING, 0L),
                statusCounts.getOrDefault(TaskStatus.COMPLETED, 0L),
                statusCounts.getOrDefault(TaskStatus.FAILED, 0L),
                statusCounts.getOrDefault(TaskStatus.RETRYING, 0L)
        );
    }

    private Map<TaskStatus, Long> getStatusCounts() {
        Map<TaskStatus, Long> counts = new HashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }

        taskStatusMap.values().forEach(status ->
                counts.merge(status, 1L, Long::sum));

        return counts;
    }

    private void detectStalledTasks() {
        long currentTime = System.currentTimeMillis();
        long stalledThreshold = 30000; // 30 seconds

        int stalledCount = 0;
        for (Map.Entry<String, TaskStatus> entry : taskStatusMap.entrySet()) {
            if (entry.getValue() == TaskStatus.PROCESSING) {
                // In a real system, we'd track when each task started processing
                // For this simulation, we'll just count long-running processing tasks
                stalledCount++;
            }
        }

        if (stalledCount > workerPool.getCorePoolSize() * 2) {
            logger.warn("Potential stalled tasks detected: {} tasks in PROCESSING state",
                    stalledCount);
        }
    }

    private void exportStatusToJson() {
        try {
            Map<String, Object> systemStatus = new HashMap<>();
            systemStatus.put("timestamp", Instant.now().toString());
            systemStatus.put("queueSize", taskQueue.size());
            systemStatus.put("retryQueueSize", retryQueue.size());
            systemStatus.put("activeWorkers", workerPool.getActiveCount());
            systemStatus.put("totalWorkers", workerPool.getPoolSize());
            systemStatus.put("completedTasks", workerPool.getCompletedTaskCount());
            systemStatus.put("processedCount", processedCount.get());
            systemStatus.put("failedCount", failedCount.get());
            systemStatus.put("statusCounts", getStatusCounts());

            Map<String, String> taskStatuses = new HashMap<>();
            taskStatusMap.forEach((id, status) ->
                    taskStatuses.put(id, status.name()));

            systemStatus.put("taskStatuses", taskStatuses);

            File outputFile = new File("concurqueue_status_" +
                    System.currentTimeMillis() + ".json");
            objectMapper.writeValue(outputFile, systemStatus);

            logger.info("System status exported to: {}", outputFile.getName());

        } catch (IOException e) {
            logger.error("Failed to export system status: {}", e.getMessage(), e);
        }
    }

    public SystemStats getCurrentStats() {
        return new SystemStats(
                taskQueue.size(),
                retryQueue.size(),
                workerPool.getActiveCount(),
                workerPool.getPoolSize(),
                workerPool.getCompletedTaskCount(),
                processedCount.get(),
                failedCount.get(),
                getStatusCounts()
        );
    }

    public static record SystemStats(
            int queueSize,
            int retryQueueSize,
            int activeWorkers,
            int totalWorkers,
            long completedTasks,
            int processedCount,
            int failedCount,
            Map<TaskStatus, Long> statusCounts
    ) {}
}
