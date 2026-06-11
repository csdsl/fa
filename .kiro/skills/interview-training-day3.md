# 面试训练记录 - Day3（正确答案+回答指南）

---

## 题目1：信用卡状态机设计

### 面试官问题
信用卡从申请到注销的状态机怎么设计？代码层面怎么实现？

### 标准答案（口述版）

**8个核心状态**：APPLYING → REVIEWING → APPROVED/REJECTED → ACTIVE → FROZEN/OVERDUE → CANCELLED

**代码实现：枚举+策略模式**（不用Spring StateMachine，太重了）

```java
public enum CardStatus {
    APPLYING(Set.of(REVIEWING)),
    REVIEWING(Set.of(APPROVED, REJECTED)),
    APPROVED(Set.of(ACTIVE)),
    ACTIVE(Set.of(FROZEN, OVERDUE, CANCELLED)),
    FROZEN(Set.of(ACTIVE, CANCELLED)),
    OVERDUE(Set.of(ACTIVE, FROZEN, CANCELLED));
    
    private final Set<CardStatus> allowedTransitions;
    
    public boolean canTransitTo(CardStatus target) {
        return allowedTransitions.contains(target);
    }
}
```

**并发安全**：状态流转用SELECT FOR UPDATE行锁，保证同一张卡的状态不会被并发修改覆盖。

**审计追踪**：每次流转记录审计日志（from_status、to_status、operator、timestamp）+ 发布领域事件（CardStatusChangedEvent），下游服务监听处理。

### 回答要点
1. 画出状态流转图（8个状态）
2. 代码实现：枚举内定义allowedTransitions
3. 并发控制：SELECT FOR UPDATE
4. 审计+领域事件
5. 为什么不用Spring StateMachine（太重，学习成本高，枚举够用）

---

## 题目2：大数据量报表导出方案

### 面试官问题
50万条数据导出Excel，怎么设计？

### 标准答案（口述版）

**整体架构**：异步导出，不阻塞用户请求。

1. **接口层**：用户点击导出，接口立即返回taskId，独立线程池执行（和Tomcat线程池隔离）
2. **查询层**：游标分页 `WHERE id > lastId ORDER BY id LIMIT 50000`，每批5万条。不用offset避免深分页
3. **写入层**：用EasyExcel流式写入，5万条flush一次，不全量加载到内存（会OOM）。超过Excel单sheet限制（104万行）自动分sheet
4. **并行加速**：10个线程并行查询不同分片，结果写入不同临时文件，最后合并ZIP打包
5. **存储与下载**：写完上传MinIO/OSS，用户通过URL下载
6. **进度反馈**：任务表记录progress（已处理行数/总行数），前端轮询展示进度条

### 回答要点
1. 异步！不能同步阻塞
2. 游标分页不用offset
3. EasyExcel流式写入防OOM
4. 多线程并行+最终合并
5. 进度反馈机制

---

## 题目3：MySQL深分页优化

### 面试官问题
`LIMIT 1000000, 10` 为什么慢？怎么优化？

### 标准答案（口述版）

**慢的本质**：MySQL要扫描offset+size行（100万+10行），然后丢弃前100万行只取10行。如果没有覆盖索引还要大量回表IO。

**方案1：游标分页（推荐）**
```sql
-- 记住上一页最后一条的ID
WHERE id > 1000000 ORDER BY id LIMIT 10
```
利用主键索引直接定位，O(logN)。限制：不能跳页。

**方案2：延迟关联（支持跳页）**
```sql
SELECT t.* FROM table t 
INNER JOIN (
    SELECT id FROM table ORDER BY id LIMIT 1000000, 10
) tmp ON t.id = tmp.id
```
子查询只查ID走覆盖索引（不回表），外层只回表10条。

**方案3：业务限制**
- 限制最大翻页深度（如最多翻到第100页）
- 引导用户加筛选条件缩小范围

**方案4：复杂查询同步ES，用search_after**

### 回答要点
1. 先解释为什么慢（扫描+丢弃+回表）
2. 游标分页：最优但不能跳页
3. 延迟关联：支持跳页的折中方案
4. 业务限制：限深度+引导筛选
5. 给出适用场景对比

---

## 题目4：JVM调优实战

### 面试官问题
你做过JVM调优吗？具体什么场景？怎么调的？

