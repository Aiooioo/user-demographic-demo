1. 最小化组件栈 (MVS)
   您只需要在本地安装 Java/Maven 和 Docker Desktop。

| 组件类别 | 组件 | 启动方式 | 角色 (在原型中) |
| --- | --- | --- | --- |
| 基础设施 | Nacos | Docker | Spring Cloud 的服务注册与发现中心。 |
| | Kafka | Docker | 实时数据总线 (MQ)。(使用单节点 bitnami/kafka 镜像) |
| | Redis | Docker | 高性能画像存储 (Profile Store)。 (替代 Aerospike/ScyllaDB) |
| 数据存储 | 本地文件系统 | N/A | “伪”数据湖 (Data Lake)。 (例如: /tmp/ad-events.log) |
| 计算引擎 | Flink | 本地 IDE 运行,流批一体计算引擎。 (在 IDEA/VSCode 中直接 main 方法启动) | 
| 微服务 | Spring Gateway | Spring Boot | API 网关。 (所有请求的入口) | 
| | collector-service | Spring Boot | 数据采集服务。 (接收 HTTP 请求，写入 Kafka 和本地文件) |
| | profile-service | Spring Boot | 画像查询服务。 (对外提供 REST API，查询 Redis) |

2. 原型的数据模型
   我们设计两个核心模型：

A. 事件模型 (Event Model): AdEvent.java 这是在 collector-service 和 Flink 中流转的数据。

```json
{
    "eventId": "uuid-12345",
    "userId": "u-001",
    "eventType": "click", // "click", "impression", "purchase"
    "timestamp": 1731296325000, // 事件时间 (Event Time)
    "category": "sports",
    "amount": 0.0 // 如果是 purchase 事件
}
```




B. 画像模型 (Profile Model): Redis HASH 这是 profile-service 最终返回的 JSON 格式，也是我们在 Redis 中的存储结构。

Redis Key: profile:{userId} (例如: profile:u-001)

Redis Type: HASH (完美对应 Flink MapState)

Hash Fields (示例):

| Field (标签 Key) | Value (标签 Value) | 计算来源 |
| --- | --- | --- |
| last_active_ts | 1731296325000 | 实时 (Flink) |
| last_click_category | """sports""" | 实时 (Flink) |
| clicks_1h_count | 5 | 实时 (Flink) |
| ltv_total | 150.75 | 离线 (Flink) |
| segment_id | """high_value_user""" | 离线 (Flink) |

🌊 核心数据流设计 (The Workflow)
这是跑通原型的关键。我们将设计三条并行的工作流。

工作流 A：实时画像处理 (Hot Path)

目标： 计算 last_click_category, clicks_1h_count 等实时标签。

1. [用户/测试] 向 Spring Gateway 发送一个 HTTP POST 请求：POST /collect/event，Body 为 AdEvent JSON。

1. [Spring Gateway] 将请求路由到 collector-service。

1. [collector-service] (Spring Boot) 收到事件后，执行两个操作： a. (实时) 将 AdEvent JSON 发送到 Kafka 的 realtime-events Topic。 b. (离线) 将 AdEvent JSON 追加到本地文件 /tmp/ad-events.log 中（模拟数据入湖）。

1. [Flink 实时作业] (在 IDE 中运行) a. 消费 Kafka realtime-events Topic。 b. keyBy(event -> event.getUserId())。 c. 使用 ProcessFunction 和 MapState（如我们昨天所讨论的）来计算画像标签（例如：last_click_category）。 d. 使用 Flink 官方的 Flink-Connector-Redis，将计算结果（一个 Map<String, String>）直接 HSET 写入 Redis 中对应的 profile:{userId}。

工作流 B：离线画像处理 (Cold Path)
目标： T+1 计算 ltv_total (生命周期总价值), segment_id (用户分群) 等复杂标签。

1. [用户/测试] 我们模拟“T+1”调度。向 Spring Gateway 发送一个 HTTP GET 请求：GET /job/run-batch。

1. [Spring Gateway] 将请求路由到（我们可以为此新建一个）batch-trigger-service，或者就在 collector-service 上加个端点。

