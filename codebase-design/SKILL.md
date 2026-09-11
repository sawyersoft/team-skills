---
name: codebase-design
description: 用于设计深模块（Deep Module）的通用术语表。当用户希望设计或改进模块接口、寻找可以进一步“做深”的模块、决定接缝（Seam）应该放在哪里、提高代码的可测试性或 AI 可导航性，或者其他 Skill 需要使用深模块相关术语时使用。
---

# 代码库设计

设计**深模块（Deep Module）**：保持interface简洁，把行为逻辑封装在application服务层或domain层，并以清晰的界面（Seam）进行封装，确保可通过该接口进行测试。在设计或重构代码的任何环节都应遵循这种语言和原则。其目标是为调用者提供便利，为维护者提供局部性，并为所有人提供可测试性。

## 术语表（Glossary）

必须严格使用以下术语，不要用其他词替代：**不要用 `component`、`service` 或 `API` 来替代这些术语，也不要用 `boundary` 表示这里的 Seam。** 保持术语的一致性至关重要。

### Module（模块）
**Module**：任何同时具有**接口（Interface）**和**实现（Implementation）**的代码单元。这个定义刻意保持与规模无关，可以是：一个function，class，Package，或 跨多个层次的代码切片（slice），*避免使用*：`unit`、`component`、`service`。这里的关键是：Module 并不等于某一种固定的代码结构，它只是“拥有接口和实现的东西”，因此，一个函数可以是 Module，一个完整的分层功能也可以是 Module。

### Interface（接口）
**Interface**：调用方为了正确使用一个 Module，**必须了解的全部信息**，它包括：类型签名（Type Signature），不变量（Invariants），调用顺序约束（Ordering Constraints），错误行为 / 错误模式（Error Modes），必需的配置（Required Configuration），性能特征（Performance Characteristics）。*避免使用*：`API`、`signature`原因是：`signature` 过于狭窄，只表示类型层面的函数/方法签名；而这里的 Interface 是一个更广泛的概念，包含调用方正确使用模块所必须知道的所有信息。

### Implementation（实现）
所谓的“实现（Implementation）”，说白了就是Module肚子里具体的代码。它和“适配器（Adapter）”不是一回事。比如，一个连接 Postgres 数据库的 Repo 模块，它的接口定义可能就一两行，但底下的实现代码却有一大堆（这就是“小适配器，大实现”）；相反，一个用来做测试的内存虚拟对象（Fake），它的接口可能为了模仿别人而写得很长，但肚子里其实没几行代码（这就是“大适配器，小实现”）。总之，当你在讨论代码之间的“对接缝隙（seam）”或“解耦切面”时，请用“适配器（Adapter）”这个词；而当你在讨论模块内部的具体干活逻辑时，用“实现（Implementation）”才更准确。

### Depth（深度）
“模块的‘深度’，本质上就是接口的‘以小博大’（模块在其interface上提供的杠杆程度leverage）。也就是说，调用方每学习一个Interface，到底能换来多少现成的功能。一个优秀的‘深层模块’，应该把复杂的底层逻辑全藏起来，只露出一两句极简的命令；相反，如果一个模块用起来琐碎繁长，说明它把内部的底细都漏在外面了，这就是‘浅层模块’。”

### Seam（接缝）
经典著作《修改代码的艺术》（Working Effectively with Legacy Code）作者 Michael Feathers 提出的标志性概念 —— Seam（接缝）。“接缝（Seam）”指的是代码里这样一个神奇的地方：你不需要改动这里的代码，就能直接改变它的运行行为。说白了，它就是模块接口所处的那条“交界线”，也就是**Module 的 Interface 所处的位置。因此，在架构设计时Interface 代表的Seam 应该放在哪里本身就是独立的设计决策，而“它背后的具体实现怎么写代码”则是另一回事，两者不能混为一谈。
*避免使用*：`boundary`，因为 `boundary` 在 DDD 中已经经常用于 **Bounded Context（限界上下文）**，容易产生概念冲突，容易鸡同鸭讲。

### Adapter（适配器）
所谓的“适配器（Adapter）”，就是卡在“接缝（Seam）”上刚好能实现某个 Interface 的具体对象。Adapter描述的是它的“角色”（看它填补了哪个空缺），而不是它的“底细”（不关心它肚子里装了什么代码）。也就是说：Adapter 关注的是“它承担了哪个接口的实现角色”，而不是“它内部采用了什么实现方式”。

