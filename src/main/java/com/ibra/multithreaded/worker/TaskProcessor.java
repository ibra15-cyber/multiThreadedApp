package com.ibra.multithreaded.worker;


import com.ibra.multithreaded.model.Task;
import com.ibra.multithreaded.model.TaskStatus;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task processor that simulates work being done on tasks.
 * Each processor can handle task processing with simulated delays and failure scenarios.
 */
public class TaskProcessor implements Runnable {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TaskProcessor.class);
    private static final int MAX_RETRIES = 3;
    private static final double FAILURE_PROBABILITY = 0.1; // 10% chance of failure

    private final BlockingQueue<Task> taskQueue;
    private final BlockingQueue<Task> retryQueue;
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap;
    private final AtomicInteger processedCount;
    private final AtomicInteger failedCount;
    private final Random random;

    public TaskProcessor(BlockingQueue<Task> taskQueue,
                         BlockingQueue<Task> retryQueue,
                         ConcurrentHashMap<String, TaskStatus> taskStatusMap,
                         AtomicInteger processedCount,
                         AtomicInteger failedCount) {
        this.taskQueue = taskQueue;
        this.retryQueue = retryQueue;
        this.taskStatusMap = taskStatusMap;
        this.processedCount = processedCount;
        this.failedCount = failedCount;
        this.random = new Random();
    }

    @Override
    public void run() {
        String workerName = Thread.currentThread().getName();
        log.info("Worker {} started", workerName);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Take task from queue (blocking operation)
                Task task = taskQueue.take();
                processTask(task, workerName);

            } catch (InterruptedException e) {
                log.info("Worker {} interrupted", workerName);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Worker {} encountered unexpected error: {}", workerName, e.getMessage(), e);
            }
        }

        log.info("Worker {} stopped", workerName);
    }

    private void processTask(Task task, String workerName) throws InterruptedException {
        String taskId = task.getId().toString();

        taskStatusMap.put(taskId, TaskStatus.PROCESSING);
        task.setLastProcessedTimestamp(Instant.now());

        log.info("Worker {} started processing {}", workerName, task);

        try {
            // Simulate processing time based on priority
            // Higher priority tasks get processed faster
            int processingTime = calculateProcessingTime(task.getPriority());
            Thread.sleep(processingTime);

            // Simulate random failures
            if (shouldSimulateFailure()) {
                throw new RuntimeException("Simulated processing failure");
            }

            taskStatusMap.put(taskId, TaskStatus.COMPLETED);
            processedCount.incrementAndGet();

            log.info("Worker {} completed {} in {}ms", workerName, task, processingTime);

        } catch (Exception e) {
            handleTaskFailure(task, workerName, e);
        }
    }

    private int calculateProcessingTime(int priority) {
        // Higher priority = faster processing
        // Priority 1-10, where 10 is highest
        int baseTime = 1000; // 1 second base
        int priorityMultiplier = (11 - priority) * 100; // Lower priority = more time
        int randomVariation = random.nextInt(500); // Add some randomness

        return baseTime + priorityMultiplier + randomVariation;
    }

    private boolean shouldSimulateFailure() {
        return random.nextDouble() < FAILURE_PROBABILITY;
    }

    private void handleTaskFailure(Task task, String workerName, Exception e) {
        String taskId = task.getId().toString();
        failedCount.incrementAndGet();

        log.warn("Worker {} failed to process {}: {}", workerName, task, e.getMessage());

        if (task.getRetryCount() < MAX_RETRIES) {
            Task retryTask = new Task(task);
            taskStatusMap.put(taskId, TaskStatus.RETRYING);

            try {
                retryQueue.put(retryTask);
                log.info("Retrying task {} (attempt {}/{})", task, retryTask.getRetryCount(), MAX_RETRIES);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Failed to queue retry for task {}: {}", task, ie.getMessage());
                taskStatusMap.put(taskId, TaskStatus.FAILED);
            }
        } else {
            // Max retries exceeded
            taskStatusMap.put(taskId, TaskStatus.FAILED);
            log.error("Failed to process task {} after {} retries: {}", task, MAX_RETRIES, e.getMessage());
        }
    }
}
