# 模块深化（Deepening）
在给定依赖的前提下，如何安全地将一组浅模块（Shallow Modules）深化为一个深模块（Deep Module）。默认采用 [SKILL.md](SKILL.md) 中的词汇——**module（模块）**、**interface（接口）**、**seam（接缝）**、**adapter（适配器）**。

## 依赖分类
在评估一个模块是否适合进一步深化时，首先要对它的依赖进行分类。依赖属于哪一类，会直接决定：深化后的 module 应该如何跨 seam 进行测试。

### 1. 进程内（In-process）
纯计算、内存状态、无 I/O。这类依赖始终可以进行深化，直接合并这些浅模块，并通过新的 Interface 对深化后的 Module 进行测试，无需额外引入 Adapter。

### 2. 本地可替代（Local-substitutable）
拥有本地测试替身的依赖（如 Postgres 的 PGLite、内存文件系统），如果已经存在可靠的本地替代实现，那么这类依赖也可以进行深化。在测试套件中直接使用加深后的模块进行测试。此时接缝（seam）位于module为内部接缝，对于调用方而言，这个内部 Seam 应该是不可见的。禁止因为测试需要替代依赖，就把这个 Seam 暴露到 Module 的外部 Interface 中。

### 3. 远程但自有（Ports & Adapters）
跨越网络边界的自有服务（自有微服务、内部 API、跨网络部署的自有服务），虽然存在网络边界，但服务本身仍由团队控制，这种情况下应在接缝(seam)处定义一个 **port**（端口），生产环境提供 HTTP Adapter，测试环境提供 In-memory Adapter。这样即使模块跨网络部署，核心逻辑仍然能够集中在一个深模块中。

### 4. 真正外部（Mock）
这一类指的是你无法控制的第三方服务（例如：Stripe、Twilio 等），深化后的 module 应该把该外部依赖作为一个注入的 Port，测试时提供一个 Mock Adapter。
核心原则是：第三方服务的不可控性应该被隔离在 Adapter 后面，而不是泄漏到 Deep Module 的业务逻辑中。

## 接缝纪律
**一个 Adapter 只是“假设存在的 Seam”，两个 Adapter 才说明这个 Seam 真实存在** 除非至少有两个适配器成立（通常是生产 + 测试），否则不要引入 port。单适配器接缝只是间接层。
**内部接缝与外部接缝。** 深度模块可以同时拥有内部接缝（对实现私有，供自身测试使用），以及位于接口处的外部接缝。

内部 Seam 则属于 Implementation 私有结构，可能只用于：
- 内部依赖替换
- Module 自身测试
- 局部实现隔离（外部 Seam 位于 Module 的 Interface）。

不要因为测试代码会使用内部 Seam，就把它暴露到 Module 的外部 Interface 中，也就是说，测试需要替换某个内部依赖，并不意味着调用方也需要知道这个依赖。

## 测试策略：替换，而非叠加
- 当新的 Deep Module 已经能够通过它的 Interface 被完整测试后，旧浅模块上的单元测试通常就已经失去价值，应当删除它们。
- 在加深后的模块 Interface 上编写新测试。新的测试应该围绕：深化后 Module 的 Interface来编写，因为：**Interface 就是 Test Surface（测试面），**调用方如何使用这个 Module，测试就应该如何测试这个 Module。
- 测试通过接口断言可观察结果，而非内部状态，核心原则：测试行为，而不是实现。
- 测试应该能够经受内部重构，一个好的 Deep Module 测试应该满足：Module 内部如何重构，测试都不需要修改，只要 Interface 行为没有变化。若实现变更时测试也必须修改，则说明它越过了接口进行测试。

