# Gemini Project Context: User Demographic Demo

This document provides a comprehensive overview of the User Demographic Demo project, intended to be used as a context file for AI-assisted development.

## Project Overview

This is a Java-based microservices project designed for real-time user profiling. The system ingests user interaction events (like ad clicks and impressions), processes them in a streaming fashion to calculate user metrics, and stores the resulting profiles in a Redis data store. A RESTful API is provided to query the generated user profiles.

### Core Technologies

*   **Backend:** Java 17, Spring Boot 3
*   **Build:** Apache Maven
*   **Data Streaming:** Apache Flink, Apache Kafka
*   **Data Storage:** Redis
*   **Service Architecture:** Spring Cloud Gateway, Spring Cloud Nacos (for service discovery)
*   **Containerization:** Docker

## Architecture

The project follows a microservices architecture, composed of several distinct modules:

*   `profile-gateway`: The public-facing entry point for all API requests. It's a Spring Cloud Gateway application that routes traffic to the appropriate downstream services.

*   `profile-collector`: The data ingestion and processing engine.
    *   It exposes a REST endpoint (`/collect/event`) to receive `AdEvent` data.
    *   Incoming events are published to a Kafka topic for real-time processing.
    *   An Apache Flink job (`FlinkRealtimeProfileJob`) consumes events from Kafka, performs stateful aggregations (e.g., counting clicks over a 24-hour window), and writes the resulting profile tags into Redis.

*   `profile-service`: The service responsible for serving user profile data.
    *   It provides a REST endpoint (`/profile/{userId}`) to retrieve a user's profile.
    *   Data is fetched directly from the Redis store populated by the `profile-collector`.

*   `profile-common`: A shared library containing common data models, such as `AdEvent`, used across all services to ensure consistency.

### Data Flow

1.  A client sends an `AdEvent` (e.g., a click) to the `profile-gateway`.
2.  The gateway forwards the request to the `profile-collector`'s `/collect/event` endpoint.
3.  `profile-collector` sends the event to a Kafka topic.
4.  The Flink job within `profile-collector` reads from Kafka.
5.  The job aggregates data (e.g., counts clicks for the user) and stores the result (e.g., `clicks_24h = 15`) in a Redis Hash under the key `profile:<userId>`.
6.  A client requests a user's profile from the `profile-gateway`, which routes the call to `profile-service`.
7.  `profile-service` queries Redis for the `profile:<userId>` key and returns the complete hash.

## Building and Running

### Prerequisites

*   Java 17
*   Apache Maven
*   Docker and Docker Compose

### 1. Build the Project

Compile and package all the Java modules from the project root:

```bash
mvn clean install
```

### 2. Run Infrastructure

Start the required backing services (Kafka and Redis) using Docker Compose:

```bash
docker-compose up -d
```

### 3. Run the Applications

Each microservice is a standalone Spring Boot application. You need to run them individually.

**TODO:** A script to launch all services with a single command would be beneficial.

```bash
# In separate terminals:
java -jar profile-gateway/target/profile-gateway-*.jar
java -jar profile-collector/target/profile-collector-*.jar
java -jar profile-service/target/profile-service-*.jar
```

**Note:** Service discovery via Nacos is configured in the POM files, but a Nacos server is not included in the `docker-compose.yml`. For a full production-like setup, a Nacos instance would need to be running.

## Development Conventions

*   **Code Style:** The project follows standard Java and Spring Boot conventions.
*   **Configuration:** Application properties are managed in `application.properties` or `application.yaml` files within each service's resources.
*   **Data Model:** Shared data structures are centralized in the `profile-common` module.
*   **Testing:** Unit and integration tests are located in the `src/test/java` directory of each module and can be run with `mvn test`. The root `pom.xml` is currently configured to skip tests (`<skipTests>true</skipTests>`); this should be set to `false` for active development.
