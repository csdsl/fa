# 面试模拟总结 - Day 2

## 总体评分：6.5/10

---

## Q1: KWork智能结账Agent的工具调用设计（编排顺序+失败回滚）

**你的回答：** 单一职责、清晰描述与结构化参数、面向任务的高阶工具优先、程序可解析的返回格式、状态与上下文管理

**评分：6/10**

**参考答案要点：**
- 工具拆分：5个独立工具（checkVoucher、exchangeAdjust、profitCarryOver、depreciationCalc、periodClose），每个输入输出结构化JSON，返回含nextStep字段
- 执行编排：Prompt引导+工具返回驱动，System Prompt定义流程DAG，Agent根据返回动态调整。不用硬编码workflow引擎，因为不同组织结账流程有差异
- 异常回滚：每步执行前记checkpoint到Redis（状态快照），失败时调rollbackTo工具按逆序补偿（Saga模式）
- 防护措施：periodClose工具内部硬约束校验前置步骤是否完成，防止LLM跳步

---

## Q2: Agent会话状态持久化（用户中断后恢复进度）

**你的回答：** checkpoint中添加执行步骤状态等信息，redis持久化

**评分：4/10**

**参考答案要点：**
- 业务状态：Redis Hash存储 `kwork:checkout:{orgId}:{period}`，field为步骤名，value为状态JSON（status/startTime/result/checkpointData）
- 持久化双写：先写MySQL `t_checkout_task` 表再写Redis，Redis是热数据加速层
- 对话上下文：MySQL `t_chat_memory` 表按session_id存历史消息，恢复时注入Agent ChatMemory
- 恢复触发：前端调 `/api/checkout/resume`，后端检查未完成任务，加载历史对话，Agent从断点继续
- 过期策略：Redis TTL 7天，超时标记为已超时

---

## Q3: ThreadPoolExecutor参数设定（50万资产卡片折旧计算）

**你的回答：** CPU密集+少量IO，核心线程8+1，最大线程2倍，1000一批分500批，队列500。压测后调优为核心12-15，最大20，队列256

**评分：7/10**

**参考答案补充：**
- 队列从500调到256的原因：队列太长导致尾延迟高，缩短让CallerRunsPolicy更早介入，整体吞吐更均匀
- 拒绝策略：CallerRunsPolicy（让提交线程自己执行，天然限流不丢任务）
- 异常处理：CompletableFuture收集结果，失败批次重试3次，数据异常的卡片隔离标记
- 压测观察指标：CPU利用率（目标80-85%）、GC频率、任务完成时间分布

---

## Q4: 分布式事务（信用卡还款场景）

**你的回答：** 本地消息表+异步保证最终一致性

**评分：5/10**

**参考答案要点：**
- 为什么不用Seata/TCC：全局锁性能差、TCC侵入性强，还款允许秒级延迟
- 流程：本地事务内扣余额+插消息表 → 异步发MQ → 信用卡服务消费更新账单 → 对账服务记录流水
- 消息表结构：msg_id, biz_type, payload, status(PENDING/SENT/FAILED), retry_count
- 可靠性：定时任务30s扫描PENDING消息重投，最大重试5次超过告警
- 消费端幂等：msg_id做幂等键，Redis判重
- 防重复扣款：requestId + 数据库乐观锁version字段

---

## Q5: RabbitMQ消息可靠性保证

**你的回答：** 对消息进行持久化

**评分：3/10**

**参考答案要点（三阶段保障）：**
- 生产端→Broker：Publisher Confirm + Return回调，ack成功更新消息表状态
- Broker存储：Exchange/Queue/Message三个持久化 + 镜像队列多节点同步
- Broker→消费端：手动ack模式，处理成功才basicAck，异常basicNack+requeue，超过3次进死信队列DLX
- 兜底：本地消息表定时对账，SENT状态超2分钟未确认的重新投递

---

## Q6: Redis缓存与数据库一致性（用户修改手机号）

**你的回答：** 旁路策略 + Binlog+Canal保证

**评分：6/10**

**参考答案要点：**
- Cache-Aside具体顺序：先更新DB再删缓存（不是更新缓存，避免并发覆盖）
- 为什么这个顺序：先删缓存再更新DB有经典并发问题（读线程写回旧值）
- Canal兜底：MySQL Binlog → Canal → MQ → 缓存清理消费者，延迟500ms-1s
- 线上问题：Canal消费积压导致缓存延迟5分钟，解决方案是应用层直接删缓存为主路径+Canal异步兜底+积压监控告警

---

## Q7: 缓存击穿防护（月初大量用户查账单）

