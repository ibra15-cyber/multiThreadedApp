
# ConcurQueue – A Multithreaded Job Processing Platform

ConcurQueue is a high-performance, multithreaded job dispatcher system designed by NovaTech Solutions to efficiently handle multiple producer clients submitting jobs and distribute those jobs to worker threads for concurrent processing. This project demonstrates core Java concurrency primitives, including synchronization, blocking queues, thread pools, concurrent collections, and addresses common concurrency issues.

## Table of Contents

* [Overview](https://www.google.com/search?q=%23overview)
* [Design Explanation](https://www.google.com/search?q=%23design-explanation)
    * [Task Model](https://www.google.com/search?q=%23task-model)
    * [Producers](https://www.google.com/search?q=%23producers)
    * [Consumers (Worker Pool)](https://www.google.com/search?q=%23consumers-worker-pool)
    * [Concurrent Queue Management](https://www.google.com/search?q=%23concurrent-queue-management)
    * [Monitoring & Status Tracker](https://www.google.com/search?q=%23monitoring--status-tracker)
    * [Synchronization Challenges](https://www.google.com/search?q=%23synchronization-challenges)
    * [Extensibility Features](https://www.google.com/search?q=%23extensibility-features)
* [How to Run](https://www.google.com/search?q=%23how-to-run)
* [Project Structure](https://www.google.com/search?q=%23project-structure)

## Overview

ConcurQueue simulates a backend job queue system where:

* Multiple producers (representing APIs or clients) submit tasks.
* Tasks are consumed and processed by worker threads.
* Tasks have different priorities and simulate various execution times.
* The system ensures no task is lost, duplicated, or executed by multiple threads simultaneously.

This project offers hands-on experience with the Java Memory Model, visibility issues, avoiding race conditions, implementing concurrent collections and executor services, handling producer-consumer patterns using BlockingQueues, and managing deadlocks and starvation.

## Design Explanation

The ConcurQueue system is built around several key components that interact to simulate a robust job processing platform.

### Task Model

* **`Task.java`**: Represents a single job in the system.
    * **Attributes**: `id` (UUID), `name` (String), `priority` (int), `createdTimestamp` (Instant), `payload` (String).
    * **Comparable**: Implements `Comparable<Task>` to allow `PriorityBlockingQueue` to order tasks. Tasks with higher priority are processed first. If priorities are equal, tasks are ordered by their `createdTimestamp`.
    * **Retry Count**: Includes a `retryCount` to track retries for failed tasks.
    * **Processing Start Time**: Includes `lastProcessedTimestamp` to track when a task last processed, useful for detecting stalled tasks.

### Producers

* **`TaskProducer.java`**: Simulates clients submitting tasks.
    * **Configuration**: The system starts 3 producers, each generating `N` tasks every few seconds.
    * **Strategies (`ProducerStrategy.java`)**: Different producers utilize distinct strategies to generate tasks with varying priorities:
        * `HIGH_PRIORITY_FOCUSED`: Generates mostly high-priority tasks.
        * `BALANCED`: Distributes priorities evenly.
        * `LOW_PRIORITY_BULK`: Generates many low-priority tasks.
    * **Functionality**: Producers place tasks into the shared `taskQueue` and update the `taskStatusMap` to `SUBMITTED`.

### Consumers (Worker Pool)

* **`TaskProcessor.java`**: Represents individual worker threads that process tasks.
    * **Implementation**: Utilizes a `ThreadPoolExecutor` with a fixed thread pool size (`WORKER_POOL_SIZE` = 4) for efficient task execution.
    * **Processing Logic**: Each worker fetches a task from the `taskQueue`, simulates work using `Thread.sleep` (with a duration based on task priority), and logs processing events including the thread name and timestamp.
    * **Failure Simulation**: Tasks have a `FAILURE_PROBABILITY` (10% chance) to simulate processing failures.
    * **Status Tracking**: Updates the `taskStatusMap` to `PROCESSING`, `COMPLETED`, or `FAILED` as tasks move through their lifecycle.

### Concurrent Queue Management

* **`PriorityBlockingQueue<Task> taskQueue`**: The main queue where producers submit tasks and consumers retrieve them. It automatically orders tasks based on their priority (from `Task.compareTo()`).
* **`LinkedBlockingQueue<Task> retryQueue`**: A separate queue for tasks that failed and are eligible for retry.
* **`ConcurrentHashMap<String, TaskStatus> taskStatusMap`**: A thread-safe map (`UUID` of task to `TaskStatus` enum) used to track the real-time state of every task in the system: `SUBMITTED`, `PROCESSING`, `COMPLETED`, `FAILED`, `RETRYING`. This ensures visibility and atomicity across multiple threads.

### Monitoring & Status Tracker

* **`SystemMonitor.java`**: A background thread that provides real-time insights into the system's health.
    * **Metrics**: Logs queue sizes (`taskQueue`, `retryQueue`), thread pool status (active, total, completed tasks), and counts of processed and failed tasks every 5 seconds.
    * **Status Breakdown**: Provides a breakdown of task counts by `TaskStatus`.
    * **Stalled Task Detection**: Identifies potential issues by checking the number of tasks in the `PROCESSING` state. While it primarily counts, a more advanced version could track start times for precise stalled task detection.
    * **JSON Export**: Optionally exports comprehensive system status (including detailed task statuses) to a JSON file every minute for external analysis.

### Synchronization Challenges

* The project deliberately introduces a shared counter (`unsafeCounter`) that is accessed unsafely to demonstrate **race conditions**.
* It then shows how to **fix** these race conditions using `AtomicInteger` (`safeCounter`) or by employing `synchronized` blocks, ensuring thread-safe operations.
* *(Optional: Deadlock Demonstration)* The framework allows for the demonstration of a simple deadlock scenario by creating two threads that attempt to acquire two locks in opposite orders. This can then be resolved by enforcing a consistent lock ordering. (Note: This specific demonstration is conceptual and would require adding dedicated code to `ConcurQueue` or a separate class.)

### Extensibility Features

* **Retry Mechanism**:
    * Failed tasks are re-queued into the `retryQueue` up to a maximum of 3 times (`MAX_RETRIES`).
    * A dedicated worker thread (`retryQueueProcessor`) takes tasks from the `retryQueue`, introduces a delay, and then re-submits them to the `taskQueue` for re-processing.
* **Shutdown Hook**: Implements a `Runtime.getRuntime().addShutdownHook()` to gracefully shut down the system. This hook ensures that `workerPool` and `monitorPool` are shut down and attempts to drain any remaining tasks from the `taskQueue` before the application exits.
* **Bounded Queue**: The `taskQueue` (`PriorityBlockingQueue`) is initialized with a `QUEUE_CAPACITY` (100), making it a bounded queue. Producers will block if the queue is full, preventing system overload.

## How to Run

To run the ConcurQueue application, follow these steps:

1.  **Prerequisites**:

    * Java Development Kit (JDK) 21 or higher.
    * Apache Maven 3.x or higher.

2.  **Compile the Project**:

    * Navigate to the root directory of the project where `pom.xml` is located.
    * Compile the project using Maven:
      ```bash
      mvn clean install
      ```
    * This command will compile the Java source code, run tests, and package the application into an executable JAR file in the `target/` directory.

3.  **Run the Application**:

    * You can run the application directly from Maven or execute the generated JAR.

    * **Running with Maven Exec Plugin (Default Duration)**:

        * The `pom.xml` is configured to run for 30 seconds by default using the `exec-maven-plugin`.

      <!-- end list -->

      ```bash
      mvn compile exec:java
      ```

    * **Running with Maven Profiles for Specific Durations**:

        * **1-minute demo**:

          ```bash
          mvn compile exec:java -Pdemo
          ```

        * **5-minute extended run**:

          ```bash
          mvn compile exec:java -Pextended
          ```

    * **Running the Executable JAR**:

        * After `mvn clean install`, an executable JAR (e.g., `concurqueue-1.0.0.jar`) will be created in the `target/` directory.
        * You can run it and specify the runtime duration in seconds as an argument:
          ```bash
          java -jar target/concurqueue-1.0.0.jar [runtime_seconds]
          ```
          For example, to run for 120 seconds:
          ```bash
          java -jar target/concurqueue-1.0.0.jar 120
          ```
          If no `runtime_seconds` argument is provided, the application will run for a default duration of 60 seconds.

4.  **Observe Output**:

    * The application will log extensive output to the console, showing producer activity, task processing by workers, task status changes, and system monitoring reports.
    * JSON status files will be generated in the project root directory (e.g., `concurqueue_status_XXXXXXXXX.json`) every minute, providing a snapshot of the system state.

## Project Structure

```
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── ibra/
│                   └── multithreaded/
│                       ├── ConcurQueue.java           # Main application class
│                       ├── model/
│                       │   ├── Task.java              # Represents a job/task
│                       │   └── TaskStatus.java        # Enum for task states
│                       ├── monitor/
│                       │   └── SystemMonitor.java     # Monitors system health and exports stats
│                       ├── producer/
│                       │   ├── ProducerStrategy.java  # Enum for producer behavior
│                       │   └── TaskProducer.java      # Generates and submits tasks
│                       └── worker/
│                           └── TaskProcessor.java     # Processes tasks from the queue
└── pom.xml                                            # Maven configuration
```