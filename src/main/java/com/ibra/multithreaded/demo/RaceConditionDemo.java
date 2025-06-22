package com.ibra.multithreaded.demo;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates race conditions and their fixes in concurrent programming.
 * Shows the difference between unsafe shared state and thread-safe alternatives.
 */
public class RaceConditionDemo {
    private static final Logger logger = LoggerFactory.getLogger(RaceConditionDemo.class);

    private static int unsafeCounter = 0;

    private static final AtomicInteger safeCounter = new AtomicInteger(0);

    private static int synchronizedCounter = 0;
    private static final Object counterLock = new Object();

    public static void demonstrateRaceConditions() {
        logger.info("=== Demonstrating Race Conditions ===");

        final int numThreads = 10;
        final int incrementsPerThread = 1000;
        final int expectedTotal = numThreads * incrementsPerThread;

        logger.info("Test 1: Unsafe Counter");
        testUnsafeCounter(incrementsPerThread, expectedTotal);

        logger.info("Test 2: AtomicInteger Counter");
        testAtomicCounter(numThreads, incrementsPerThread, expectedTotal);

        logger.info("Test 3: Synchronized Counter");
        testSynchronizedCounter(numThreads, incrementsPerThread, expectedTotal);
    }

    private static void testUnsafeCounter(int incrementsPerThread, int expectedTotal) {
        unsafeCounter = 0; // Reset
        ExecutorService executor = Executors.newFixedThreadPool(10);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    // UNSAFE: Multiple threads can read-modify-write simultaneously
                    unsafeCounter++;
                    try {
                        Thread.sleep(0, 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                }
            });
        }

        shutdownAndWait(executor);
        long endTime = System.currentTimeMillis();

        logger.info("Unsafe Counter Result: {} (Expected: {}) - {}",
                unsafeCounter, expectedTotal,
                unsafeCounter == expectedTotal ? "CORRECT" : "RACE CONDITION DETECTED!");
        logger.info("Time taken: {} ms", endTime - startTime);

        // Show the lost updates
        if (unsafeCounter != expectedTotal) {
            logger.warn("Lost updates: {}", expectedTotal - unsafeCounter);
        }
    }

    private static void testAtomicCounter(int numThreads, int incrementsPerThread, int expectedTotal) {
        safeCounter.set(0);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    // SAFE: AtomicInteger.incrementAndGet() is atomic
                    safeCounter.incrementAndGet();
                }
            });
        }

        shutdownAndWait(executor);
        long endTime = System.currentTimeMillis();

        logger.info("AtomicInteger Result: {} (Expected: {}) - {}",
                safeCounter.get(), expectedTotal,
                safeCounter.get() == expectedTotal ? "CORRECT" : "ERROR");
        logger.info("Time taken: {} ms", endTime - startTime);
    }

    private static void testSynchronizedCounter(int numThreads, int incrementsPerThread, int expectedTotal) {
        synchronizedCounter = 0; // Reset
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    // SAFE: Synchronized block ensures atomic read-modify-write
                    synchronized (counterLock) {
                        synchronizedCounter++;
                    }
                }
            });
        }

        shutdownAndWait(executor);
        long endTime = System.currentTimeMillis();

        logger.info("Synchronized Counter Result: {} (Expected: {}) - {}",
                synchronizedCounter, expectedTotal,
                synchronizedCounter == expectedTotal ? "CORRECT" : "ERROR");
        logger.info("Time taken: {} ms", endTime - startTime);
    }

    private static void shutdownAndWait(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }


    public static void main(String[] args) {
        demonstrateRaceConditions();
    }

}