package com.ibra.multithreaded.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Task model representing a job to be processed by the ConcurQueue system.
 * Implements Comparable for priority queue handling.
 */
public class Task implements Comparable<Task> {
    private final UUID id;
    private final String name;
    private final int priority;
    private final Instant createdTimestamp;
    private final String payload;
    private int retryCount;
    private Instant lastProcessedTimestamp;

    public Task(String name, int priority, String payload) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.priority = priority;
        this.payload = payload;
        this.createdTimestamp = Instant.now();
        this.retryCount = 0;
    }

    public Task(Task original) {
        this.id = original.id;
        this.name = original.name;
        this.priority = original.priority;
        this.payload = original.payload;
        this.createdTimestamp = original.createdTimestamp;
        this.retryCount = original.retryCount + 1;
        this.lastProcessedTimestamp = original.lastProcessedTimestamp;
    }

    @Override
    public int compareTo(Task other) {
        // Higher priority tasks come first (reverse order)
        int priorityComparison = Integer.compare(other.priority, this.priority);
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        // If same priority, earlier tasks come first
        return this.createdTimestamp.compareTo(other.createdTimestamp);
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getPriority() { return priority; }
    public Instant getCreatedTimestamp() { return createdTimestamp; }
    public String getPayload() { return payload; }
    public int getRetryCount() { return retryCount; }
    public Instant getLastProcessedTimestamp() { return lastProcessedTimestamp; }

    public void setLastProcessedTimestamp(Instant timestamp) {
        this.lastProcessedTimestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("Task{id=%s, name='%s', priority=%d, retryCount=%d, created=%s}",
                id.toString().substring(0, 8), name, priority, retryCount, createdTimestamp);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Task task = (Task) obj;
        return id.equals(task.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
