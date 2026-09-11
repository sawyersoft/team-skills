# HTML 报告格式规范 (HTML Report Format)

架构评审结果须渲染为一个全包含、自支撑的独立 HTML 文件，并输出至操作系统的临时目录中。Tailwind 和 Mermaid 均通过 CDN 引入。Mermaid 负责稳定输出图状拓扑（如调用链、依赖图）；手写 Div 与内联 SVG 则负责处理更具表现力的视觉元素（如体量图、剖面图）。两者须混用 —— 切勿所有图表都依赖 Mermaid，否则会导致报告风格千篇一律、缺乏特色。

## 基础脚手架 (Scaffold)

<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <title>架构评审报告 — {{代码仓库名称}}</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script type="module">
      import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs";
      mermaid.initialize({ startOnLoad: true, theme: "neutral", securityLevel: "loose" });
    </script>
    <style>
      /* 针对 Tailwind 无法完美覆盖的细节进行微调的自定义样式层：
         如虚线形式的接缝、手绘感的箭头等 */
      .seam { stroke-dasharray: 4 4; }
      .leak { stroke: #dc2626; }
      .deep { background: linear-gradient(135deg, #0f172a, #1e293b); }
    </style>
  </head>
  <body class="bg-stone-50 text-slate-900 font-sans">
    <main class="max-w-5xl mx-auto px-6 py-12 space-y-12">
      <header>...</header>
      <section id="candidates" class="space-y-10">...</section>
      <section id="top-recommendation">...</section>
    </main>
  </body>
</html>

## 页头规范 (Header)

包含仓库名称、日期以及一个紧凑的图例：实线方框 = 模块（module），虚线 = 接缝（seam），红色箭头 = 穿透泄漏（leakage），深色粗线方框 = 深层模块（deep module）。**严禁编写任何前言或介绍性段落** —— 直接切入候选方案列表。

## 方案卡片规范 (Candidate card)

图表是卡片的核心。文字描述须保持精炼、直白，并自然地使用架构术语表（来自 `codebase-design` skill），无需做任何铺垫。

每个候选方案对应一个 `<article>` 标签：:

- **标题 (Title)** — 简短，直接命名重构方向（例如：“收拢订单接收流水线”）。
- **标签行 (Badge row)** — 推荐指数(`Strong` = emerald, `Worth exploring` = amber, `Speculative` = slate)，外加一个依赖项分类标签（in-process、local-substitutable、ports & adapters、mock）。
- **涉及文件 (Files)** — 等宽字体列表，使用`font-mono text-sm`。
- **重构前/后对比图 (Before / After diagram)** — 整个卡片的视觉中心。采用双列设计，左右并排呈现。具体版式见下文。
- **当前痛点 (Problem) ** — 一句话点明哪里在引发摩擦。
- **解决方案 (Solution)** — 一句话说明将发生什么改变。
- **预期收益 (Wins) ** — 采用列表符号，每条收益不得超过 12 个汉字（原文为不超过 6 个英文单词）。例如：“测试仅对齐单一接口”、“计价逻辑停止向外泄漏”、“删除了 4 个浅层包装”。
- 

- **ADR 冲突高亮 (ADR callout)** （若适用）—— 在琥珀黄底色的提示框内写明一行提示。

严禁编写成段的解释性文字。 如果一个图表需要配合一个段落才能让人看懂，说明图表画得不合格，请重新画图。

## 图表版式模式 (Diagram patterns)

根据候选方案的具体特征选择最合适的图表版式。多种版式须交替使用，保持报告的多样性。

### Mermaid 拓扑图（最适用于依赖关系 / 调用链路）

当需要表达“X 调用 Y，Y 又调用 Z，导致链路极其混乱”时，使用 Mermaid 的 `flowchart` 或 `graph`。将图表包裹在带 Tailwind 样式的卡片中，避免产生突兀的割裂感。利用 classDef 将发生穿透泄漏的边（Edges）标记为红色，将深层模块标记为深色。时序图（Sequence diagrams）非常适用于展现“重构前：6 次网络往返；重构后：1 次”。

```html
<div class="rounded-lg border border-slate-200 bg-white p-4">
  <pre class="mermaid">
    flowchart LR
      A[OrderHandler] --> B[OrderValidator]
      B --> C[OrderRepo]
      C -.leak.-> D[PricingClient]
      classDef leak stroke:#dc2626,stroke-width:2px;
      class C,D leak
  </pre>
</div>
```

### 手写方框箭头图（当 Mermaid 的自动布局不听使唤时）

将模块绘制为带有边框和标签的 `<div>`。箭头则使用内联 SVG 的 `<line>` 或 `<path>` 元素，在声明了 relative 的父容器下进行 absolute 绝对定位。当你希望“重构后”的图表呈现为一个带有粗边框、内部细节淡化的巨型深层模块时，应优先采用此方案——因为 Mermaid 无法渲染出这种视觉分量。

### 横断面图（最适用于层层嵌套的浅层代码）

将水平条带堆叠起来（h-12 border-l-4），用以展示一个调用请求必须穿过的所有层级。重构前：6 层薄如蝉翼、毫无实质行为的浅层代码；重构后：一个厚实的条带，打上合并后核心职责的标签。

### 体量对比图（最适用于“接口与实现一样宽”的场景）

每个模块由两个矩形组成——一个代表接口的表面积，一个代表内部实现的体量。重构前：接口矩形的高度几乎与实现矩形持平（属于浅层模块）；重构后：接口矩形极矮，实现矩形极高（属于深层模块）。

### 调用图收拢

重构前：以嵌套方框形式呈现的函数调用树；重构后：整个调用树坍缩并收纳进一个大方框内，原本的内部调用在大方框内以淡化、半透明的形式呈现。

## 样式设计指南 (Style guidance)

- 整体风格应偏向“社论社刊”的高级质感，而非企业仪表盘。留白要慷慨。标题可选用衬线体（`font-serif` 与石板灰/原石色 slate/stone 搭配效果极佳）。
- 用色须克制：仅允许使用一种主色调（翡翠绿 emerald 或靛蓝 indigo），外加代表泄漏的红色、代表警告的琥珀黄。
- 图表高度控制在约 320px 左右，以确保重构前/后的对比图可以舒适地左右并排呈现，无需用户横向滚动屏幕。
- 图表内部的模块标签使用 `text-xs uppercase tracking-wider` 样式——使其读起来像标准的系统图纸，而非 UI 界面。
- 整个报告中唯一的脚本只能是 Tailwind CDN 和 Mermaid ESM 模块导入。除此之外，报告应当是完全静态的——不包含任何应用代码，除了 Mermaid 自身的图表渲染外，不提供任何多余的交互。

## 首选推荐章节规范 (Top recommendation section)

使用一个尺寸更大的卡片。仅包含候选方案名称、一句话的推荐理由、以及指向该方案卡片的锚点链接。到此为止，绝不多写。

## 语言风格与语调 (Tone)

使用直白、凝练的语言——但使用的架构名词和动词必须严格源自Skill `/codebase-design` 工具。精简不意味着可以随意发挥。

**必须精准使用以下词汇:** 模块（module）、接口（interface）、内部实现（implementation）、深度（depth）、深（deep）、浅（shallow）、接缝/插槽（seam）、适配器（adapter）、杠杆率（leverage）、局部性（locality）。

**严禁使用替代词:** 组件（component）、服务（service）、单元（unit for module) · API、方法签名（signature for interface）, 边界（boundary for seam)，层（layer）、包装器（wrapper 用于在指代模块时使用 for module, when you mean module]。

**符合本规范风格的示范句式：**

- “订单接收模块过于浅显（shallow）——接口复杂度几乎等同于其内部实现。”
- “计价逻辑穿透并泄漏（leak）到了接缝之外。”
- “将其做深（Deepen）：收拢为单一接口，建立唯一的测试表面。”
- “两种适配器证明了该接缝的合理性：生产环境下的 HTTP 适配器与测试环境下的内存适配器。”

**预期收益（Wins）的列表符号中，必须用术语来为收益定性**：如 _“局部性（locality）：Bug 收拢在单一模块内”_、_“杠杆率（leverage）：单一接口对接 N 个调用点”_、_“接口浓缩，内部实现吞并了包装器”_。切勿编写 _“更易于维护”_ 或 _“代码更整洁”_ 等空洞词汇——这些词汇不在术语表中，没有任何实际技术分量。

拒绝任何模棱两可、委婉客套或“值得注意的是……”等废话。如果一句话能写成列表符号，就写成列表符号。如果一个列表符号可以被删掉，就删掉它。如果一个概念不在 /codebase-design 术语表中，在发明新词前，优先从现有术语表中寻找能精准替代的技术词汇。
