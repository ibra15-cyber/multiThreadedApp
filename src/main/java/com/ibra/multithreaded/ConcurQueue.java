package com.ibra.multithreaded;

import com.ibra.multithreaded.manager.*;
import com.ibra.multithreaded.producer.ProducerStrategy;
import com.ibra.multithreaded.producer.TaskProducer;
import com.ibra.multithreaded.worker.TaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcurQueue {
    private static final Logger logger = LoggerFactory.getLogger(ConcurQueue.class);

    // Configuration constants
    private static final int WORKER_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;
    private static final int RUNTIME_SECONDS = 60;

    // Managers
    private final QueueManager queueManager;
    private final ThreadPoolManager threadManager;
    private final StatisticsManager statsManager;
    private final ShutdownManager shutdownManager;

    public ConcurQueue() {
        this.queueManager = new QueueManager(QUEUE_CAPACITY);
        this.threadManager = new ThreadPoolManager(WORKER_POOL_SIZE);
        this.statsManager = new StatisticsManager(queueManager, threadManager);
        this.shutdownManager = new ShutdownManager(queueManager, threadManager, statsManager);
    }

    public void start() {
        logger.info("Starting ConcurQueue system...");

        // Setup shutdown hook
        shutdownManager.setupShutdownHook();

        // Start monitoring
        statsManager.startMonitoring(shutdownManager.getSystemRunning());

        // Start worker threads
        startWorkerThreads();

        // Start producer threads
        startProducerThreads();

        logger.info("ConcurQueue system started successfully");
    }

    private void startWorkerThreads() {
        // Start worker threads
        for (int i = 0; i < WORKER_POOL_SIZE; i++) {
            threadManager.getWorkerPool().submit(new TaskProcessor(
                    queueManager.getTaskQueue(),
                    queueManager.getRetryQueue(),
                    queueManager.getTaskStatusMap(),
                    queueManager.getProcessedCount(),
                    queueManager.getFailedCount()
            ));
        }
    }

    private void startProducerThreads() {
        // Start different types of producers
        startProducer("HighPriorityProducer", 3, 5, ProducerStrategy.HIGH_PRIORITY_FOCUSED);
        startProducer("BalancedProducer", 5, 3, ProducerStrategy.BALANCED);
        startProducer("BulkProducer", 8, 7, ProducerStrategy.LOW_PRIORITY_BULK);
    }

    private void startProducer(String name, int rate, int batchSize, ProducerStrategy strategy) {
        threadManager.getProducerPool().submit(new TaskProducer(
                name,
                queueManager.getTaskQueue(),
                queueManager.getTaskStatusMap(),
                shutdownManager.getSystemRunning(),
                rate,
                batchSize,
                strategy
        ));
    }

    public void runForDuration(int seconds) {
        try {
            logger.info("Running system for {} seconds...", seconds);
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("System run interrupted", e);
        } finally {
            shutdownManager.initiateShutdown();
        }
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
            logger.error("Invalid duration argument: {}. Using default runtime of {} seconds.",
                    args.length > 0 ? args[0] : "N/A", RUNTIME_SECONDS, e);
            new ConcurQueue().runForDuration(RUNTIME_SECONDS);
        } catch (Exception e) {
            logger.error("System error during demonstration: {}", e.getMessage(), e);
        }

        logger.info("ConcurQueue demonstration completed");
    }
}