### Leverage（杠杆）
所谓的“杠杆率（Leverage）”，其实就是“深层模块”带给调用者的红利。调用方只要学习一个Interface，就能白嫖到成倍的功能。最爽的是，你只需要咬牙写好“这一处”复杂的底层实现，就能在 N 个调用它的业务场景、以及 M 个测试用例里复用它。因此： **一个好的深模块，用较小的 Interface 换取较大的实现能力。**

### Locality（局部性）
维护者从深度中获益。变更、缺陷、知识和验证都集中在一个地方，而不是分散在各个调用者之间。一次修复，处处有效。

# 深模块 vs 浅模块

## 深模块（Deep Module）

**小 Interface + 大量 Implementation**

```text
┌─────────────────────┐
│      小型接口       │  ← 方法少，参数简单
├─────────────────────┤
│                     │
│      深层实现       │  ← 复杂逻辑被隐藏
│                     │
└─────────────────────┘
```

核心思想：**把复杂度藏在模块内部，让调用方只面对一个简单的 Interface。**

## 浅模块（Shallow Module）

**大型 Interface + 少量 Implementation**

```text
┌─────────────────────────────────┐
│          大型接口               │  ← 方法多，参数复杂
├─────────────────────────────────┤
│          薄层实现               │  ← 主要只是透传
└─────────────────────────────────┘
```

这种设计应该尽量避免。

典型表现是：每一层都只是简单地把参数往下一层传递，却增加了新的 Interface、类型和调用关系。这种情况下，新增的模块没有真正隐藏复杂度，因此属于浅模块。
设计一个 Interface 时，应主动问：
* 能不能减少方法数量？
* 能不能简化参数？
* 能不能把更多复杂度隐藏在模块内部？

核心目标不是：“Interface 看起来足够完整”，而是：**让调用方用尽可能小的认知成本获得尽可能大的能力。**

# 核心原则（Principles）

## 1. Depth 是 Interface 的属性，而不是 Implementation 的属性
一个 Deep Module 内部完全可以由许多小型模块、可 Mock 的组件、可替换的实现组合而成；只是这些内部结构不应该暴露给 Module 的 Interface。一个 Module 可以同时存在：
- **Internal Seam（内部接缝）**：仅供 Implementation 内部使用，也可以供自身测试使用。
- **External Seam（外部接缝）**：即 Module 对外暴露的 Interface 所处的位置。

因此：内部可以很复杂、很模块化；对外仍然应该保持一个简单的 Interface。

## 2. 删除测试（The Deletion Test）
可以做一个非常简单的思维实验：**假设把这个 Module 整个删除。** 如果复杂度随 Module 一起消失，那么这个 Module 很可能只是一个**透传层（Pass-through）**，如果复杂度重新出现在 N 个调用方中，那么这个 Module 就真正承担了隐藏复杂度的价值。

## 3. Interface 就是测试面（The Interface Is the Test Surface）
调用方和测试代码应该穿过**同一个 Seam**访问 Module。如果为了测试一个 Module，不得不绕过它的 Interface，直接测试它内部的实现，那么这个 Module 很可能设计得不对，理想状态是：调用方怎么使用，测试就怎么使用。

## 4. 一个 Adapter 只是“假设存在的接缝”，两个 Adapter 才说明它是真实存在的接缝
如果一个插槽（接口）底下只接了一个适配器，那这个所谓的“接缝”纯粹是你凭空想象出来的伪需求；只有当底下至少接了两个不同的适配器时，这条接缝才算真正立住了。听哥一句劝，只要某个业务还没有出现真正的“多变性”，就千万别为了解耦而瞎留插槽（接口）。

### “一个适配器 = 伪需求” (One adapter means a hypothetical seam)：
- 很多程序员在写代码时，喜欢搞“防御性设计”。比如明明系统只用到了 Mysql 数据库，他非要定义一个 IUserRepo 接口，然后写一个 MysqlUserRepo 去实现它。他心里想的是：“万一以后要换 Oracle 呢？”
- 大白话：我们要直接戳破这个幻想。如果你的代码里，某个接口（Seam）永远只有唯一一个真实实现（One adapter），那这个接口就是纯纯的摆设（Hypothetical）。你只是在为你脑补出来的“伪需求”买单。

