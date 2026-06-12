# 面试训练记录 - Day5（智囊模式·正确答案+回答指南）

---

## 题目1：资产折旧计算整体优化方案

### 面试官问题
你在金蝶云星空旗舰版的资产管理模块中，对50万张资产卡片的折旧计算做了性能优化。请详细讲一下：当时遇到了什么问题？你是怎么设计的？最终效果如何？

### 标准答案（STAR格式，口述版）

【S】金蝶云星空旗舰版资产管理模块，客户有50万张资产卡片需要月末批量计算折旧。原方案单线程串行+逐条数据库读写，整个过程需要40多分钟，严重阻塞期末结账流程。

【T】我负责优化折旧计算性能，目标是将整体耗时压缩到5分钟以内。

【A】优化分三个维度，不只是加线程：

**1. 并行计算（解决CPU瓶颈）**
- 按资产类别+组织维度分片，每批2000张卡片，50万张约250个任务
- ThreadPoolExecutor：core=12，max=20，队列256，CallerRunsPolicy
- CompletableFuture提交任务，allOf等待汇总，每个分片独立异常隔离

**2. 批量IO（解决数据库瓶颈——最大收益点）**
- 原来逐条SELECT改为游标批量查：`WHERE id > lastId LIMIT 2000`
- 原来逐条INSERT改为MyBatis批量insertBatch（2000条一次提交）
- 数据库交互次数从50万次降到250次

**3. 缓存+计算优化（减少冗余）**
- 相同折旧方法的公式参数缓存复用（同一类别的卡片折旧率相同，不重复查询配置表）
- BigDecimal高频常量复用，避免GC压力
- 中间结果Redis缓存，支持断点续算

**异常处理**：失败分片重试3次（递增间隔），最终失败写fail_log表+告警，支持局部重算。

【R】整体从40分钟降到4分钟，提速10倍。其中并行贡献约8倍，批量IO优化额外贡献约3倍。失败影响面从整批回滚缩小到单卡片级。Full GC基本消除。

### 回答要点
1. 不只是"加线程"，是三个维度组合优化
2. 批量IO是最大收益点（面试官容易忽略，你主动点出加分）
3. 10倍的拆解逻辑：计算占35%（14分钟→1.5分钟），IO占65%（26分钟→2.5分钟）
4. 异常处理一句带过，面试官追问再展开

---

## 题目2：线程池参数详解

### 面试官问题
线程池最终参数是core=12，max=20，队列=256。这些数字怎么来的？为什么不是core=16？队列为什么不是1000？

### 标准答案（口述版）

**core=12 而不是16：**
服务器8核CPU。压测对比：
- core=16：CPU持续85%以上，GC频率升高（Young GC从每10秒变成每3秒），上下文切换开销吃掉并行收益
- core=12：CPU稳定60-70%，GC正常，吞吐量达到最佳

折旧计算不是纯CPU——每批有一次批量IO（读2000条+写2000条），实测线程数在核数的1.5倍（8×1.5=12）是最优解。留30% CPU余量给GC线程、Tomcat线程、监控采集。

**max=20：**
比core多8个，应对偶发IO抖动——某批写库稍慢时核心线程全在等IO，队列积压时max线程顶上。不设太大因为CPU就8核，开再多也是空转切换。

**队列=256 而不是1000：**
分片策略产生250个任务，256刚好容纳。队列太大有三个害处：
1. 尾延迟高——尾部任务等待时间长
2. 掩盖消费能力不足——看不到报错只看到队列增长
3. CallerRunsPolicy永远不会触发——失去天然限流效果

核心思路：CPU密集型任务，控制并发度比堆积任务更重要。

### 回答要点
1. 每个参数都要有压测数据支撑
2. 解释为什么留CPU余量
3. 队列大小关联分片数量
4. CallerRunsPolicy的限流作用
5. 追问"CallerRunsPolicy会不会阻塞主线程"→回答：正是要的效果（背压），但实际256能装下250个任务几乎不会触发

---

## 题目3：断点续算（宕机/失败恢复）

### 面试官问题
计算进行到一半，应用服务器宕机了或某一批任务执行失败了，怎么保证不重复算、不漏算、还能接着算？

### 标准答案（口述版）

三个保障：

**一、任务状态持久化（不漏算）**

每批分片提交前先写入任务追踪表`t_depreciation_batch`：
- 字段：batch_id, org_id, period, shard_index, status(PENDING/RUNNING/SUCCESS/FAILED), retry_count, start_card_id, end_card_id, worker_ip, create_time

流程：250个分片全写入表(PENDING) → 线程池领取改RUNNING → 完成改SUCCESS → 失败改FAILED。

宕机恢复：重启后扫描status=PENDING或RUNNING的分片重新提交。RUNNING超过10分钟视为超时，重置为PENDING。

**二、幂等写入（不重复算）**