### 标准答案（口述版，STAR格式）

【S】金蝶资产折旧计算，50万张卡片批量计算时，发现Young GC频繁（每秒1-2次）+ 偶发Full GC（每小时1-2次，每次停顿2-3秒）。

【T】优化GC表现，消除Full GC，减少Young GC对业务的影响。

【A】
- 收集器：G1GC（JDK17，8G堆）
- 关键参数：`-Xms8g -Xmx8g -XX:MaxGCPauseMillis=100 -XX:G1HeapRegionSize=16m`
- 问题定位：jstat发现大对象直接进老年代（BigDecimal数组超过Region的50%触发Humongous分配）
- 调优方案：
  1. 调大年轻代比例到40-60%，给年轻代更多空间
  2. 代码层面：5万一批改为5000一小批处理后释放，避免Humongous对象
  3. BigDecimal常量复用（0、1、100等高频值不反复new）
  4. 对象复用：折旧计算结果DTO用对象池

【R】Full GC基本消除（每天0-1次），Young GC频率降低60%，整体计算提速。

### 回答要点
1. 必须有具体场景（不能空谈理论）
2. 问题现象→定位工具→根因→方案→结果
3. 代码层优化和JVM参数调优结合
4. G1的Humongous问题是加分知识点

---

## 题目5：KWork Prompt设计与调优

### 面试官问题
KWork的Prompt怎么设计的？遇到过什么调优问题？

### 标准答案（口述版）

**System Prompt结构**：
- 角色定位：你是金蝶云星空结账助手
- 任务边界：只能执行结账相关操作
- 硬性约束：不确定时必须询问用户，禁止跳步
- 输出格式：固定JSON格式，方便程序解析

**调优实战（线上踩坑）**：
两个相似接口——"计提折旧"和"折旧查询"，LLM经常调错。

调优迭代3轮：
1. 只改Prompt效果不稳（告诉LLM"注意区分"没用）
2. 改工具description加【模块标识】前缀，稍有改善但不够
3. 最终方案：修改Tool入参结构让两个工具签名差异化 + 完善description写明"什么时候用这个工具" + 设置默认行为（无明确计算意图时默认查询）

结果：准确率从75%提升到98%。

**经验总结**：LLM对Tool的选择主要依赖入参schema和description，Prompt里的约束效果弱于工具定义本身的清晰度。

### 回答要点
1. Prompt结构4要素：角色+边界+约束+格式
2. 用真实调优案例展示经验（不是空谈）
3. 调优迭代过程（3轮）
4. 金句："工具定义的清晰度比Prompt约束更有效"
5. 量化：75%→98%

---

## 题目6：LLM生产化（稳定性/限流/降级/成本）

### 面试官问题
LLM接入生产系统，稳定性怎么保障？成本怎么控制？

### 标准答案（口述版）

**稳定性保障**：
- 超时重试：30s超时，指数退避重试2次
- 模型路由：优先级列表（qwen-plus → qwen-turbo → glm-4-flash），滑动窗口统计错误率/延迟，触发自动切换
- 降级方案：所有模型都不可用时，返回预设话术"系统繁忙请稍后"

**限流三维度**：
- 全局QPS上限（保护账户额度）
- 单用户QPS限制（防滥用）
- 单租户Token月度配额

**成本管控**：
- Token用量监控：每次调用记录input/output token数
- 月度预算配额：超额自动降级到小模型
- Prompt精简：去掉冗余描述，压缩System Prompt长度
- 缓存复用：相同请求（语义缓存）直接返回历史结果

**可观测性**：每次LLM调用记录模型/耗时/token数/是否命中缓存，Grafana看板+告警。

### 回答要点
1. 稳定性：超时重试+模型路由+降级
2. 限流：三维度（全局/用户/租户）
3. 成本：监控+配额+精简+缓存
4. 可观测性：全链路指标

---

## 题目7：CompletableFuture核心API

### 面试官问题
CompletableFuture的thenApply和thenCompose区别？allOf和anyOf呢？生产中怎么用？

### 标准答案（口述版）

**thenApply vs thenCompose**：
- thenApply = map：同步转换结果，`T -> U`
- thenCompose = flatMap：异步扁平化，`T -> CompletableFuture<U>`，避免嵌套