1. [Trigger-Service] 收到请求后，通过命令行（Runtime.getRuntime().exec(...)）或 Flink REST API，启动一个新的 Flink 作业——离线批处理作业。

1. [Flink 离线作业] (在 IDE 中以 ExecutionMode.BATCH 模式启动) a. (数据源) 读取本地文件 /tmp/ad-events.log (使用 FileSource，这就是有界流)。 b. keyBy(event -> event.getUserId())。 c. 执行复杂的批处理聚合逻辑（例如：sum(amount) 得到 ltv_total）。 d. 同样使用 Flink-Connector-Redis，将批处理结果（ltv_total）也 HSET 写入到同一个 Redis HASH 中（profile:{userId}）。

工作流 C：画像服务 (Serving Path)
目标： 融合“实时”和“离线”标签，对外提供查询。

1. [用户/Bidder 模拟器] 向 Spring Gateway 发送一个 HTTP GET 请求：GET /profile/user/u-001。

1. [Spring Gateway] 将请求路由到 profile-service。

1. [profile-service] (Spring Boot) a. (可选) 通过 Nacos 发现 Redis 的地址（原型中可写死 localhost:6379）。 b. 使用 Spring Data Redis (Jedis/Lettuce) 连接 Redis。 c. 执行 HGETALL profile:u-001 命令。 d. Redis 返回一个包含所有标签（无论来自实时还是离线）的 HASH。 e. profile-service 将这个 HASH 序列化为 JSON 并返回给用户。

🚀 您的本地行动计划 (Step-by-Step)
1. 环境准备 (15 分钟)：

- 安装并启动 Docker Desktop。

- 在 docker-compose.yml 文件中定义 Nacos, Kafka (单节点), Redis，一键启动 docker-compose up -d。

2. 创建 Spring Cloud 服务 (30 分钟)：

- 使用 start.spring.io 创建三个 Maven/Gradle 项目：

  - gateway (依赖: Spring Cloud Gateway, Nacos Discovery)

  - collector-service (依赖: Web, Nacos Discovery, Kafka)

  - profile-service (依赖: Web, Nacos Discovery, Redis)

- 在 application.yml 中配置 Nacos 地址和 Kafka/Redis 地址。

3. 创建 Flink 作业 (1 小时)：

- 创建一个单独的 Maven 项目 flink-jobs。

- 引入 flink-streaming-java, flink-connector-kafka, flink-connector-redis 依赖。

- 创建 RealtimeProfileJob.java：
  - StreamExecutionEnvironment.getExecutionEnvironment()
  - Source: KafkaSource 
  - Process: KeyedProcessFunction + MapState 
  - Sink: RedisSink (写入 Redis HASH)
- 创建 OfflineProfileJob.java：
  - StreamExecutionEnvironment.getExecutionEnvironment()，并设置 RuntimeMode.BATCH。 
  - Source: FileSource.forRecordStreamFormat(...) (读取 /tmp/ad-events.log)
  - Process: AggregateFunction or ProcessFunction 
  - Sink: RedisSink (写入 Redis HASH)

4. 联调测试 (30 分钟)：
   1. 在 IDEA 中启动 gateway, collector-service, profile-service。

   2. 在 IDEA 中启动 RealtimeProfileJob.java (它会常驻运行)。

   3. 测试实时流： a. 用 Postman 调用 collector-service 发送几条 u-001 的 "click" 事件。 b. 立刻用 Postman 调用 profile-service 查询 u-001，您应该能看到 clicks_1h_count 在变化。

   4. 测试离线流： a. 用 Postman 调用 collector-service 发送几条 u-001 的 "purchase" 事件（amount > 0）。 b. 在 IDEA 中启动 OfflineProfileJob.java (它跑完会自动停止)。 c. 再次调用 profile-service 查询 u-001，您应该能看到 ltv_total 字段被添加或更新了。

这个最小化原型方案可以让您在一天之内，就在本机上直观地看到“流批一体”的 Flink 作业是如何与 Spring Cloud 微服务协作，共同构建一个融合画像的。