折旧结果表有唯一约束：`UNIQUE INDEX uk_card_period (card_id, period)`

写入用`INSERT ... ON DUPLICATE KEY UPDATE`。重复执行相同分片，已写入的结果会被覆盖为相同值（折旧计算是确定性函数，相同输入结果一定相同）。

**三、断点续算（能接着算）**

```java
List<Batch> unfinished = batchMapper.selectByStatus(orgId, period, 
    List.of("PENDING", "FAILED", "RUNNING"));
// RUNNING超10分钟视为超时重置
// 只提交未完成的分片，SUCCESS的不再跑
submitToThreadPool(unfinished);
```

**失败重试控制**：retry_count<3回PENDING等待重调度，>=3改FAILED写fail_log+告警，需人工修复后手动触发。

一句话总结：任务表记进度，幂等保证可重入，超时检测防悬挂。

### 回答要点
1. 任务追踪表是核心（具体字段说出来）
2. 幂等靠唯一约束+确定性计算
3. 超时检测防"悬挂任务"
4. 追问"为什么不用MQ调度"→回答：250个任务全部已知，不是增量流式任务，数据库任务表更简单直观可查

---

## 题目4：CompletableFuture vs Future

### 面试官问题
thenApply和thenCompose有什么区别？为什么不用Future而用CompletableFuture？

### 标准答案（口述版）

**thenApply vs thenCompose：**
- thenApply = map：同步转换，`T -> U`
- thenCompose = flatMap：异步扁平化，`T -> CompletableFuture<U>`，避免嵌套

```java
// thenApply：同步格式化金额
cf.thenApply(amount -> amount.setScale(2, RoundingMode.HALF_UP));

// thenCompose：异步写库（返回的又是一个CF）
cf.thenCompose(amount -> asyncSaveToDB(cardId, amount));
```

用thenApply接一个返回CF的函数会得到`CompletableFuture<CompletableFuture<T>>`嵌套。thenCompose帮你打平。

**为什么不用Future：**

| 痛点 | Future | CompletableFuture |
|------|--------|-------------------|
| 等多个任务 | for循环逐个get()阻塞 | allOf()一行搞定 |
| 异常处理 | get()抛ExecutionException只能try-catch | exceptionally()链式，每个任务独立 |
| 任务编排 | 无法表达A完成后做B | thenApply/thenCompose链式编排 |
| 线程池指定 | 依赖提交时的ExecutorService | 每步xxxAsync(fn, executor) |

结合折旧场景的实际用法：
```java
List<CompletableFuture<BatchResult>> futures = shards.stream()
    .map(shard -> CompletableFuture.supplyAsync(() -> calculateShard(shard), pool)
        .exceptionally(ex -> BatchResult.failed(shard, ex)))
    .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .get(30, TimeUnit.MINUTES);
```

一句话：Future是"提交等结果"，CompletableFuture是"异步任务编排框架"。

### 回答要点
1. thenApply=map, thenCompose=flatMap（简洁类比）
2. 给代码示例区分
3. Future四个痛点对比表
4. 结合折旧场景的真实代码
5. 生产三要素：自定义线程池+超时控制+exceptionally异常隔离

---

## 题目5：JVM调优实战

### 面试官问题
你做过JVM调优吗？具体什么场景？怎么分析的？最后怎么调？

### 标准答案（STAR格式，口述版）

【S】资产折旧计算上线后，50万张卡片批量计算期间监控发现：Young GC每3-4秒一次（暂停50-80ms），偶发Full GC每小时1-2次（STW 2-3秒）。Full GC期间计算线程全部挂起。

【T】消除Full GC，降低Young GC频率。

【A】

**信息收集**：JDK17，G1GC，堆8G。jstat观察Eden每3秒满，Old缓慢增长。GC日志发现大量"Humongous Allocation"。

**根因定位**：G1的Region默认4MB（8G÷2048），超过2MB（50%）的对象直接进老年代。排查代码：
1. 每批2000条结果攒在ArrayList里≈3MB，超阈值→直接Humongous分配
2. BigDecimal大量临时对象快速填满Eden

**JVM参数调整**：
```
-XX:G1HeapRegionSize=16m      # 阈值升到8MB
-XX:MaxGCPauseMillis=100
-XX:G1NewSizePercent=40        # 年轻代最小40%
-XX:G1MaxNewSizePercent=60
```

**代码优化（更关键）**：
1. 每500条flush一次到数据库，不再攒2000条大数组
2. BigDecimal高频值（0,1,12,100）改static final常量
3. DepreciationResult用ThreadLocal对象复用

【R】Full GC完全消除。Young GC频率降低80%。计算性能额外提升15%。

### 回答要点
1. 必须有具体场景+具体数字
2. 定位链路：jstat→GC日志→Humongous→代码定位
3. 代码优化比JVM参数更关键（面试加分）
4. 追问"为什么不用ZGC"→批量计算不需极致低延迟，G1够用且成熟