**allOf vs anyOf**：
- allOf：等全部完成。场景：折旧计算等所有分片完成后汇总
- anyOf：取最快的。场景：多模型竞速，谁先返回用谁的结果

**生产必须注意**：
- 自定义线程池（不用commonPool，否则和Stream parallelStream抢线程）
- 超时控制：`.get(30, TimeUnit.MINUTES)` 或 `.orTimeout(30, MINUTES)`（JDK9+）
- 异常隔离：`.exceptionally(ex -> defaultValue)` 每个任务独立处理异常
- 线程池命名：方便jstack排查线程归属

### 回答要点
1. thenApply=map，thenCompose=flatMap
2. allOf=等全部，anyOf=取最快
3. 生产三要素：自定义线程池+超时+异常隔离
4. 结合折旧计算场景举例

---

## 题目8：DDD在ERP中的实践

### 面试官问题
你们ERP系统用了DDD吗？怎么建模的？

### 标准答案（口述版）

**四层架构**：interfaces（接口层）→ application（应用层）→ domain（领域层）→ infrastructure（基础设施层）

**资产模块建模**：
- 聚合根：AssetCard（资产卡片）
- 实体：DepreciationPlan（折旧计划）、AssetChange（资产变动）
- 值对象：Money（金额）、UsefulLife（使用年限）

**职责划分**：
- 应用层：编排用例（事务管理+领域事件发布），不包含业务规则
- 领域层：放核心业务规则（折旧计算逻辑在领域服务DepreciationService中）

**Trade-off**：
- 只在核心域（资产管理、总账）用DDD
- 支撑域（通知、日志）用传统三层架构
- 不是所有模块都值得用DDD，复杂度不够的场景用DDD反而过度设计

**话术（面试用）**：不说"进去时已成型"，说"在此架构基础上深度开发并做了优化，比如重构了折旧计算的领域模型"。

### 回答要点
1. 四层架构说清楚
2. 建模要素：聚合根+实体+值对象
3. 领域层放业务规则
4. Trade-off：核心域DDD，支撑域三层
5. 不要过度吹嘘，展示理解深度

---

## 题目9：分布式链路追踪排查慢请求

### 面试官问题
线上一个接口突然变慢，你怎么排查？

### 标准答案（口述版，必须讲完整故事）

【S】数字银行App还款接口P99从200ms飙升到4秒，影响用户体验。

【T】定位慢请求根因并修复。

【A】
1. **发现问题**：Grafana监控告警，还款接口P99超阈值
2. **定位链路**：网关记录慢请求的traceId → Zipkin/Sleuth查分布式链路 → 发现account-service那一跳耗时4秒
3. **深入分析**：account-service内部耗时集中在数据库操作 → 查慢SQL日志 → 发现是行锁等待（SELECT FOR UPDATE等待超时）
4. **根因确认**：月初定时任务在批量更新账户余额，和在线还款请求争抢同一行锁

**解决方案**：
- 定时任务错峰到凌晨执行
- 同账户操作串行化（Redis分布式锁排队，减少数据库行锁竞争）
- 还款操作增加锁等待超时控制（innodb_lock_wait_timeout缩短到5秒，快速失败重试）

**采样策略**：基础10%采样 + 慢请求强制100%采样（响应时间>1s的一定会被记录）。

【R】P99恢复到200ms，行锁等待告警清零。

### 回答要点
1. 必须讲完整故事：发现→链路定位→深入分析→根因→解决
2. 具体技术栈：Sleuth + Zipkin
3. 根因要合理（行锁竞争是数据库常见问题）
4. 采样策略是加分项
5. 不能只说"用链路追踪定位的"就完了

---

## 题目10：RAG系统设计

### 面试官问题
你做过RAG系统吗？文档怎么切分？检索怎么做？

### 标准答案（口述版）

**文档切分策略**：递归结构化切分
- 按文档结构分层：标题→段落→表格，保持语义完整
- 每个chunk 500-800字符，overlap 100字符
- 保留元数据：来源文档、章节标题、页码

**检索方案：混合检索**
- 向量检索：千问text-embedding-v3，1024维，Milvus存储。语义理解能力强
- BM25关键词检索：精确匹配专业术语（如"固定资产减值准备"）
- 两路结果融合：Reciprocal Rank Fusion（RRF）

