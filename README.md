# llm-observability

面向 LLM / RAG 应用的 Spring Boot 可观测组件，基于 **Micrometer Observation + OpenTelemetry**：

- 步骤级 span（`@TelemetryStep`）与会话级 trace（`@TelemetryConversation`）
- 结构化事件日志（`TelemetryLogger` / `TelemetryStructuredLog`）
- 统一事件管线（processor 摘要/截断 + exporter 落 span/日志/指标）
- 事件过滤器链（Spring 风格 SPI：宿主实现 `TelemetryFilter`，覆盖事件/结构化日志/普通日志全生命周期）
- 框架无关的 LLM 观测规范（`LlmObservations` / `LlmTraceHandler`，写标准 `gen_ai.*` 语义）
- 属性 key 单一来源（`AttributeKeys`），业务零字面量
- 后端可插拔：`llm-observability-backends` 提供 OpenObserve / Langfuse 适配

核心原则：**业务只面向 OTel / Micrometer 写通用语义，后端可插拔**。

## 效果展示

OpenObserve Traces（span 列表、耗时、状态与 `gen_ai.*` 属性）：

![OpenObserve Traces](doc/images/openobserve-traces.png)

Langfuse Traces（trace 列表）：

![Langfuse Traces](doc/images/langfuse-traces.png)

Langfuse 单条 trace 详情（输入/输出、token 用量、耗时）：

![Langfuse Trace](doc/images/langfuse-trace.png)

## 模块

| 模块 | 说明 |
|---|---|
| `llm-observability` | 核心 starter（步骤/会话/LLM 观测、事件管线、跨线程传播） |
| `llm-observability-backends` | OpenObserve / Langfuse 配置、直连 OTLP exporter、属性映射、读取/编排客户端 |
| `llm-observability-examples` | 后端客户端接口使用示例（可编译、可运行只读演示） |

## 要求

- JDK 17+
- Spring Boot 3.5.x

## 快速开始

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.apprentice-ol.llm-observability</groupId>
    <artifactId>llm-observability</artifactId>
    <version>0.1.1</version>
</dependency>
```

后端适配按需引入 `llm-observability-backends`，配置前缀统一为 `telemetry.*`。宿主需自行提供 OTel 导出依赖（actuator + tracing bridge + OTLP exporter）。

引入后自动装配生效，业务用注解或 `TelemetryTemplate` 埋点：

```java
@TelemetryStep("rag.retrieve")
public List<Chunk> retrieve(String query) { ... }
```

## 实际宿主：spring-rag

[spring-rag](https://gitee.com/apprentice-ol/spring-rag)（即 springai-rag，RAG 学习练手项目）已把本组件用作全链路可观测组件：

- 查询链（归一化/改写/意图/检索/重排/Agent）、入库链路与 Eval 均用 `@TelemetryStep` 埋点；`ChatController` 入口用 `@TelemetryConversation` + `@TelemetryStep`
- `StreamChatPipeline` 对 AOP 盲区用 `TelemetryTemplate.step`；`TelemetryDimensions` 通过 `dimensionOnOutput` 自动提取 intent、agent 范式
- 本地经 otel-collector 扇出 OpenObserve + Langfuse，服务器直连 OpenObserve；配置收敛在 `telemetry.*` 与 `.env`
- 详细埋点规范见 springai-rag 仓库的 `docs/llm-observability-guide.md`

## 发布与使用

通过 **JitPack** 发布（推荐，公开仓库免 token）。仓库为 public，已打 `0.1.1` 标签，任何人无需凭据即可拉取。

发新版流程（改完代码打新 tag，JitPack 自动构建）：

```powershell
git tag 0.2.0
git push origin 0.2.0
```

完整说明见 [doc/publish-jitpack.md](doc/publish-jitpack.md)；
GitHub Packages（需 token）作为备选发布方式见 [doc/publish-github-packages.md](doc/publish-github-packages.md)。

## 构建

```bash
mvn -B package
```

详细使用说明见各模块 README 与 [doc/README.md](doc/README.md)（含核心模块 README 副本、效果图与截图脚本）。