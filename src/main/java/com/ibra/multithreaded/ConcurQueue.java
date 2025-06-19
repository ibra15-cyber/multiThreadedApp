package com.ibra.multithreaded;

import com.ibra.multithreaded.demo.RaceConditionDemo;
import com.ibra.multithreaded.model.Task;
import com.ibra.multithreaded.model.TaskStatus;
import com.ibra.multithreaded.monitor.SystemMonitor;
import com.ibra.multithreaded.producer.ProducerStrategy;
import com.ibra.multithreaded.producer.TaskProducer;
import com.ibra.multithreaded.worker.TaskProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main class for ConcurQueue - A Multithreaded Job Processing Platform
 *
 * This system demonstrates advanced Java concurrency concepts including:
 * - Producer-Consumer patterns with BlockingQueues
 * - Thread pools and ExecutorService
 * - Concurrent collections and atomic operations
 * - System monitoring and graceful shutdown
 */
public class ConcurQueue {
    // Changed: Use SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(ConcurQueue.class);

    // System configuration
    private static final int QUEUE_CAPACITY = 1000;
    private static final int WORKER_POOL_SIZE = 5;
    private static final int PRODUCER_COUNT = 3;

    // Shared data structures
    private final PriorityBlockingQueue<Task> taskQueue;
    private final BlockingQueue<Task> retryQueue;
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap;

    // Thread management
    private final ExecutorService workerPool;
    private final List<Thread> producerThreads;
    private final Thread monitorThread;
    private final Thread retryProcessorThread;

    // System state
    private final AtomicBoolean running;
    private final AtomicInteger processedCount;
    private final AtomicInteger failedCount;

    // System components
    private final SystemMonitor systemMonitor;

    public ConcurQueue() {
        // Initialize concurrent data structures
        this.taskQueue = new PriorityBlockingQueue<>(QUEUE_CAPACITY);
        this.retryQueue = new LinkedBlockingQueue<>();
        this.taskStatusMap = new ConcurrentHashMap<>();

        // Initialize atomic counters
        this.running = new AtomicBoolean(true);
        this.processedCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);

        // Initialize thread pool for workers
        this.workerPool = Executors.newFixedThreadPool(WORKER_POOL_SIZE, r -> {
            Thread t = new Thread(r);
            t.setName("ConcurQueue-Worker-" + t.getId());
            t.setDaemon(false);
            return t;
        });

        // Initialize system monitor
        this.systemMonitor = new SystemMonitor(
                taskQueue, retryQueue, taskStatusMap,
                (ThreadPoolExecutor) workerPool, running,
                processedCount, failedCount
        );

        // Initialize threads
        this.producerThreads = new ArrayList<>();
        this.monitorThread = new Thread(systemMonitor, "SystemMonitor");
        this.retryProcessorThread = new Thread(this::processRetryQueue, "RetryProcessor");

