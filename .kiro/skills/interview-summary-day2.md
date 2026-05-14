---
inclusion: manual
---

# 面试模拟总结 - 第二天

## Q1: MySQL联合索引设计 + 索引失效排查

**问题：** 资产卡片表50万+数据，按org_id + status + entry_date范围查询，怎么建索引？用了索引还是慢怎么排查？

**参考答案：**

索引：`idx_org_status_date(org_id, status, entry_date)`

顺序原因：org_id和status是等值查询放前面，entry_date是范围查询放最后。联合索引遇到范围查询后面的列就用不上了。entry_date放最后还能利用索引有序性避免filesort。

用了索引还是慢的排查：
- explain看type（range/ref）、rows估算值、Extra（Using filesort/Using temporary）
- 回表问题：SELECT * 导致大量回表，改成覆盖索引
- 数据分布问题：如果该org_id下40万条都是NORMAL，索引过滤效果差
- 深分页问题：LIMIT 10000,20用延迟关联或游标分页

补充：WHERE条件的书写顺序不影响索引使用，MySQL优化器会自动重排匹配最优索引。

---

## Q2: 微服务线上故障排查（user-service响应飙升到5秒）

**问题：** user-service响应从50ms飙到5秒，下游服务超时，怎么排查和处理？

**参考答案：**

第一步：先止血（1分钟内）
- 下游触发熔断降级，不再调user-service
- 判断是否需要紧急扩容或重启

第二步：快速定位层级（5分钟内）
- Grafana看CPU/内存/GC/线程数/连接池
- Zipkin看请求卡在哪个环节
- 数据库监控：连接数是否打满、SHOW PROCESSLIST

第三步：缩小范围
- 数据库层面：死锁日志、慢查询日志
- 应用层面：jstack看线程状态（BLOCKED/WAITING）
- GC问题：GC日志，Full GC是否频繁
- 连接池耗尽：HikariCP监控

第四步：Arthas精确定位
- `thread -n 3` 看最忙线程
- `trace` 追踪方法耗时
- `watch` 观察入参返回值

第五步：修复并复盘

核心原则：先恢复，再定位，最后根治。

---

## Q3: Sentinel vs Hystrix熔断策略对比

**问题：** 两者区别？银行App中怎么配置熔断规则？

**参考答案：**

核心区别：
- Hystrix：主要通过请求失败率触发，维度单一，配置多为静态编码，已停止维护，线程池隔离开销大
- Sentinel：支持多维度（异常比例、异常数、慢调用RT），支持Nacos动态配置，信号量隔离更轻量，有Dashboard可视化

熔断状态机：Hystrix经典三态（Closed→Open→Half-Open），Sentinel多了慢调用比例策略，恢复更平滑

银行App配置示例：
- 调用user-service：慢调用比例策略，RT阈值500ms，比例阈值60%，熔断时长10秒，最小请求数5
- 含义：5个请求中60%超过500ms就熔断10秒，10秒后放探测请求

选Sentinel的原因：Hystrix停止维护、线程池隔离开销大、Sentinel有Dashboard+Nacos动态推送。

---

## Q4: 多租户数据隔离架构设计

**问题：** 大客户要物理隔离，中小客户共享数据库，怎么设计同时支持两种模式？

**参考答案：**

核心思路：一套代码，多种隔离策略，通过配置切换。

三层架构：

1. 租户路由层：TenantContext（ThreadLocal）存租户ID，拦截器从Token/Header解析

2. 数据源路由层：
   - 租户配置表记录每个租户的隔离模式（SHARED/EXCLUSIVE）和数据源信息
   - AbstractRoutingDataSource实现动态数据源切换
   - determineCurrentLookupKey()根据当前租户ID查配置返回对应数据源

3. 数据隔离策略：
   - 独立数据库（大客户）：每个租户独立数据库实例
   - 共享数据库（中小客户）：通过tenant_id字段区分，MyBatis拦截器自动注入WHERE条件

防泄露兜底：所有表必须有tenant_id字段+索引，拦截器强制注入，定期审计脚本。

扩展性：新租户按套餐自动分配模式，提供升级迁移工具。

---

## Q5: 多租户Redis缓存Key设计 + 大租户防护

**问题：** Redis Key怎么设计避免覆盖？大租户数据量大怎么处理？

**参考答案：**

Key命名规范：`{tenantId}:{业务模块}:{具体Key}`

封装TenantRedisTemplate自动拼接前缀，开发者无需关心。

为什么不用Hash存所有数据：大Key问题（>10MB影响性能）、无法单独设TTL、删除时阻塞Redis。

大租户防护方案：
- 方案A：应用层按租户设内存配额，超配额触发LRU淘汰
- 方案B：大客户独立Redis实例，中小客户共享集群
- 方案C：Redis Cluster Hash Tag + 配额控制

监控：按前缀统计Key数量和内存占用，单租户超30%告警，大Key检测。

---

## Q6: JVM问题实战排查（内存泄漏）

**问题：** 项目中实际遇到的JVM问题，怎么发现、排查、解决？

**参考答案（结合资产折旧场景）：**

发现：Grafana告警GC暂停从几十ms飙到3-5秒，Full GC从每天1-2次变成每小时多次。

排查：
1. GC日志：老年代占用持续增长，Full GC后回收不了多少，典型内存泄漏
2. jmap导出堆转储，MAT分析
3. Leak Suspects：一个静态HashMap占老年代60%，存了大量AssetCard对象

根因：折旧计算时用静态HashMap做本地缓存，没有大小限制和过期策略，只进不出。

解决：
1. 替换为Caffeine缓存（maximumSize=10000, expireAfterWrite=10min）
2. 批量任务结束后主动invalidateAll()
3. JVM参数：堆从4G调到8G，新生代比例调大

效果：Full GC从每小时多次降到每天1次，暂停从3-5秒降到100ms内。

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
