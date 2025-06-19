package com.ibra.multithreaded;

// Removed: import com.ibra.multithreaded.demo.RaceConditionDemo; // No longer directly used in this version
import com.ibra.multithreaded.model.Task;
import com.ibra.multithreaded.model.TaskStatus;
import com.ibra.multithreaded.monitor.SystemMonitor;
import com.ibra.multithreaded.producer.ProducerStrategy;
import com.ibra.multithreaded.producer.TaskProducer;
import com.ibra.multithreaded.worker.TaskProcessor;

import org.slf4j.Logger; // Changed: Import SLF4J Logger
import org.slf4j.LoggerFactory; // Changed: Import SLF4J LoggerFactory

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
// Removed: import java.util.logging.Logger; (original JDK logger)

/**
 * ConcurQueue - A Multithreaded Job Processing Platform
 *
 * This application demonstrates advanced Java concurrency concepts including:
 * - Producer-Consumer patterns with BlockingQueues
 * - Thread pools and ExecutorService
 * - Concurrent collections and synchronization
 * - Race condition detection and resolution
 * - Retry mechanisms and fault tolerance
 * - System monitoring and status tracking
 */
public class ConcurQueue {
    private static final Logger logger = LoggerFactory.getLogger(ConcurQueue.class);

    // Configuration constants
    private static final int WORKER_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;
    private static final int RUNTIME_SECONDS = 60;

    // Core components
    private final PriorityBlockingQueue<Task> taskQueue;
    private final LinkedBlockingQueue<Task> retryQueue;
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap;
    private final ThreadPoolExecutor workerPool;
    private final AtomicBoolean systemRunning;
    private final AtomicInteger processedCount;
    private final AtomicInteger failedCount;

    // Demonstrate race condition (initially unsafe)
    private volatile int unsafeCounter = 0;
    private final AtomicInteger safeCounter = new AtomicInteger(0);

    // Threads
    private final ExecutorService producerPool;
    private final ExecutorService monitorPool;
    private SystemMonitor systemMonitor;

    public ConcurQueue() {
        // Initialize core data structures
        this.taskQueue = new PriorityBlockingQueue<>(QUEUE_CAPACITY);
        this.retryQueue = new LinkedBlockingQueue<>();
        this.taskStatusMap = new ConcurrentHashMap<>();
        this.systemRunning = new AtomicBoolean(true);
        this.processedCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);

