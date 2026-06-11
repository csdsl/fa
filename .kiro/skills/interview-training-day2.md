# 面试训练记录 - Day2（正确答案+回答指南）

---

## 题目1：KWork工具调用设计（编排顺序+失败回滚）

### 面试官问题
KWork的工具怎么设计的？编排顺序怎么定？某一步失败了怎么回滚？

### 标准答案（口述版）

**工具拆分**：5个独立工具——checkVoucher、exchangeAdjust、profitCarryOver、depreciationCalc、periodClose。每个工具输入输出结构化JSON，返回值含nextStep字段引导Agent下一步。

**执行编排**：Prompt引导 + 工具返回驱动。System Prompt定义流程DAG（步骤依赖关系），Agent根据每一步的返回动态调整。不用硬编码workflow引擎，因为不同组织结账流程有差异。

**异常回滚**：每步执行前记checkpoint到Redis（状态快照），失败时调rollbackTo工具按逆序补偿（Saga模式）。但实际上ERP结账的每一步是独立事务，已成功的步骤不应回滚——更像检查清单逐项完成。

**防护措施**：periodClose工具内部硬约束校验前置步骤是否全部完成，防止LLM跳步。

### 回答要点
1. 工具粒度："一个Tool对应一个业务动作的最小完整单元"
2. 编排方式：Prompt引导 + 返回值驱动，不用硬编码工作流
3. 异常策略：checkpoint + Saga补偿（但ERP场景通常不需要全局回滚）
4. 硬约束防跳步

---

## 题目2：Agent会话状态持久化（中断恢复）

### 面试官问题
用户结账到一半退出了，怎么保存进度？下次怎么恢复？

### 标准答案（口述版）

**业务状态持久化**：Redis Hash存储 `kwork:checkout:{orgId}:{period}`，field为步骤名，value为状态JSON（status/startTime/result/checkpointData）。

**持久化双写**：先写MySQL `t_checkout_task` 表再写Redis。Redis是热数据加速层，MySQL是持久化保障。

**对话上下文**：MySQL `t_chat_memory` 表按session_id存历史消息，恢复时注入Agent的ChatMemory。

**恢复触发**：前端调 `/api/checkout/resume`，后端检查未完成任务，加载历史对话上下文，Agent从断点继续。

**过期策略**：Redis TTL 7天，超时自动标记为"已超时"。

### 回答要点
1. 双写：MySQL持久化 + Redis加速
2. Redis Key设计：`kwork:checkout:{orgId}:{period}`
3. 对话上下文也要持久化（不只是业务状态）
4. 恢复是从断点继续，不是从头开始
5. 有过期策略

---

## 题目3：ThreadPoolExecutor参数设定（重复题，加深版）

### 面试官问题
资产折旧计算的线程池参数怎么来的？

### 标准答案（口述版）

同Day1题目4，补充以下关键点：

- 队列从500调到256的原因：队列太长导致尾延迟高，缩短让CallerRunsPolicy更早介入，整体吞吐更均匀
- 压测观察指标：CPU利用率（目标60-70%）、GC频率、任务完成时间分布（P99）
- 异常处理：CompletableFuture收集结果，失败批次重试3次，数据异常的卡片隔离标记

### 回答要点
1. 比Day1多说队列缩短的原因和压测指标
2. 结合异常隔离一起说

---

## 题目4：分布式事务（信用卡还款，重复题加深版）

### 面试官问题
还款场景的分布式事务怎么做？为什么不用Seata？

### 标准答案（口述版）

同Day1题目10，额外补充：

**消息表结构**：msg_id, biz_type, payload, status(PENDING/SENT/FAILED), retry_count, create_time, update_time

**为什么不用Seata/TCC**：
- Seata AT模式需要全局锁，高并发下性能差
- TCC侵入性强，每个参与方要实现Try/Confirm/Cancel三个方法
- 外部银行渠道不一定支持Cancel操作
- 还款允许秒级延迟，不需要强一致

**防重复扣款**：requestId + 数据库乐观锁version字段

### 回答要点
1. 先说为什么不用Seata/TCC（3个理由）
2. 本地消息表具体字段设计
3. 防重复扣款机制

---

## 题目5：RabbitMQ消息可靠性（重复题加深版）

### 面试官问题
只说了持久化，还有呢？三个环节分别怎么保障？

### 标准答案（口述版）

三阶段保障：