**你的回答：** Redisson分布式锁+随机过期时间，DB读写分离，请求级别缓存，限流降级，监控告警

**评分：7/10**

**参考答案补充：**
- 区分概念：击穿=单个热点key过期，雪崩=大批key同时过期。随机过期时间解决的是雪崩
- 击穿方案：Redisson锁+逻辑过期，抢到锁的线程异步刷新，抢不到的返回旧数据
- 双重检查：加锁后再查一次缓存，防止重复回源
- 预热：月初账单是可预测热点，凌晨定时任务批量预热
- 本地缓存：Caffeine做L1缓存，TTL 10秒减少Redis访问

---

## Q8: 服务间调用选型（Feign vs Dubbo）+ 超时处理

**你的回答：** 用Feign，Spring Cloud全家桶成本低，RESTful API，遇到过超时

**评分：5/10**

**参考答案要点：**
- 选型三维度：HTTP协议跨语言友好、生态整合度高、QPS几千级性能够用
- 超时实战：账户服务热点账户行锁竞争导致P999飙高，解决方案是区分服务配置不同超时时间
- 熔断降级：Sentinel配置失败率50%触发熔断，降级逻辑记录补偿队列而非直接返回成功
- 重试策略：只对读操作重试，写操作绝不重试防重复扣款

---

## Q9: Eureka vs Nacos选型 + 服务上下线感知

**你的回答：** 没有思考过

**参考答案要点：**
- Nacos优势：AP/CP可切换、自带配置中心、服务端主动探测、持续维护、Web控制台
- Eureka感知延迟最慢90秒（3个30s叠加）
- 解决方案：优雅下线主动注销、缩短拉取间隔到10s、Ribbon重试切换节点、K8s preStop hook等待20s

---

## Q10: AI融入ERP生成业务单据的实现

**你的回答：** 生成业务单据，封装业务API，集成免费大模型，MCP注册服务，提示词匹配字段，必录字段提示

**评分：7/10**

**参考答案补充：**
- 完整流程：用户自然语言 → LLM意图识别+字段提取 → 必录字段校验追问 → MCP Tool调用 → 业务API → 生成单据 → 用户确认
- 工具schema设计：每个工具有完整的参数描述、类型、required标记
- 准确率保障：few-shot prompt + 业务校验 + 用户确认兜底

---

## Q11: MCP vs Function Calling区别00ms内。

---

## Q7: Spring Boot自动配置原理 + 自定义Starter设计

**问题：** 自动配置原理？怎么设计一个多租户starter？

**参考答案：**

自动配置链路：
`@SpringBootApplication` → `@EnableAutoConfiguration` → `AutoConfigurationImportSelector` → 读取`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（3.x）→ 加载自动配置类 → `@Conditional`系列注解决定哪些生效

关键注解：
- `@ConditionalOnClass`：classpath有某类才生效
- `@ConditionalOnMissingBean`：用户没自定义才用默认的
- `@ConditionalOnProperty`：配置文件有某属性才生效

多租户Starter设计：
- TenantAutoConfiguration：自动配置类，注册拦截器、数据源路由、SQL拦截器
- TenantProperties：`@ConfigurationProperties(prefix = "tenant")`
- 所有Bean加`@ConditionalOnMissingBean`允许用户覆盖
- 提供`tenant.enabled=false`一键关闭

使用方只需引入依赖+加配置，开箱即用。

---

## Q8: 单体到微服务迁移规划

**问题：** 8人团队，单体迁微服务，怎么规划？风险？怎么保证业务不受影响？

**参考答案：**

迁移步骤（渐进式）：
1. 单体内模块化：按领域拆代码模块，禁止直接依赖实现类，数据库按模块命名
2. 基础设施先行：容器化部署、可观测体系（集中日志、监控、链路埋点）
3. 识别拆分目标：高耦合或高负载模块，先拆边界清晰的服务
4. 逐步引入服务治理：注册发现、配置中心、熔断限流
5. 团队能力升级

保证业务不受影响：
- 绞杀者模式：新老系统并行，通过网关路由流量
- 灰度发布：先切10%流量验证
- 数据双写：迁移数据库时双写+对账
- 回滚预案：网关层一键切回老系统

主要风险：
- 分布式事务复杂度 → 优先最终一致性
- 调用链路变长性能下降 → 性能基线对比
- 团队不熟悉微服务运维 → 先搭CI/CD和监控
- 拆分粒度不当 → 先粗拆后细分

时间线（8人团队约6个月）：
- 第1-2月：模块化+基础设施
- 第3-4月：拆2-3个边界清晰的服务
- 第5-6月：拆核心业务+全量切流+稳定性验证
