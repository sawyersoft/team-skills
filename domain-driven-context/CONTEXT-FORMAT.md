# CONTEXT.md Format

## 文档结构

```md
# {上下文名称}
{用一到两句话描述该领域上下文是什么，以及为什么需要存在这个上下文。}

## 领域语言

**Order（订单）**：
{用一到两句话描述该术语所代表的领域概念}
_避免使用_：Purchase、Transaction

**Invoice（发票）**：
{客户完成交付后，向其提出付款请求的单据。}
_避免使用_：Bill、Payment Request

**Customer（客户）**：
{下单的个人或组织。}
_避免使用_：Client、Buyer、Account
```

## 规则

### **明确且有立场（Be opinionated）**
当多个词实际上表示同一个领域概念时，必须选择其中一个作为项目的规范术语（Canonical Term），并将其他容易产生歧义或不应使用的词列在 `_Avoid_` 下。
这里不是说 Client、Buyer 在英语中“错误”，而是：在当前领域上下文中，项目明确规定统一使用 Customer 来表示这个概念。
这样可以避免不同开发人员分别使用 Customer、Client、Buyer，最终导致代码、接口和业务讨论中的概念漂移。

### **定义必须简洁（Keep definitions tight）**
术语定义应使用简洁的语言，说明：“它是什么（What it IS）”，“它做什么（What it DOES）”，“它在上下文哪里（Where it is）”，“它和谁有关（Who is it related to?）”

### **只记录当前领域上下文特有的术语**
只将当前项目/领域上下文中特有的领域概念加入 CONTEXT.md，一般性的编程概念，即使项目大量使用，也不应该记录进去。
例如：Timeout（超时），Error Type（错误类型），Utility Pattern（工具模式），Retry（重试），Cache（缓存）这些通常属于通用的软件工程概念，而不是当前领域上下文中特有的业务概念。
在添加一个术语之前，先问：“这个概念是当前领域上下文所特有的吗，还是一个通用的软件开发概念？” 只有前者才应该进入 CONTEXT.md。

### **当术语形成自然的概念群时，使用子标题进行分组（Group terms under subheadings）**
如果术语逐渐形成明显的领域概念簇（Concept Cluster），可以使用子标题进行分组，例如：Order 和 Order Item 等，如果所有术语都属于同一个紧密相关的领域，则不需要强行分组，使用**扁平的术语列表（Flat List）**即可。

## 单上下文与多上下文仓库（Single vs multi-context repos）

**单上下文:** 大多数代码仓库属于单上下文，这种情况下，只需要在仓库根目录维护一个 `CONTEXT.md` 。这里的 Context（上下文） 在 DDD 语境下不是简单的“代码上下文”，而更接近于：一组拥有明确边界、使用统一领域语言，并对领域概念具有一致理解的业务模型范围。在更严格的 DDD 语境中，它通常对应 Bounded Context（限界上下文）。

**多上下文仓库:** 
如果一个仓库中包含多个相互独立的领域上下文，则在仓库根目录创建：`CONTEXT-MAP.md`，它负责描述：仓库中有哪些上下文；每个上下文的 CONTEXT.md 在哪里；上下文之间是什么关系；例如：

```md
# Context Map

## 上下文

- [Ordering](./src/ordering/CONTEXT.md)：接收并跟踪客户订单
- [Billing](./src/billing/CONTEXT.md)：生成发票并处理付款
- [Fulfillment](./src/fulfillment/CONTEXT.md)：管理仓库拣货和商品发运

## 上下文之间的关系

- **Ordering → Fulfillment**：Ordering 发布 `OrderPlaced` 事件；Fulfillment 消费该事件并开始拣货
- **Fulfillment → Billing**：Fulfillment 发布 `ShipmentDispatched` 事件；Billing 消费该事件并生成发票
- **Ordering ↔ Billing**：共享 `CustomerId` 和 `Money` 类型
```

这里的 Context Map 是 DDD 中用于描述多个 Bounded Context（限界上下文） 之间关系的模型。它关注的不是代码目录本身，而是：不同领域模型之间如何划分边界，以及它们如何发生协作、集成和共享。

## 本 skill 按以下规则推断适用结构：
- 若存在 CONTEXT-MAP.md，则读取它以定位各上下文
- 若仅存在根目录 CONTEXT.md，则为单上下文
- 若两者皆不存在，则在首个术语被确认时惰性创建根目录 CONTEXT.md
- 当存在多个上下文时，推断当前话题所属上下文，若无法明确判断，则主动询问