**生产端→Broker**：Publisher Confirm + Return回调。ack成功更新消息表状态为SENT，失败重投。

**Broker存储**：Exchange/Queue/Message三个持久化 + 镜像队列（Quorum Queue）多节点同步。

**Broker→消费端**：手动ack模式，处理成功才basicAck。异常basicNack+requeue，超过3次进死信队列DLX。

**兜底**：本地消息表定时对账，SENT状态超2分钟未确认的重新投递。

### 回答要点
1. 必须三个环节都覆盖，不能只说一个
2. 每个环节至少2个技术手段
3. 兜底方案

---

## 题目6：Redis缓存与数据库一致性

### 面试官问题
用户修改手机号后，缓存和数据库怎么保持一致？

### 标准答案（口述版）

**主方案：Cache-Aside（旁路缓存）**
- 读：先查缓存，miss查DB，回填缓存
- 写：先更新DB，再删缓存（不是更新缓存，避免并发覆盖）

**为什么是"先更新DB再删缓存"而不是"先删缓存再更新DB"**：
先删缓存有经典并发问题——线程A删缓存→线程B读miss→线程B从DB读旧值回填缓存→线程A更新DB。结果缓存里是旧值。

**Canal兜底**：MySQL Binlog → Canal → MQ → 缓存清理消费者。延迟500ms-1s，但能兜住应用层删缓存失败的情况。

**线上踩坑**：Canal消费积压导致缓存延迟5分钟。解决方案：应用层直接删缓存为主路径 + Canal异步兜底 + 积压监控告警。

### 回答要点
1. 先说方案：Cache-Aside
2. 写操作顺序及原因（先DB后删缓存）
3. 为什么不用另一种顺序（并发问题）
4. Canal做异步兜底
5. 讲一个线上踩坑经历（Canal积压）

---

## 题目7：缓存击穿防护

### 面试官问题
月初大量用户查账单，热点Key过期了怎么办？

### 标准答案（口述版）

先区分概念：
- **击穿** = 单个热点key过期，大量请求穿透到DB
- **雪崩** = 大批key同时过期（随机过期时间解决的是这个）

击穿方案：

1. **Redisson分布式锁 + 逻辑过期**：抢到锁的线程异步刷新缓存，抢不到的返回旧数据（逻辑过期时间已到但物理key还在）
2. **双重检查**：加锁后再查一次缓存，防止重复回源
3. **预热**：月初账单是可预测热点，凌晨定时任务批量预热
4. **本地缓存**：Caffeine做L1缓存，TTL 10秒减少Redis访问

### 回答要点
1. 先区分击穿和雪崩（面试官常考）
2. 核心方案：分布式锁 + 逻辑过期
3. 预热策略（可预测的热点）
4. 多级缓存：Caffeine L1 + Redis L2

---

## 题目8：Feign vs Dubbo选型 + 超时处理

### 面试官问题
服务间调用用什么？遇到过什么问题？

### 标准答案（口述版）

**选型**：用Feign。三个理由：
- HTTP协议跨语言友好（银行有些服务是.NET）
- Spring Cloud生态整合度高，和Eureka/Sentinel无缝配合
- QPS几千级，性能够用（Dubbo的高性能在万级QPS以上才有明显优势）

**超时实战**：账户服务热点账户行锁竞争导致P999飙高到3秒。解决方案：
- 区分服务配置不同超时时间（账户服务3s，通知服务1s）
- 热点账户请求串行化（用Redis分布式锁）

**熔断降级**：Sentinel配置失败率50%触发熔断。降级逻辑不是直接返回成功，而是记录到补偿队列后续重试。

**重试策略**：只对读操作重试，写操作绝不重试（防重复扣款）。

### 回答要点
1. 选型理由（3个维度）
2. 具体超时案例和解决方案
3. 熔断降级策略（不是返回成功，是补偿队列）
4. 重试的区分：读可重试，写不重试

---

## 题目9：Eureka vs Nacos + 服务上下线感知

### 面试官问题
注册中心用什么？Eureka服务下线感知延迟怎么办？

### 标准答案（口述版）

**Nacos优势**（对比Eureka）：
- AP/CP模式可切换（Eureka只有AP）
- 自带配置中心，不需要额外引入Spring Cloud Config
- 服务端主动探测（Eureka靠客户端心跳，被动感知）
- 社区持续维护（Eureka 2.x已停更）
- Web控制台开箱即用

