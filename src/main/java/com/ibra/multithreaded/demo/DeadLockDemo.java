package com.ibra.multithreaded.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates a simple deadlock scenario and its resolution
 */
public class DeadLockDemo {
    private static final Logger logger = LoggerFactory.getLogger(DeadLockDemo.class);

    public static void demonstrateDeadlock() {
        logger.info("=== Demonstrating Deadlock Prevention ===");

        final Object lock1 = new Object();
        final Object lock2 = new Object();

        // This could cause deadlock if not handled properly
        Thread t1 = new Thread(() -> {
            synchronized (lock1) {
                logger.info("Thread 1: Acquired lock1");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                synchronized (lock2) {
                    logger.info("Thread 1: Acquired lock2");
                }
            }
        }, "DeadlockDemo-1");

        Thread t2 = new Thread(() -> {
            synchronized (lock2) { // Same order as Thread 1
                logger.info("Thread 2: Acquired lock1");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                synchronized (lock1) {
                    logger.info("Thread 2: Acquired lock2");
                }
            }
        }, "DeadlockDemo-2");

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
            logger.info("Deadlock demo completed successfully - no deadlock occurred!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        demonstrateDeadlock();
    }
}
