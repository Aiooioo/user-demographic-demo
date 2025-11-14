1. 最小原型组件选型 (MVS)

我们的目标是在您的本地机器（例如一台笔记本电脑）上跑起来。我们将使用 docker-compose 来管理“平台组件”，使用您的 IDE (如 IntelliJ/Eclipse) 来运行 Spring Cloud 微服务。

A. 平台组件 (使用 Docker Compose 管理)

您只需要一个 docker-compose.yml 文件，包含 3 个轻量级服务：

1. 消息总线 (Message Bus)：Kafka x 1

   - 生产环境： 高可用的 Kafka 集群。 
   - 本地原型： docker-compose 启动一个单节点的 Kafka（和它依赖的 Zookeeper）。这是我们所有数据的“主动脉”。

2. 画像存储 (Profile Store)：Redis x 1

   - 生产环境： Aerospike / ScyllaDB。 
   - 本地原型： Redis。它是一个完美的高性能 K-V 库，并且是一个出色的 Redis Hash 结构，与我们之前讨论的 MapState 或画像模型（user_id -> Map<Tag, Value>）完美契合。

3. 服务发现 (Discovery)：Eureka Server x 1

   - 原型要求： 这是 Spring Cloud 的核心。我们需要它来让所有微服务互相“找到”对方。 
   - 实现： 一个最简单的 Spring Boot 应用，加上 @EnableEurekaServer 注解即可。

B. 应用组件 (Spring Cloud 微服务)

您需要编写 4 个核心的 Spring Boot 微服务：

1. ingestion-gateway (数据采集网关)

   - 技术栈： Spring Cloud Gateway + Spring Boot Web 
   - 职责： 模拟数据采集入口。它提供一个 REST API (如 /event)，接收您模拟的“用户行为数据”（JSON），然后立即将其推送到 Kafka 的 user_events 主题中。

2. stream-processor (实时流处理器)

   - 技术栈： Spring Cloud Stream (重点！)
   - 职责： 这是我们的**“实时链路（Hot Path）”**。
     - 它使用 @StreamListener (或新的函数式 Consumer) 监听 user_events 主题。
     - [核心] 它使用 Kafka Streams (KStreams) 库（Spring Cloud Stream 完美集成）来进行有状态计算。它会按 userId 进行 groupBy，并使用 KTable（Kafka Streams 版的 MapState）来维护“实时画像”（例如：1h_click_count）。
     - [写入] 它将计算出的实时画像标签写入 Redis（HSET profile:<userId> 1h_click_count 5）。
     - [归档] 它同时将原始事件转存到本地磁盘（例如 /tmp/data-lake/ 目录下的 JSON 文件），模拟“数据湖归档”。

3. batch-processor (离线批处理器)

   - 技术栈： Spring Batch + Spring Boot Web 
   - 职责： 这是我们的**“离线链路（Cold Path）”**。
     - 它提供一个 REST API (如 /run-batch-job) 让你手动触发 T+1 批处理。
     - [核心] 当被触发时，一个 Spring Batch Job 会启动。
     - [读取] 它的 ItemReader 会去扫描 /tmp/data-lake/ 目录下的所有原始事件文件。
     - [处理] 它的 ItemProcessor 会执行复杂的聚合（例如：计算总购买金额 total_ltv）。
     - [写入] 它的 ItemWriter 会将这个“离线标签”也写入 Redis（HSET profile:<userId> total_ltv 999.99）。

4. profile-service (画像服务 API)

   - 技术栈： Spring Boot Web + Redis 
   - 职责： 模拟对竞价引擎（Bidder）提供的服务。
     - 它提供一个 REST API (如 GET /profile/{userId})。
     - [核心] 它接收请求，立即从 Redis 中使用 HGETALL profile:<userId> 命令，获取该用户所有的标签（包含实时标签和离线标签）。 
     - 它将这些标签组装成一个 JSON 对象并返回，实现了“画像融合”。

2. 原型数据模型

A. UserEvent (数据采集模型 - JSON)

这是您在调用 ingestion-gateway 时发送的 JSON 体：

