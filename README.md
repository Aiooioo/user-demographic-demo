# User Profile System Demo for Ad Platform

This project is a prototype demonstration of a user profiling system, specifically designed for an advertising platform. It showcases a complete end-to-end data pipeline that processes user events in both real-time and batch modes to generate valuable user profile tags. These tags can then be consumed by downstream systems, such as an ad bidding service, to make informed, millisecond-level decisions.

## Project Goal

The primary goal of this demo is to illustrate how to build a scalable and responsive user profile system by:

1.  **Ingesting** high-throughput user events (impressions, clicks, purchases).
2.  **Processing** these events via two parallel paths:
    *   A **real-time stream processing** path for immediate, short-term user behavior tags (e.g., "clicks in the last 5 minutes").
    *   An **offline batch processing** path for long-term, computationally intensive user value tags (e.g., "total lifetime value").
3.  **Fusing** the real-time and offline tags into a unified user profile.
4.  **Serving** the unified profile through a low-latency API.

## Features

- **Microservices Architecture**: The system is decoupled into specialized services for scalability and maintainability.
- **Dual Data Processing**: Implements both stream (real-time) and batch (offline) processing using Apache Flink.
- **Real-time Profile Tags**: Generates tags based on recent user activity (e.g., `views_5m`, `clicks_24h`).
- **Offline Profile Tags**: Calculates long-term user metrics (e.g., `ltv_total`, `segment_id`).
- **Unified Profile API**: A single API endpoint provides a merged view of both real-time and offline tags for any user.
- **Simulation Ready**: Includes a Python script to generate mock user event data for easy testing.

## Architecture

The system is composed of several microservices and data infrastructure components that work together.

```
+-----------------+      +------------------+      +---------------------+
|  Event Source   |----->| profile-gateway  |----->|  profile-collector  |
| (user-events.py)|      | (API Gateway)    |      | (Event Ingestion)   |
+-----------------+      +------------------+      +---------------------+
                                                          |
                                                          |
                                     +--------------------+--------------------+
                                     | (writes to)        | (writes to)        |
                                     v                    v                    v
                              +------------+      +----------------+      +-----------+
                              | ODS File   |      | Kafka Topic    |      |   Redis   |
                              | (for Batch)|      | (for Real-time)|      | (Profile) |
                              +------------+      +----------------+      +-----------+
                                     ^                    ^                    ^
                                     | (reads from)       | (reads from)       |
                                     |                    |                    |
      +------------------------+     |                    |     +-------------------------+
      | Flink Offline Job      |-----+                    +-----| Flink Real-time Job     |
      | (calculates ltv_total) |                                | (calculates clicks_24h) |
      +------------------------+                                +-------------------------+
```

### Data Flow

1.  **Ingestion**: The `user-events.py` script (or any client) sends `AdEvent` JSON data to the `profile-gateway`.
2.  **Collection**: The gateway forwards the request to `profile-collector`. This service then persists the event in two places:
    *   It sends the event to a **Kafka topic** for the real-time pipeline.
    *   It appends the event to a local **ODS file** for the offline pipeline.
3.  **Real-time Processing**: The `FlinkRealtimeProfileJob` consumes from Kafka, calculates metrics over sliding windows, and writes tags like `clicks_24h` to a Redis Hash for the corresponding user.
4.  **Offline Processing**: When triggered via an API call, the `FlinkOfflineProfileJob` reads all historical data from the ODS file, calculates long-term metrics like `ltv_total`, and writes the resulting tags to the same Redis Hash.
5.  **Serving**: A client requests a user's profile from `profile-gateway`, which routes the call to `profile-service`. This service reads all tags for the user from the Redis Hash and returns them as a single, "fused" JSON object.

## Technology Stack

- **Backend**: Java 17, Spring Boot 3, Spring Cloud
- **Data Processing**: Apache Flink
- **Messaging**: Apache Kafka
- **In-Memory Storage**: Redis
- **Build Tool**: Apache Maven
- **Containerization**: Docker, Docker Compose
- **Service Discovery**: Nacos (Assumed to be provided externally)

## Getting Started

### Prerequisites

- Java 17 (or higher)
- Apache Maven 3.6+
- Docker & Docker Compose
- Python 3.x (for the event simulator)

### 1. Build the Project

Compile and package all Java modules from the project root:

```bash
mvn clean install
```

### 2. Run Infrastructure

Start the required backing services (Kafka and Redis) using Docker Compose:

```bash
docker-compose up -d
```

### 3. Run the Applications

Each microservice is a standalone Spring Boot application. You need to run them individually in separate terminals.

```bash
# Terminal 1: Run the Gateway
java -jar profile-gateway/target/profile-gateway-*.jar

# Terminal 2: Run the Collector (This will also start the real-time Flink job)
java -jar profile-collector/target/profile-collector-*.jar

# Terminal 3: Run the Profile Service
java -jar profile-service/target/profile-service-*.jar
```
**Note**: Ensure your external Nacos service is running and accessible to the applications.

## Usage & Testing

Once all services are running, you can test the full data pipeline.

### 1. Generate Mock Data

Open a new terminal and run the provided Python script. This will start sending a continuous stream of user events to the system.

First, install the required library:
```bash
pip install requests
```

Then, run the script:
```bash
python scripts/user-events.py
```

### 2. Trigger the Offline Batch Job

After letting the event simulator run for a while, some data will accumulate in the ODS file. Trigger the offline processing job with the following command:

```bash
curl -X POST http://localhost:8080/collect/batch
```

### 3. Query a User's Profile

Wait a few minutes for both the real-time and offline jobs to process data. Then, you can query the unified profile for any user ID (e.g., `user-101`).

```bash
curl http://localhost:8080/profile/user-101
```

You should receive a JSON response containing a mix of real-time and offline tags, for example:

```json
{
  "views_5m": "3",
  "clicks_24h": "1",
  "ltv_total": "470.5",
  "segment_id": "high_value_user"
}
```
This confirms that the entire data flow is working as expected.