### “两个适配器 = 真需求” (Two adapters means a real one)
什么时候这个接口才真正有价值？至少有两种情况（Two adapters）：
- 线上 vs 测试： 线上跑的是真实的 PostgresAdapter，跑单测时换成了内存里的 In-MemoryFakeAdapter。
- 业务多态：比如支付场景，底下同时接了 AlipayAdapter 和 WechatAdapter。

大白话： 只有当你真正把两个不同的东西插进同一个槽里时，你才算没有白挖这个槽。

### 终极戒律：没有变化，就别加戏 (Don't introduce a seam...)
- 增加一个接口/接缝是有代价的。它会增加系统的认知成本、让代码跳转变得更麻烦（Ctrl+左键点进去只能看到接口，还要再点一步看具体实现）。
- 架构师的克制：优秀的架构师应该遵循 YAGNI 原则（You Aren't Gonna Need It —— 你根本不需要它）。在变化真正发生之前，保持代码的简单和直接；当第二种情况真的出现时，再去重构出接缝。

# 面向可测试性进行设计

优秀的 Interface 应该让测试变得自然。

## 1. 接收依赖，不创建依赖

```java
// 易于测试
public void processOrder(order, paymentGateway) {}

// 难以测试
public void function processOrder(order) {
  StripeGateway gateway = new StripeGateway();
}
```

第一种方式将依赖从外部传入，因此测试时可以轻松替换 `paymentGateway`。
第二种方式把具体依赖写死在 Implementation 中，测试时就很难控制其行为。

## 2. 返回结果，而不是直接产生副作用

```java
// 易于测试
public Discount calculateDiscount(cart){}

// 难以测试
public void applyDiscount(cart): void {
  cart.total -= discount;
}
```

第一种方式返回计算结果，测试可以直接验证结果。
第二种方式直接修改外部状态，因此测试需要额外增加处理这个副作用。

## 3. 保持 Interface 足够小：更少的方法 = 更少的测试。更少的参数 = 更简单的测试准备。

# 概念之间的关系
- 一个 **Module** 恰好拥有一个 **Interface**——即它向调用方和测试代码暴露的统一使用面。
- **Depth** 是 Module 的属性，通过它的 Interface 来衡量。
- **Seam** 是 Module 的 Interface 所处的位置。
- **Adapter** 位于 Seam 上，用于满足对应的 Interface。
- **Depth** 为调用方带来 **Leverage**，为维护者带来 **Locality**。

# 被否决的观点（Rejected Framings）

## 1. 不使用“实现代码行数 / 接口代码行数”来衡量 Depth
单纯按照代码行数计算，会鼓励人为增加 Implementation 的代码量，从而制造虚假的“深度”，我们禁止采用这种定义，而是采用深度杠杆效应来衡量深度。也就是关注：**Interface 学习成本与它能够提供的能力之间的关系。** 而不是简单统计代码行数。

## 2. 不把 Interface 理解为 开发语言 的 `interface` 关键字
这里的 **Interface** 并不特指：开发语言 的 `interface` 关键字,也不只是一个 Class 的 public methods，这种理解太狭窄，而是 **调用方为了正确使用这个 Module，必须了解的所有事实信息。**

## 3. 不使用 Boundary（边界）
不要使用 `boundary` 来描述这里的 Seam，因为`boundary` 在 DDD 中已经与 **Bounded Context（限界上下文）** 紧密相关。为了避免与 DDD 概念混淆，这套术语统一使用：**Seam（接缝）** 或 **Interface（接口）** 而不是 `Boundary`。

# 进一步深入

## 根据依赖关系对模块进行深化
如果想把现有的某个“代码集群（或模块组）”做深、做厚，参考 `DEEPENING.md` 里的指南。它的核心招式包括：给依赖项分类、严守接缝纪律，以及推行“直接替换，绝不加层”的无痛测试法。

## 探索不同的 Interface 设计方案
在构思接口设计时，为了探索更多可能，可以参考 `DESIGN-IT-TWICE.md` 的做法：直接派生出几个并行的 AI 子智能体（Sub-agents），让他们用几种截然不同的脑洞去设计这个接口，最后拉出来PK——从“深度（是否以小博大）”、“高内聚性（代码是否扎堆）”以及“接缝位置（插槽留得准不准）”这三个维度进行全方位比对。
