# llm-observability

一个基于 **Micrometer Observation + OpenTelemetry** 的 Spring Boot 可观测组件，面向 RAG / LLM 类应用，提供业务级观测能力：

- 步骤级 span（`@TelemetryStep`）
- 会话级 trace（`@TelemetryConversation`）
- 结构化事件日志（`TelemetryLogger` / `TelemetryStructuredLog`）
- 统一的事件处理与导出管线（`processor` + `exporter`）
- 事件过滤器链（Spring 风格 SPI：宿主实现 `TelemetryFilter`，组件在整个日志生命周期统一执行）
- 框架无关的 LLM 观测规范（`LlmObservations` / `LlmTraceHandler`）

核心原则：**业务只面向 OTel / Micrometer 写通用语义，后端（OpenObserve、Langfuse、任意新后端）可插拔**。

## 目录

1. [快速开始](#快速开始)
2. [使用方式](#使用方式)
3. [流式、异步与跨线程](#流式异步与跨线程)
4. [接口扩展](#接口扩展)
5. [新增后端](#新增后端)
6. [配置项](#配置项)
7. [注意事项](#注意事项)

## 快速开始

### 依赖

引入本组件：

```xml
<dependency>
    <groupId>com.nageoffer.ai</groupId>
    <artifactId>llm-observability</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

宿主应用需要自行提供 OTel 导出能力（本组件只负责埋点，不绑定具体 exporter）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-otlp</artifactId>
</dependency>
<!-- 日志要走 OTLP Logs 时额外引入 OpenTelemetry logback appender -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>2.16.0-alpha</version>
</dependency>
```

### 配置

```yaml
telemetry:
  tracer-name: my-service
  sampling:
    probability: 1.0
  collector:
    enabled: ${TELEMETRY_COLLECTOR_ENABLED:true}
    base-url: ${OBSERVE_OTLP_ENDPOINT:http://localhost:4318}
    traces-endpoint: ${TELEMETRY_COLLECTOR_TRACES_ENDPOINT:http://localhost:4318/v1/traces}
    logs-endpoint: ${TELEMETRY_COLLECTOR_LOGS_ENDPOINT:http://localhost:4318/v1/logs}
    metrics-endpoint: ${TELEMETRY_COLLECTOR_METRICS_ENDPOINT:http://localhost:4318/v1/metrics}
```

> `TelemetryEnvironmentPostProcessor` 会在启动早期把上述配置桥接为 Spring Boot 的
> `management.otlp.tracing.endpoint` / `management.otlp.logging.endpoint` /
> `management.otlp.metrics.export.url` / `management.tracing.sampling.probability`。
>
> `telemetry.collector.enabled=false` 时，日志/指标默认直连 OpenObserve，trace 由宿主提供直连扇出配置。

日志如要统一走 OTLP Logs，宿主还需要加入 OpenTelemetry Logback appender，并在 `logback-spring.xml` 中挂载
`io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender`。

### 第一个埋点

```java
@Service
public class RetrievalService {

    @TelemetryStep("rag.retrieve")
    public RetrievalResult retrieve(SearchContext ctx) {
        // 方法执行期间自动开一个 child span，并自动记录 input/output/耗时/异常
        return doRetrieve(ctx);
    }
}
```

## 使用方式

### 1. `@TelemetryStep`：步骤埋点

只对 Spring 代理的 public bean 方法生效。

```java
@TelemetryStep("rag.channel")
public ChannelResult search(Query q) { ... }
```

如果目标 bean 暴露无参 `getName()`，span 名会自动展开为 `前缀.name`：

```java
@TelemetryStep("rag.channel")   // getName() 返回 "vector" 时，span 名 = rag.channel.vector
public class VectorSearchChannel implements SearchChannel {
    @Override
    public String getName() { return "vector"; }
}
```

`kind` 支持开独立根 trace：

```java
@TelemetryStep(value = "eval.item", kind = TelemetryStep.Kind.ROOT)
public Score run(EvalItem item) { ... }
```

### 2. `@TelemetryConversation`：会话入口

标注在 Controller 入口，自动捕获会话 id 与用户问题，写入根 span 的 OTel GenAI 标准属性 `gen_ai.input.messages` / `gen_ai.output.messages`（OpenObserve 原生识别；Langfuse 由 backends 模块兼容映射）。

```java
@PostMapping("/chat/stream")
@TelemetryStep("rag.chat")
@TelemetryConversation(questionIndex = 0, conversationIdIndex = 1)
public SseEmitter stream(@RequestBody String question, String conversationId) {
    // conversationId 为空时切面会生成 UUID 并回填
}
```

### 3. `TelemetryLogger`：普通日志 + 结构化事件

```java
private static final TelemetryLogger log = TelemetryLogger.of(MyService.class);

public void doSomething(String q) {
    log.info("[检索] 命中={} 条", 3);                 // 普通日志，自动带 traceId/spanId
    log.event("rerank.scores", Map.of("scores", list)); // 附属结构化事件，挂在当前 step 上
    log.conversationOutput(answer);                     // 写会话级输出
}
```

### 4. `TelemetryStructuredLog`：直接发结构化事件

用于框架组件或不想持有 `TelemetryLogger` 的场景：

```java
TelemetryStructuredLog.emit("llm.request", Map.of("model", "deepseek-chat"));
```

结构化事件 schema：`{"_event":"step.output","step":"rag.retrieve","step_id":"<spanId>","data":{...},"duration_ms":82}`。

### 5. `TelemetryTemplate`：手动埋点（AOP 盲区）

同类内部调用、私有方法、静态方法切不到，用手动门面：

```java
private final TelemetryTemplate obsTemplate;   // 构造注入

public Result manual(Object input) {
    return obsTemplate.step("rag.manual", input, () -> doWork(input));
}

public void manualRoot(Object input) {
    try (TelemetrySpan span = obsTemplate.openTrace("background.job")) {
        span.input(input);
        span.tag("job.type", "rebuild");
        // ...
        span.output(result);
    }
}
```

`TelemetrySpan` 链式 API：`tag / input / output / outputRaw / traceInput / traceOutput / error / close / finish`。

## 流式、异步与跨线程

### Flux / Mono

推荐用注解，返回类型是 `Flux`/`Mono` 时切面自动装饰：

```java
@TelemetryStep(value = "rag.answer", captureOutput = true)
public Flux<String> answer(Prompt prompt) {
    return chatClient.stream(prompt).map(...);
}
```

手动方式：

```java
public Flux<String> answer(String input, Flux<String> upstream) {
    return obsTemplate.stream("rag.answer", input, upstream, true);
}
```

`captureOutput=true` 会把完整流式输出原样记为 span output（受 20000 字符上限兜底截断）。

### SSE / ResponseBodyEmitter

返回 `SseEmitter` / `ResponseBodyEmitter` 时，`@TelemetryStep` 会通过 `onCompletion/onTimeout/onError` 回调关闭 span，无需手动处理。

### 虚拟线程

用 `ContextPropagator.wrap` 把 MDC / OTel Context / 会话上下文透传到虚拟线程：

```java
Thread.ofVirtual().start(ContextPropagator.wrap(() -> {
    // 这里能继续读到 traceId/spanId 和会话上下文，新开的 span 会挂到父 trace
}));
```

### 线程池

优先用 Spring 的 `ThreadPoolTaskExecutor` + `ContextPropagatingTaskDecorator`：

```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
```

或不使用 `ContextPropagatingTaskDecorator` 时，提交前手动包装：

```java
executor.submit(ContextPropagator.wrap(task));
```

### Reactor

`ContextPropagationConfiguration` 已注册 MDC / OTel / 会话上下文 accessor，并开启
`Hooks.enableAutomaticContextPropagation()`，因此 Reactor 回调线程会自动恢复上下文，通常无需手动处理。

### 跨线程写 span 的通用规则

凡是在异步完成点要写 span 数据的，**先在业务线程捕获句柄/写入器引用，再在回调里使用**，不要依赖 `Span.current()`。

```java
Consumer<Object> outputWriter = log.conversationSink();   // subscribe 前捕获
flux.doOnNext(...)
    .doOnComplete(() -> outputWriter.accept(fullAnswer));
```

## 接口扩展

`ObservationPipeline` 会自动收集所有 `ObservationProcessor` / `ObservationExporter` bean，并按 `@Order` 排序执行。

### 事件过滤器链（SPI：前置 / 后置）

组件**不内置任何过滤规则**（脱敏、丢弃等策略由宿主实现），只提供 Spring 风格的过滤器链：宿主实现 `TelemetryFilter`
并注册为 Spring bean，组件自动收集全部过滤器、按 `@Order` / `Ordered` 排序统一执行。

过滤器链在可观测过程中的位置（包住“处理 + 落库”的整条下游）：

```text
业务埋点（@TelemetryStep / TelemetryTemplate / TelemetryLogger.event）
   │ 产生 TelemetryEvent
   ▼
ObservationPipeline.emit
   │
   ▼
┌──────────────────────────────────────────────────────────────┐
│ TelemetryFilterChain（同一过滤器链，按 @Order 排序）              │
│                                                              │
│   chain.doFilter(event) 之前 = 前置：可修改 / 丢弃               │
│              │                                               │
│              ▼                                               │
│   processor 链：摘要(10) → 截断(20) → 用户扩展(30+)             │
│              │                                               │
│              ▼                                               │
│   exporter 链：span attribute(10) → 结构化日志(20) → 指标(30)  │
│              │                                               │
│   chain.doFilter(event) 之后 = 后置：下游全部完成后执行           │
└──────────────────────────────────────────────────────────────┘
   │
   ├──► span attribute ──► OTel Traces ──► 后端
   └──► 结构化日志 ──► slf4j ──► logback appender ──► OTLP Logs / 后端
```

除事件链路外，同一过滤器链还覆盖另外两条日志路径：

1. **直发结构化日志**：`TelemetryStructuredLog.emit` 直接调用时，经静态桥走同一过滤器链，再进 slf4j；
2. **普通日志**：`TelemetryLogbackFilter` 默认装到所有 logback appender 之前，业务/框架/第三方日志都走同一过滤器链（`telemetry.telemetry` 结构化 logger 跳过，避免重复过滤；`telemetry.filter.logback-enabled=false` 关闭）。

可运行的完整示例已放在 examples 模块：

- `PasswordRedactorFilter`：前置修改 Map 中的 password/secret 字段（脱敏）；
- `DebugEventDropFilter`：不调用 `chain.doFilter(event)` 丢弃 `debug.trace` 事件。

代码见 [llm-observability-examples](llm-observability-examples/src/main/java/com/nageoffer/ai/llmobservability/example/filter/)。

- **前置 / 后置**：`chain.doFilter(event)` 之前的代码为前置，之后为后置；
- **丢弃**：不调用 `chain.doFilter(event)`，该事件/日志直接过滤；
- **排序**：多个过滤器按 `@Order` / `Ordered` 从小到大执行；
- **开关**：`telemetry.filter.enabled=false` 整体关闭；`telemetry.filter.logback-enabled=false` 关闭普通日志的 appender 级过滤；
- **手动执行**：`TelemetryFilterChain` 本身也是 Spring bean，可注入业务代码后调用 `apply(event)`。

### `ObservationProcessor`：处理（转）

在落地前加工、过滤、映射事件。返回 `null` 表示丢弃该事件。

```java
@Component
@Order(30)
public class EventNameFilter implements ObservationProcessor {
    @Override
    public TelemetryEvent process(TelemetryEvent event) {
        if ("debug.trace".equals(event.getName())) {
            return null;
        }
        return event;
    }
}
```

内置 processor：`SummarizeProcessor`（摘要，@Order 10）、`SpanIoLimitProcessor`（截断，@Order 20）；事件过滤器链包在 `ObservationPipeline.emit` 之外，先于 processor 执行（前置/后置见上一节）。

### `ObservationExporter`：落地（发）

把加工后的事件落到一个具体出口（span attribute / 结构化日志 / 自定义后端）。

```java
@Component
@Order(30)
public class MyMetricExporter implements ObservationExporter {
    @Override
    public void export(TelemetryEvent event, SpanWriter target) {
        if (event.getType() == TelemetryEvent.EventType.STEP_OUTPUT) {
            meterRegistry.timer("telemetry.step.duration", "step", event.getName())
                    .record(event.getDurationMs(), TimeUnit.MILLISECONDS);
        }
    }
}
```

内置 exporter：`SpanAttributeExporter`（写 span attribute，@Order 10）、`StructuredLogExporter`（结构化日志，@Order 20）。

### LLM SDK 集成

#### 场景 A：SDK 不自带 Micrometer Observation

用 `LlmObservations.record` 包一层，产出标准 `gen_ai.client.operation` span：

```java
String answer = LlmObservations.record(
        observationRegistry,
        "openai",            // gen_ai.system
        "gpt-4o-mini",       // gen_ai.request.model
        prompt,
        () -> sdk.chat(prompt),
        Resp::getText,       // completion 提取
        resp -> LlmUsage.of(resp.getPromptTokens(), resp.getCompletionTokens()));
```

#### 场景 B：SDK 已自带 Observation Context

继承 `LlmObservationFilterAdapter` 实现 `extract`：

```java
@Component
public class MySdkLlmFilter extends LlmObservationFilterAdapter<MySdkObservationContext> {
    public MySdkLlmFilter(LlmTraceHandler handler) {
        super(MySdkObservationContext.class, handler);
    }

    @Override
    protected LlmCall extract(MySdkObservationContext ctx) {
        LlmCall call = new LlmCall();
        call.setSystem("mysdk");
        call.setModel(ctx.getModel());
        call.setPrompt(ctx.getPrompt());
        call.setCompletion(ctx.getCompletion());
        call.setPromptTokens(ctx.getPromptTokens());
        call.setCompletionTokens(ctx.getCompletionTokens());
        call.setTotalTokens(ctx.getTotalTokens());
        return call;
    }
}
```

#### 场景 C：覆盖 LLM 语义记录

默认 `GenAiLlmTraceHandler` 写 `gen_ai.*`。可提供自己的 bean 覆盖：

```java
@Bean
public LlmTraceHandler llmTraceHandler() {
    return (context, call) -> {
        context.addLowCardinalityKeyValue(KeyValue.of("my.llm.model", call.getModel()));
        // ...
    };
}
```

### 覆盖核心 bean

`TelemetryAutoConfiguration` 里的核心 bean 都带 `@ConditionalOnMissingBean`，宿主可自行提供同名 bean 覆盖。

## 新增后端

### 方式一（推荐）：在 otel-collector 增加 exporter + pipeline

应用零改动，因为 app 只发 OTLP。以新增一个 `newbackend` 为例：

```yaml
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 5s

exporters:
  otlphttp/newbackend:
    traces_endpoint: http://newbackend:4318/v1/traces
    headers:
      Authorization: Basic ${env:NEW_BACKEND_AUTH}

service:
  pipelines:
    traces/newbackend:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlphttp/newbackend]
```

如果新后端需要专属字段（如 Langfuse 的 `langfuse.observation.*`），加一个 `transform` processor，把通用 `rag.*` 映射成新后端的字段，应用侧仍无感。

### 方式二：应用内直连（自定义 `ObservationExporter`）

适合非 OTLP 后端、或需要业务语义直推的场景：

```java
@Component
@Order(30)
public class MyBackendExporter implements ObservationExporter {
    private final MyBackendClient client;

    @Override
    public void export(TelemetryEvent event, SpanWriter target) {
        client.send(event.getType(), event.getName(), event.getData(), event.getDurationMs());
    }
}
```

### 连接与凭据配置

新后端的连接信息建议在宿主侧用一个 `@ConfigurationProperties` 绑定，并走环境变量，例如：

```java
@ConfigurationProperties("telemetry.mybackend")
public class MyBackendProperties {
    private String url;
    private String username;
    private String password;
}
```

```yaml
telemetry:
  mybackend:
    url: ${MY_BACKEND_URL:http://localhost:9999}
    username: ${MY_BACKEND_USERNAME:admin}
    password: ${MY_BACKEND_PASSWORD:secret}
```

这样新后端就和 Spring Boot 的 `spring.datasource` / `spring.data.redis` 一样，是一个清晰的连接块。

## 配置项

| 配置 | 说明 | 默认值 |
|---|---|---|
| `telemetry.tracer-name` | OTel tracer 名，`openTrace` 开无父根 trace 时使用 | `telemetry` |
| `telemetry.collector.enabled` | `true` 走 otel-collector；`false` 应用直连后端 OTLP 入口 | `true` |
| `telemetry.collector.base-url` | collector OTLP/HTTP 基础地址 | `http://localhost:4318` |
| `telemetry.collector.traces-endpoint` | collector traces 出口 | `base-url/v1/traces` |
| `telemetry.collector.logs-endpoint` | collector logs 出口 | `base-url/v1/logs` |
| `telemetry.collector.metrics-endpoint` | collector metrics 出口 | `base-url/v1/metrics` |
| `telemetry.sampling.probability` | trace 采样率（0.0~1.0） | `1.0` |
| `telemetry.filter.enabled` | 是否执行事件过滤器链（规则由宿主实现 `TelemetryFilter` 提供） | `true` |
| `telemetry.filter.logback-enabled` | 是否把过滤器链装到所有 logback appender 之前（覆盖普通/框架日志） | `true` |
| `telemetry.springai.log-prompt` | 桥接 `spring.ai.chat.observations.log-prompt` | `false` |
| `telemetry.springai.log-completion` | 桥接 `spring.ai.chat.observations.log-completion` | `false` |

Spring AI 宿主：LLM 的 `gen_ai` span 与 token 用量由 Spring AI 原生输出，`telemetry.springai.*` 只控制是否把 prompt/completion 原文落库。

## 注意事项

1. **大 payload 摘要**：step input/output 默认走 `SummarizeProcessor` + `SpanIoLimitProcessor`，避免整篇文档 / 完整检索结果撑爆 span 和日志。需要完整原文时用 `outputRaw` / `captureOutput=true`。
2. **敏感信息**：Spring AI 的 prompt/completion 默认不落库；`telemetry.springai.*` 排查完记得关闭。
   如需对日志/span 脱敏，实现 `TelemetryFilter` 注册为 bean 即可，组件负责在整个日志生命周期（事件链路 / 直发结构化日志 / logback appender）执行过滤器链。
3. **采样**：生产环境建议调低 `telemetry.sampling.probability`，避免 trace 数据量过大。
4. **后端无关**：业务埋点只用通用 `rag.*` / `gen_ai.*` 语义，后端专属映射下沉到 collector 的 transform processor，换后端不碰业务代码。
5. **异步规则**：跨线程写 span/output 前先捕获句柄引用，别依赖 `Span.current()`。