**精排**：粗排Top20 → 交叉编码器Rerank → 精排Top5

**Prompt组装**：
```
以下是参考资料：
{top5_chunks}

请基于以上资料回答用户问题。如果资料中没有相关信息，请明确说明。
用户问题：{question}
```

**引用溯源**：回答附上来源文档名和章节。

**效果评估**：200道标注QA对，评估命中率和准确率。

### 回答要点
1. 切分策略：结构化+overlap+元数据
2. 混合检索：向量+BM25+RRF融合
3. Rerank精排
4. Prompt模板（含"不知道就说不知道"约束）
5. 效果评估方法

---

## 题目11：RAG多租户数据隔离

### 面试官问题
RAG系统怎么做多租户数据隔离？不同租户的知识库不能串？

### 标准答案（口述版）

**方案选型**：
- 中小租户：逻辑隔离（同一个Milvus Collection + 元数据过滤tenant_id）
- 大客户：独立Collection

**安全三层防护**：
1. 网关层提取tenantId（从JWT中解析）
2. 拦截器校验tenantId非空
3. 检索结果二次过滤（即使向量搜索返回了其他租户的数据，应用层也会过滤掉）

**性能优化**：tenant_id字段建标量索引（Trie类型），过滤性能损耗<5%。

**检索时动态过滤**：
```python
search_params = {
    "filter": f"tenant_id == '{current_tenant}'"
}
```

### 回答要点
1. 分级策略：中小逻辑隔离 vs 大客户物理隔离
2. 三层安全防护
3. 性能：标量索引优化过滤
4. 检索代码示例

---

## 题目12：设计模式（多种折旧算法扩展）

### 面试官问题
折旧算法有好几种（直线法、加速折旧法等），怎么设计让它可扩展？

### 标准答案（口述版）

**策略模式 + 工厂模式**：

```java
// 策略接口
public interface DepreciationStrategy {
    String getMethod(); // "STRAIGHT_LINE", "DOUBLE_DECLINING"
    BigDecimal calculate(AssetCard card, int period);
}

// 实现类
@Component
public class StraightLineStrategy implements DepreciationStrategy {
    public String getMethod() { return "STRAIGHT_LINE"; }
    public BigDecimal calculate(AssetCard card, int period) {
        return (card.getOriginalValue() - card.getResidualValue()) 
               / card.getUsefulLife();
    }
}

// 工厂：Spring自动注入所有策略
@Component
public class DepreciationFactory {
    private final Map<String, DepreciationStrategy> strategies;
    
    public DepreciationFactory(List<DepreciationStrategy> list) {
        this.strategies = list.stream()
            .collect(Collectors.toMap(DepreciationStrategy::getMethod, s -> s));
    }
    
    public DepreciationStrategy get(String method) {
        return strategies.get(method);
    }
}
```

**客户自定义公式**：表达式引擎（Aviator/SpEL）动态执行配置的公式字符串，不需要发版。

**开闭原则**：新增折旧算法只需加一个实现类+@Component，零修改已有代码。

### 回答要点
1. 策略模式：接口+多实现
2. 工厂模式：Spring注入构建Map路由
3. 自定义公式：表达式引擎
4. 金句："新增算法零修改已有代码"
5. 代码要写出来，不能空说

---

## 题目13：敏捷管理（紧急需求插入）

### 面试官问题
迭代中间来了紧急需求，你怎么处理？

### 标准答案（口述版）

**第一步：评估**
- Challenge"是否真紧急"——明确影响范围、deadline、不做的后果
- 确认最小可交付范围（MVP），不是紧急需求就要做全做大

**第二步：排期调整**
- 从低到高挤：技术债务 → 非核心功能 → 核心功能
- 被延后的需求不是取消，是移到下个迭代

**第三步：透明沟通**
- 站会同步变更，团队知道为什么改
- 被延后需求的干系人需要主动通知和解释

**第四步：预防机制**
- 每个迭代预留15-20% buffer应对紧急插入
- 回顾会分析为什么没提前识别这个需求

### 回答要点
1. 先质疑真假紧急（展示独立判断能力）
2. 排期有优先级策略
3. 透明沟通
4. 预留buffer是经验之谈
5. 复盘改进