---

## 题目6：分库分表方案设计

### 面试官问题
资产管理模块分库分表怎么做的？分片Key怎么选？跨分片查询怎么解决？

### 标准答案（STAR格式，口述版）

【S】多租户SaaS ERP，卡片表800万+行，折旧明细表3000万+行。月末批量计算行锁竞争+报表查询P99超3秒。

【T】单表控制在500万以内，批量计算和在线查询互不干扰。

【A】

**分片Key选择**：org_id（组织ID）。理由：ERP 100%查询带org_id，折旧按组织批量执行，天然是最高频查询条件。

**具体方案**：
| 维度 | 策略 | 理由 |
|------|------|------|
| 分库 | org_id哈希分4个库 | 组织间完全独立，事务简单 |
| 卡片表分表 | 库内按资产类别分3张 | 固定资产/无形资产/长期待摊 |
| 折旧明细分区 | 按年月range分区 | 增长最快，查询100%带期间 |

中间件：ShardingSphere-JDBC。全局ID：雪花算法。

**跨分片查询方案**：
1. 集团汇总报表→异步汇总到独立汇总库/ClickHouse，T+1延迟
2. 按资产编号全局搜索→路由表`t_asset_routing(asset_code, org_id)`或ES
3. DDL变更→ShardingSphere广播+flyway版本控制

【R】单表从800万降到200万，查询P99从3秒降到200ms。迁移采用双写+Canal+对账验证，业务零感知。

### 回答要点
1. 金句："分片Key跟着最高频查询条件走"
2. 先说为什么要分（排除了其他方案）
3. 分库和分表是两个维度
4. 跨分片有三种场景各有方案
5. 追问"迁移怎么不停机"→双写+Canal+灰度切流

---

## 题目7：分布式事务（本地消息表）

### 面试官问题
信用卡还款跨三个服务，怎么保证数据一致性？为什么不用Seata/TCC？

### 标准答案（口述版）

**方案：本地消息表+最终一致性**

**为什么不用Seata AT**：全局锁高并发下TPS腰斩 + TC是单点 + 需要undo_log表银行DBA不批
**为什么不用TCC**：开发成本3倍 + "更新账单"没有Cancel语义 + 短信发出去不可逆 + 还款允许秒级延迟

**实现**：
```
1. 账户服务（同一事务）：扣余额 + 写本地消息表(PENDING)
2. 投递器（每5秒扫描）：PENDING消息发到RabbitMQ → 改SENT
3. 信用卡服务消费：减欠款更新账单 + msg_id幂等
4. 通知服务消费：发短信 + 手机号+流水号去重
```

**可靠性**：重试5次超过标FAILED+告警。消费端Redis SETNX判重。T+1对账兜底。

**一句话**：还款是单向推进——扣款成功后续一定要完成，不需要全局回滚。

### 回答要点
1. 先说为什么不用Seata/TCC（各3个理由）
2. 核心：消息和扣款在同一个本地事务保证原子性
3. 重试+幂等+对账三重保障
4. 追问"什么场景用TCC"→跨行实时转账（双方必须原子性）

---

## 题目8：支付系统架构设计（场景题）

### 面试官问题
设计一个简化支付系统（创建订单→扣余额→通知商户）。怎么拆服务？怎么通信？怎么保证高可用？

### 标准答案（口述版）

**拆服务（4个）**：
- API网关：鉴权、限流、幂等拦截
- 订单服务：创建/查询订单、状态机
- 账户服务：余额管理、扣款
- 通知服务：商户回调、重试

每个服务独立数据库，互不直接访问对方的表。

**通信方式**：
- 主链路同步：订单服务→账户服务（Feign），用户在等结果
- 非主链路异步：扣款成功→MQ→通知服务，商户接口慢不拖垮支付

**高可用五维度**：
1. **无状态+多实例**：每服务3实例跨可用区，任意挂自动摘除
2. **数据库高可用**：主从半同步+自动切换，账户服务读写分离
3. **熔断限流**：网关令牌桶限流，账户服务Sentinel熔断（失败率50%触发）
4. **幂等设计**：请求Token+订单号唯一键+乐观锁version
5. **对账兜底**：T+1三方比对（订单表PAID vs 账户流水 vs 商户通知成功）

**总结金句**：主链路同步保证实时性，非主链路异步保证解耦，幂等保证不多扣，对账保证不少算，多实例+熔断保证不宕机。

### 回答要点
1. 先画架构图（4个服务+数据库）
2. 通信决策：同步vs异步的分界线在"用户是否在等"
3. 高可用要从多维度说（不能只说"加机器"）
4. 幂等是支付系统的命根（防资金损失）
5. 追问"账户服务扣款和订单状态不在一个库"→以资金侧为准，状态异步补偿
6. 追问"支持多少QPS"→单库3000-5000TPS，分库可线性扩展
