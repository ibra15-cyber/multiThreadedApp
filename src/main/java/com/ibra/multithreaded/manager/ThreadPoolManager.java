package com.ibra.multithreaded.manager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolManager.class);

    private final ThreadPoolExecutor workerPool;
    private final ExecutorService producerPool;
    private final ExecutorService monitorPool;

    public ThreadPoolManager(int workerPoolSize) {
        this.workerPool = new ThreadPoolExecutor(
                workerPoolSize,
                workerPoolSize,
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
    }

    public void shutdownAll() {
        shutdownPool(producerPool, "Producer Pool", 5);
        shutdownPool(workerPool, "Worker Pool", 10);
        shutdownPool(monitorPool, "Monitor Pool", 2);
    }

    private void shutdownPool(ExecutorService pool, String poolName, int timeoutSeconds) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                logger.warn("{} did not terminate gracefully, forcing shutdown", poolName);
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for {} to terminate", poolName, e);
        }
    }

    public ThreadPoolExecutor getWorkerPool() { return workerPool; }
    public ExecutorService getProducerPool() { return producerPool; }
    public ExecutorService getMonitorPool() { return monitorPool; }
}