        // Setup shutdown hook
        setupShutdownHook();
    }

    /**
     * Starts the ConcurQueue system
     */
    public void start() {
        logger.info("Starting ConcurQueue system...");

        // Start worker threads
        startWorkers();

        // Start producer threads
        startProducers();

        // Start retry processor
        retryProcessorThread.start();

        // Start system monitor
        monitorThread.start();

        logger.info("ConcurQueue system started successfully");
        // Changed: SLF4J parameterized logging
        logger.info("Configuration: {} workers, {} producers, queue capacity: {}",
                WORKER_POOL_SIZE, PRODUCER_COUNT, QUEUE_CAPACITY);
    }

    /**
     * Starts worker threads using ExecutorService
     */
    private void startWorkers() {
        for (int i = 0; i < WORKER_POOL_SIZE; i++) {
            TaskProcessor processor = new TaskProcessor(
                    taskQueue, retryQueue, taskStatusMap,
                    processedCount, failedCount
            );
            workerPool.submit(processor);
        }
        // Changed: SLF4J parameterized logging
        logger.info("Started {} worker threads", WORKER_POOL_SIZE);
    }

    /**
     * Starts producer threads with different strategies
     */
    private void startProducers() {
        // Producer 1: High Priority Focused
        TaskProducer producer1 = new TaskProducer(
                "HighPriorityProducer", taskQueue, taskStatusMap, running,
                3, 2, ProducerStrategy.HIGH_PRIORITY_FOCUSED
        );

        // Producer 2: Balanced
        TaskProducer producer2 = new TaskProducer(
                "BalancedProducer", taskQueue, taskStatusMap, running,
                5, 3, ProducerStrategy.BALANCED
        );

        // Producer 3: Low Priority Bulk
        TaskProducer producer3 = new TaskProducer(
                "BulkProducer", taskQueue, taskStatusMap, running,
                8, 4, ProducerStrategy.LOW_PRIORITY_BULK
        );

        Thread t1 = new Thread(producer1, "Producer-1");
        Thread t2 = new Thread(producer2, "Producer-2");
        Thread t3 = new Thread(producer3, "Producer-3");

        producerThreads.add(t1);
        producerThreads.add(t2);
        producerThreads.add(t3);

        t1.start();
        t2.start();
        t3.start();

        // Changed: SLF4J parameterized logging
        logger.info("Started {} producer threads", PRODUCER_COUNT);
    }

    /**
     * Processes tasks from the retry queue
     */
    private void processRetryQueue() {
        logger.info("Retry processor started");

        while (running.get() || !retryQueue.isEmpty()) {
            try {
                Task retryTask = retryQueue.poll(1, TimeUnit.SECONDS);
                if (retryTask != null) {
                    // Add delay before retry
                    Thread.sleep(2000); // 2 second delay

                    // Re-queue the task
                    taskQueue.put(retryTask);
                    taskStatusMap.put(retryTask.getId().toString(), TaskStatus.SUBMITTED);

                    // Changed: SLF4J parameterized logging
                    logger.info("Re-queued retry task {} (attempt {})",
                            retryTask, retryTask.getRetryCount());
                }
            } catch (InterruptedException e) {
                // Changed: SLF4J info with interruption message and throwable
                logger.info("Retry processor interrupted", e); // Pass exception as last argument
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("Retry processor stopped");
    }

    /**
     * Runs the system for a specified duration
     */
    public void run(int durationSeconds) {
        start();

        try {
            // Changed: SLF4J parameterized logging
            logger.info("System will run for {} seconds...", durationSeconds);
            Thread.sleep(durationSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Changed: SLF4J info with interruption message and throwable
            logger.info("System interrupted", e); // Pass exception as last argument
        }

        shutdown();
    }

    /**
     * Gracefully shuts down the system
     */
    public void shutdown() {
        logger.info("Initiating system shutdown...");

        // Stop accepting new tasks
        running.set(false);

        // Stop producers
        for (Thread producer : producerThreads) {
            producer.interrupt();
        }

        // Wait for producers to finish
        for (Thread producer : producerThreads) {
            try {
                producer.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                // Changed: SLF4J error/warn with interruption message and throwable
                logger.warn("Interrupted while waiting for producer {} to join", producer.getName(), e);
                Thread.currentThread().interrupt();
            }
        }

        // Process remaining tasks in queue
        // Changed: SLF4J parameterized logging
        logger.info("Processing remaining {} tasks in queue...", taskQueue.size());

        // Shutdown worker pool gracefully
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                // Changed: SLF4J warning
                logger.warn("Workers did not terminate gracefully, forcing shutdown");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Changed: SLF4J error/warn with interruption message and throwable
            logger.warn("Interrupted while waiting for worker pool to terminate, forcing shutdown", e);
            workerPool.shutdownNow();
        }

        // Stop retry processor
        retryProcessorThread.interrupt();
        try {
            retryProcessorThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Changed: SLF4J error/warn with interruption message and throwable
            logger.warn("Interrupted while waiting for retry processor to join", e);
        }

        // Stop monitor
        monitorThread.interrupt();
        try {
            monitorThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Changed: SLF4J error/warn with interruption message and throwable
            logger.warn("Interrupted while waiting for monitor to join", e);
        }

        // Final statistics
        printFinalStatistics();

        logger.info("ConcurQueue system shutdown complete");
    }

    /**
     * Sets up shutdown hook for graceful termination
     */
    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered");
            if (running.get()) {
                shutdown();
            }
        }, "ShutdownHook"));
    }

    /**
     * Prints final system statistics
     */
    private void printFinalStatistics() {
        SystemMonitor.SystemStats stats = systemMonitor.getCurrentStats();

        logger.info("=== FINAL SYSTEM STATISTICS ===");
        // Changed: SLF4J parameterized logging
        logger.info("Total tasks processed: {}", processedCount.get());
        logger.info("Total tasks failed: {}", failedCount.get());
        logger.info("Tasks remaining in queue: {}", stats.queueSize());
        logger.info("Tasks in retry queue: {}", stats.retryQueueSize());
        logger.info("Total worker threads completed: {}", stats.completedTasks());

        logger.info("Task status breakdown:");
        stats.statusCounts().forEach((status, count) ->
                // Changed: SLF4J parameterized logging
                logger.info("  {}: {}", status, count));
    }

    /**
     * Demonstrates the race condition fixes
     */
    public void demonstrateRaceConditions() {
        logger.info("\n" + "=".repeat(50));
        logger.info("DEMONSTRATING RACE CONDITIONS AND FIXES");
        logger.info("=".repeat(50));

        RaceConditionDemo.demonstrateRaceConditions();
        RaceConditionDemo.demonstrateDeadlock();

        logger.info("=".repeat(50));
        logger.info("RACE CONDITION DEMONSTRATION COMPLETE");
        logger.info("=".repeat(50) + "\n");
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        // Parse command line arguments
        int durationSeconds = 30; // Default runtime
        if (args.length > 0) {
            try {
                durationSeconds = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // Changed: SLF4J warning with exception and message
                logger.warn("Invalid duration argument, using default: {}", durationSeconds, e);
            }
        }

        logger.info("=".repeat(60));
        logger.info("CONCURQUEUE - MULTITHREADED JOB PROCESSING PLATFORM");
        logger.info("=".repeat(60));

        ConcurQueue system = new ConcurQueue();

        // First demonstrate race conditions
        system.demonstrateRaceConditions();

        // Then run the main system
        system.run(durationSeconds);

        logger.info("Program completed successfully");
    }
}