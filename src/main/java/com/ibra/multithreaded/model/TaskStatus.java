package com.ibra.multithreaded.model;

/**
 * Enumeration representing the various states a task can be in
 * throughout its lifecycle in the ConcurQueue system.
 */
public enum TaskStatus {
    SUBMITTED("Task has been submitted to the queue"),
    PROCESSING("Task is currently being processed by a worker"),
    COMPLETED("Task has been successfully completed"),
    FAILED("Task processing has failed"),
    RETRYING("Task is being retried after a failure");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + ": " + description;
    }
}
