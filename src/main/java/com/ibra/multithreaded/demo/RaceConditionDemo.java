package com.ibra.multithreaded.demo;


import org.slf4j.Logger; // Import SLF4J Logger
import org.slf4j.LoggerFactory; // Import SLF4J LoggerFactory

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
// Removed: import java.util.logging.Logger; (original JDK logger)

/**
 * Demonstrates race conditions and their fixes in concurrent programming.
 * Shows the difference between unsafe shared state and thread-safe alternatives.
 */
public class RaceConditionDemo {
    private static final Logger logger = LoggerFactory.getLogger(RaceConditionDemo.class);

    // Unsafe counter - will show race conditions
    private static int unsafeCounter = 0;

    // Thread-safe counter using AtomicInteger
    private static final AtomicInteger safeCounter = new AtomicInteger(0);

    // Thread-safe counter using synchronization
    private static int synchronizedCounter = 0;
    private static final Object counterLock = new Object();

    public static void demonstrateRaceConditions() {
        logger.info("=== Demonstrating Race Conditions ===");

        final int numThreads = 10;
        final int incrementsPerThread = 1000;
        final int expectedTotal = numThreads * incrementsPerThread;

        // Test 1: Unsafe counter (will show race conditions)
        logger.info("Test 1: Unsafe Counter");
        testUnsafeCounter(numThreads, incrementsPerThread, expectedTotal);

        // Test 2: AtomicInteger (thread-safe)
        logger.info("Test 2: AtomicInteger Counter");
        testAtomicCounter(numThreads, incrementsPerThread, expectedTotal);

        // Test 3: Synchronized counter (thread-safe)
        logger.info("Test 3: Synchronized Counter");
        testSynchronizedCounter(numThreads, incrementsPerThread, expectedTotal);
    }

    private static void testUnsafeCounter(int numThreads, int incrementsPerThread, int expectedTotal) {
        unsafeCounter = 0; // Reset
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.currentTimeMillis();

        // Submit tasks that increment unsafe counter
        for (int i = 0; i < numThreads; i++) {
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
        safeCounter.set(0); // Reset
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.currentTimeMillis();

        // Submit tasks that increment atomic counter
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

        // Changed: SLF4J parameterized logging
        logger.info("AtomicInteger Result: {} (Expected: {}) - {}",
                safeCounter.get(), expectedTotal,
                safeCounter.get() == expectedTotal ? "CORRECT" : "ERROR");
        logger.info("Time taken: {} ms", endTime - startTime);
    }

    private static void testSynchronizedCounter(int numThreads, int incrementsPerThread, int expectedTotal) {
        synchronizedCounter = 0; // Reset
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.currentTimeMillis();

        // Submit tasks that increment synchronized counter
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