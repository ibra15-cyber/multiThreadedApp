package com.ibra.multithreaded.producer;


import com.ibra.multithreaded.ConcurQueue;
import com.ibra.multithreaded.model.Task;
import com.ibra.multithreaded.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Producer thread that generates and submits tasks to the queue.
 * Each producer has different strategies for creating tasks.
 */
public class TaskProducer implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(TaskProducer.class);

    private final String producerName;
    private final BlockingQueue<Task> taskQueue;
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap;
    private final AtomicBoolean running;
    private final Random random;
    private final int tasksPerBatch;
    private final int intervalSeconds;
    private final ProducerStrategy strategy;
    private final AtomicInteger tasksSubmitted;



    public TaskProducer(String producerName,
                        BlockingQueue<Task> taskQueue,
                        ConcurrentHashMap<String, TaskStatus> taskStatusMap,
                        AtomicBoolean running,
                        int tasksPerBatch,
                        int intervalSeconds,
                        ProducerStrategy strategy) {
        this.producerName = producerName;
        this.taskQueue = taskQueue;
        this.taskStatusMap = taskStatusMap;
        this.running = running;
        this.tasksPerBatch = tasksPerBatch;
        this.intervalSeconds = intervalSeconds;
        this.strategy = strategy;
        this.random = new Random();
        this.tasksSubmitted = new AtomicInteger(0);
    }

    @Override
    public void run() {

        log.info("Producer {} started with strategy: {}", producerName, strategy);

        while (running.get()) {
            try {
                for (int i = 0; i < tasksPerBatch; i++) {
                    Task task = generateTask();

                    taskQueue.put(task);

                    taskStatusMap.put(task.getId().toString(), TaskStatus.SUBMITTED);

                    int count = tasksSubmitted.incrementAndGet();

                    log.info("Producer {} submitted task {} (Total: {})", producerName, task, count);
                }

                Thread.sleep(intervalSeconds * 1000L);

            } catch (InterruptedException e) {
                log.info("Producer {} interrupted", producerName);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Producer {} encountered error: {}", producerName, e.getMessage());
            }
        }

        log.info("Producer {} stopped. Total tasks submitted: {}", producerName, tasksSubmitted.get());
    }

    private Task generateTask() {
        String taskName = String.format("%s-Task-%d", producerName, tasksSubmitted.get() + 1);
        int priority = generatePriority();
        String payload = generatePayload();

        return new Task(taskName, priority, payload);
    }

    private int generatePriority() {
        return switch (strategy) {
            case HIGH_PRIORITY_FOCUSED -> {
                // 70% high priority (7-10), 30% medium-low (1-6)
                yield random.nextBoolean() && random.nextBoolean() && random.nextBoolean()
                        ? random.nextInt(4) + 7  // 7-10
                        : random.nextInt(6) + 1; // 1-6
            }
            case BALANCED -> {
                // Even distribution 1-10
                yield random.nextInt(10) + 1;
            }
            case LOW_PRIORITY_BULK -> {
                // 80% low priority (1-4), 20% medium-high (5-10)
                yield random.nextInt(5) == 0
                        ? random.nextInt(6) + 5  // 5-10
                        : random.nextInt(4) + 1; // 1-4
            }
        };
    }

    private String generatePayload() {
        String[] payloadTypes = {
                "data-processing", "email-sending", "report-generation",
                "image-resize", "backup-operation", "notification-dispatch",
                "log-analysis", "cache-refresh", "database-cleanup"
        };

        String type = payloadTypes[random.nextInt(payloadTypes.length)];
        return String.format("{'type': '%s', 'producer': '%s', 'data': 'sample_%d'}", type, producerName, random.nextInt(1000));
    }

    public int getTasksSubmitted() {
        return tasksSubmitted.get();
    }
}