```json
{
  "eventId": "uuid-12345-abcde",
  "userId": "u-001",
  "eventType": "click", // "click", "impression", "purchase"
  "timestamp": 1678886400000,
  "properties": {
    "category": "sports",
    "item_id": "item-abc",
    "page": "/products/item-abc",
    "value": 0 // 如果是 purchase 事件, 这里可以填金额
  }
}
```

B. Profile (画像存储模型 - Redis Hash)

这是存储在 Redis 中的结构（profile-service 将返回这个结构的 JSON 形式）：

- Key: profile:u-001

- Fields (K-V Map):
```json
{
  "last_active_ts": "1678886400000",
  "realtime_last_click_category": "sports",
  "realtime_clicks_1h": "12",
  "batch_total_purchase_value": "450.75", // 初始为 null
  "batch_inferred_gender": "male" // 初始为 null
}
```

3. 本地原型数据流 (如何跑通)
   请按照这个工作流来“玩”您的原型，您将获得极强的直观印象：

第 0 步：启动所有服务

1. 在本机运行 docker-compose up -d (启动 Kafka, Redis)。

2. 在您的 IDE 中，依次启动 eureka-server, ingestion-gateway, stream-processor, batch-processor, profile-service (共 5 个 Spring Boot 应用)。

第 1 步：实时数据采集 (模拟用户点击)

1. 打开一个 API 测试工具 (如 Postman)。

2. 发送一个 POST 请求到 http://localhost:8080/event (假设 8080 是 Gateway 端口)。

3. Body： (上面定义的 UserEvent JSON，userId 设为 u-001，eventType 设为 click)

4. 发生了什么？
   - ingestion-gateway 接收到请求，将其丢入 Kafka user_events 主题。

第 2 步：观察实时链路 (Hot Path)
1. 后台： stream-processor 立即收到了这个事件。

2. 后台： 它的 Kafka Streams KTable 状态被更新（例如 1h_click_count 增加了）。

3. 后台： 它向 Redis 发送了 HSET profile:u-001 realtime_clicks_1h 1。

4. 后台： 它同时在 /tmp/data-lake/ 目录里追加了一行 UserEvent 的 JSON 文本。

5. 您的操作： 立即在 Postman 中发送 GET 请求到 http://localhost:9090/profile/u-001 (假设 9090 是 profile-service 端口)。

6. 直观印象 (Aha! 时刻 1)： 您会立即收到一个 JSON，其中 realtime_clicks_1h 已经是 1。而 batch_total_purchase_value 此时是 null。

第 3 步：重复第 1 步 (模拟更多行为)

1. 多发送几个事件，eventType 可以是 click 或 purchase (例如 value: 100)。

2. 您的操作： 每次发送后，都去刷新 GET /profile/u-001。

3. 直观印象 (Aha! 时刻 2)： 您会看到 realtime_clicks_1h 在实时变化，而 batch_total_purchase_value 始终是 null。您亲眼见证了实时链路的威力。

第 4 步：触发离线链路 (Cold Path)

1. 现在，假装时间到了“凌晨 T+1”。

2. 您的操作： 向 batch-processor 发送一个 POST 请求 http://localhost:9091/run-batch-job。

3. 发生了什么？
    - batch-processor 的 Spring Batch 任务启动。 
    - 它开始读取 /tmp/data-lake/ 里的所有日志文件。 
    - 它计算出 u-001 的总购买金额（total_ltv），例如 100。 
    - 它向 Redis 发送 HSET profile:u-001 batch_total_purchase_value 100。

第 5 步：观察最终融合画像

1. 批处理任务完成后（您可以看 batch-processor 的日志）。

2. 您的操作： 再次发送 GET 请求到 http://localhost:9090/profile/u-001。

3. 直观印象 (Aha! 时刻 3)：
    - 您收到的 JSON 变了！ 
    - realtime_clicks_1h 还是那个实时值（比如 12）。 
    - batch_total_purchase_value 不再是 null，它变成了 100。

恭喜！ 通过这个 5 步流程，您在本地、用最小代价、基于您熟悉的 Spring Cloud，完整地复现了生产环境下的Lambda 架构（实时采集、实时处理、离线批处理、服务层融合）。