package com.ibra.multithreaded.manager;

import com.ibra.multithreaded.model.Task;
import com.ibra.multithreaded.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class QueueManager {
    private static final Logger logger = LoggerFactory.getLogger(QueueManager.class);

    private final PriorityBlockingQueue<Task> taskQueue;
    private final LinkedBlockingQueue<Task> retryQueue;
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap;
    private final AtomicInteger processedCount;
    private final AtomicInteger failedCount;

    public QueueManager(int queueCapacity) {
        this.taskQueue = new PriorityBlockingQueue<>(queueCapacity);
        this.retryQueue = new LinkedBlockingQueue<>();
        this.taskStatusMap = new ConcurrentHashMap<>();
        this.processedCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);
    }

    public void drainQueue(int limit) {
        int drainedTasks = 0;
        while (!taskQueue.isEmpty() && drainedTasks < limit) {
            Task task = taskQueue.poll();
            if (task != null) {
                taskStatusMap.put(task.getId().toString(), TaskStatus.COMPLETED);
                processedCount.incrementAndGet();
                drainedTasks++;
                logger.info("Drained task: {}", task);
            }
        }
        logger.info("Drained {} remaining tasks", drainedTasks);
    }

    // Getters
    public PriorityBlockingQueue<Task> getTaskQueue() { return taskQueue; }
    public LinkedBlockingQueue<Task> getRetryQueue() { return retryQueue; }
    public ConcurrentHashMap<String, TaskStatus> getTaskStatusMap() { return taskStatusMap; }
    public AtomicInteger getProcessedCount() { return processedCount; }
    public AtomicInteger getFailedCount() { return failedCount; }
}