**Eureka感知延迟问题**：最慢90秒才感知（3个30s叠加：客户端心跳30s + 服务端失效检测30s + 客户端拉取间隔30s）

**解决方案**：
- 优雅下线：主动调Eureka API注销实例
- 缩短拉取间隔到10s
- Ribbon重试：第一次调用失败自动切换节点
- K8s preStop hook等待20s，让流量排空

### 回答要点
1. Nacos 5个优势
2. Eureka延迟原因：3个30s叠加
3. 4个解决方案
4. 如果用的是Nacos就说Nacos怎么解决的（服务端推送+长轮询，秒级感知）

---

## 题目10：AI融入ERP生成业务单据

### 面试官问题
AI怎么帮助用户自动生成ERP业务单据？

### 标准答案（口述版）

完整流程：用户自然语言输入 → LLM意图识别+字段提取 → 必录字段校验（缺了追问用户）→ MCP Tool调用 → 业务API → 生成单据 → 用户确认后保存

**关键设计**：
- 工具schema设计：每个工具有完整的参数描述、类型、required标记，帮助LLM准确填参
- 必录字段处理：LLM发现缺少必录字段时，不是编造，而是追问用户补充
- 准确率保障：few-shot prompt（给几个示例）+ 业务校验（提交前后端校验）+ 用户确认兜底

**LLM选型**：集成免费/低成本大模型（通义千问），通过MCP注册ERP各模块的单据创建服务。

### 回答要点
1. 完整流程：输入→识别→校验→调用→确认
2. 必录字段缺失不编造，而是追问
3. 三层保障：few-shot + 业务校验 + 用户确认
4. 结合MCP架构说明扩展性

---

## 题目11：Spring Boot自动配置原理 + 自定义Starter

### 面试官问题
Spring Boot自动配置原理是什么？怎么设计一个多租户Starter？

### 标准答案（口述版）

**自动配置链路**：
`@SpringBootApplication` → `@EnableAutoConfiguration` → `AutoConfigurationImportSelector` → 读取 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（3.x写法） → 加载自动配置类 → `@Conditional`系列注解决定哪些生效

**关键注解**：
- `@ConditionalOnClass`：classpath有某类才生效
- `@ConditionalOnMissingBean`：用户没自定义才用默认的
- `@ConditionalOnProperty`：配置文件有某属性才生效

**多租户Starter设计**：
- `TenantAutoConfiguration`：自动配置类，注册拦截器、数据源路由、SQL拦截器
- `TenantProperties`：`@ConfigurationProperties(prefix = "tenant")`读取配置
- 所有Bean加`@ConditionalOnMissingBean`允许用户覆盖默认实现
- 提供`tenant.enabled=false`一键关闭

使用方只需引入maven依赖+加配置，开箱即用。

### 回答要点
1. 链路：注解→选择器→读配置文件→条件注解过滤
2. 三个关键@Conditional注解
3. Starter设计：AutoConfiguration + Properties + ConditionalOnMissingBean
4. 2.x vs 3.x区别（spring.factories vs imports文件）

---

## 题目12：单体到微服务迁移规划

### 面试官问题
8人团队要从单体迁移到微服务，怎么规划？有什么风险？

### 标准答案（口述版）

**迁移步骤（渐进式，不能一步到位）**：
1. 单体内模块化：按领域拆代码模块，禁止直接依赖实现类
2. 基础设施先行：容器化、可观测体系（日志、监控、链路追踪）
3. 识别拆分目标：先拆边界清晰、高负载的模块
4. 逐步引入服务治理：注册发现、配置中心、熔断限流
5. 团队能力升级

**保证业务不受影响**：
- 绞杀者模式：新老系统并行，网关路由流量
- 灰度发布：先切10%流量验证
- 数据双写+对账
- 回滚预案：网关一键切回老系统

**主要风险**：
- 分布式事务复杂度 → 优先最终一致性
- 链路变长性能下降 → 性能基线对比
- 团队不熟运维 → 先搭CI/CD和监控
- 拆分粒度不当 → 先粗拆后细分

**时间线（8人团队约6个月）**：
- 第1-2月：模块化+基础设施
- 第3-4月：拆2-3个边界清晰的服务
- 第5-6月：拆核心业务+全量切流+稳定性验证

### 回答要点
1. 关键词："渐进式"，不能Big Bang
2. 绞杀者模式是核心迁移策略
3. 风险必须主动提（面试官会追问）
4. 给出时间线体现项目管理能力