        // Initialize thread pools
        this.workerPool = new ThreadPoolExecutor(
                WORKER_POOL_SIZE,
                WORKER_POOL_SIZE,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "Worker-" + threadNumber.getAndIncrement());
                    }
                }
        );

        this.producerPool = Executors.newFixedThreadPool(3);
        this.monitorPool = Executors.newSingleThreadExecutor();

        // Setup shutdown hook
        setupShutdownHook();
    }

    public void start() {
        logger.info("Starting ConcurQueue system...");

        // Start system monitor
        systemMonitor = new SystemMonitor(
                taskQueue, retryQueue, taskStatusMap, workerPool,
                systemRunning, processedCount, failedCount
        );
        monitorPool.submit(systemMonitor);

        // Start worker threads
        startWorkerThreads();

        // Start producer threads with different strategies
        startProducerThreads();


        logger.info("ConcurQueue system started successfully");
    }

    private void startWorkerThreads() {
        // Worker threads that process both regular and retry queues
        for (int i = 0; i < WORKER_POOL_SIZE; i++) {
            workerPool.submit(() -> {
                TaskProcessor processor = new TaskProcessor(
                        taskQueue, retryQueue, taskStatusMap, processedCount, failedCount
                );
                processor.run();
            });
        }

        // Separate thread to move retry tasks back to main queue
        workerPool.submit(this::processRetryQueue);
    }

    private void startProducerThreads() {
        // Producer 1: High priority focused
        producerPool.submit(new TaskProducer(
                "HighPriorityProducer", taskQueue, taskStatusMap, systemRunning,
                3, 5, ProducerStrategy.HIGH_PRIORITY_FOCUSED
        ));

        // Producer 2: Balanced
        producerPool.submit(new TaskProducer(
                "BalancedProducer", taskQueue, taskStatusMap, systemRunning,
                5, 3, ProducerStrategy.BALANCED
        ));

        // Producer 3: Low priority bulk
        producerPool.submit(new TaskProducer(
                "BulkProducer", taskQueue, taskStatusMap, systemRunning,
                8, 7, ProducerStrategy.LOW_PRIORITY_BULK
        ));
    }

    private void processRetryQueue() {
        logger.info("Retry processor thread started."); // Added for clarity
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task retryTask = retryQueue.take();

                // Add delay before retry
                Thread.sleep(2000);

                // Move back to main queue
                taskQueue.put(retryTask);
                logger.info("Moved retry task back to main queue: {}", retryTask);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Retry processor thread interrupted", e);
                break;
            }
        }
        logger.info("Retry processor thread stopped."); // Added for clarity
    }


    public void runForDuration(int seconds) {
        try {
            logger.info("Running system for {} seconds...", seconds);
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("System run interrupted", e);
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        logger.info("Shutting down ConcurQueue system...");

        // Stop accepting new tasks
        systemRunning.set(false);

        // Shutdown producer threads
        producerPool.shutdown();
        try {
            if (!producerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                producerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            producerPool.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for producer pool to terminate", e);
        }

        // Process remaining tasks in queue
        logger.info("Processing remaining tasks in queue...");
        drainQueue();

        // Shutdown worker pool
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Worker pool did not terminate gracefully, forcing shutdown");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for worker pool to terminate, forcing shutdown", e);
        }

        // Shutdown monitor
        monitorPool.shutdown();
        try {
            if (!monitorPool.awaitTermination(2, TimeUnit.SECONDS)) {
                monitorPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorPool.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for monitor pool to terminate", e);
        }

        // Print final statistics
        printFinalStatistics();

        logger.info("ConcurQueue system shutdown complete");
    }

    private void drainQueue() {
        int drainedTasks = 0;
        // Changed condition: ensure system is not running or taskQueue is not empty to avoid infinite loop
        while ((systemRunning.get() || !taskQueue.isEmpty()) && drainedTasks < 50) { // Limit to prevent excessive draining
            try {
                Task task = taskQueue.poll(100, TimeUnit.MILLISECONDS); // Use poll with timeout
                if (task != null) {
                    // In a real system, these would be properly processed
                    taskStatusMap.put(task.getId().toString(), TaskStatus.COMPLETED);
                    processedCount.incrementAndGet();
                    drainedTasks++;
                    logger.info("Drained task: {}", task);
                } else {
                    // No tasks in queue, maybe all processed
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Drain queue interrupted", e);
                break;
            }
        }
        // Changed: SLF4J parameterized logging
        logger.info("Drained {} remaining tasks", drainedTasks);
    }

    private void printFinalStatistics() {
        if (systemMonitor != null) {
            SystemMonitor.SystemStats stats = systemMonitor.getCurrentStats();

            logger.info("=== FINAL SYSTEM STATISTICS ===");
            logger.info("Total tasks processed: {}", stats.processedCount());
            logger.info("Total tasks failed: {}", stats.failedCount());
            logger.info("Tasks remaining in queue: {}", stats.queueSize());
            logger.info("Tasks in retry queue: {}", stats.retryQueueSize());
            logger.info("Worker pool completed tasks: {}", stats.completedTasks());

            logger.info("Task status breakdown:");
            stats.statusCounts().forEach((status, count) ->
                    logger.info("  {}: {}", status, count));
        } else {
            logger.warn("System monitor was not initialized, cannot print detailed final statistics.");
        }
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered");
            if (systemRunning.get()) {
                shutdown();
            }
        }, "ShutdownHook")); // Added name to the shutdown hook thread
    }

    public static void main(String[] args) {

        logger.info("Starting ConcurQueue demonstration...");

        try {
            ConcurQueue system = new ConcurQueue();
            system.start();

            // Run for specified duration
            int runtimeSeconds = args.length > 0 ? Integer.parseInt(args[0]) : RUNTIME_SECONDS;
            system.runForDuration(runtimeSeconds);

        } catch (NumberFormatException e) {
            logger.error("Invalid duration argument: {}. Using default runtime of {} seconds.", args.length > 0 ? args[0] : "N/A", RUNTIME_SECONDS, e);
            new ConcurQueue().runForDuration(RUNTIME_SECONDS); // Run with default if argument is bad
        } catch (Exception e) {
            logger.error("System error during demonstration: {}", e.getMessage(), e);
        }

        logger.info("ConcurQueue demonstration completed");
    }
}