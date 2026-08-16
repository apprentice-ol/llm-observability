# llm-observability-examples

后端客户端接口（`llm-observability-backends`）的使用示例模块：

- `LangfuseDatasetExample`：`LangfuseDatasetClient` / `LangfuseScoreClient` 用法（列数据集条目、关联 run item、提交评分），
  以及综合编排参考实现 `runDatasetEvaluation`（跑数据集 → 关联 → LLM 打分写回）。
- `OpenObserveQueryExample`：`OpenObserveQueryClient` 用法（按 traceId 查日志 / 查 span 链路）。
- `PasswordRedactorFilter` / `DebugEventDropFilter`：事件过滤器链（`TelemetryFilter`）示例——前置脱敏、不调用 `chain.doFilter` 丢弃事件。
- `ExampleRunner`：只读演示（默认关闭）。

## 运行只读演示

需要本机已启动 Langfuse（:3000）与 OpenObserve（:5080），并配置凭据：

```bash
set LANGFUSE_AUTH=<base64(pk:sk)>
set TELEMETRY_EXAMPLE_ENABLED=true
set TELEMETRY_EXAMPLE_DATASET=liveRAG
set TELEMETRY_EXAMPLE_TRACE_ID=<一个真实 traceId>
mvn -o -s ../.mvn-settings.xml -pl llm-observability-examples -am -DskipTests package
java -jar target/llm-observability-examples-0.1.0-SNAPSHOT.jar
```

演示内容：列出 `liveRAG` 前 3 条黄金数据，按 traceId 查日志与 span（均为只读）。

## 写操作（关联 run item + 评分）如何接入

参考 `LangfuseDatasetExample#runDatasetEvaluation`：框架只提供原语，应用注入三个回调：

```java
@Component
@RequiredArgsConstructor
public class DatasetEvalService {

    private final LangfuseDatasetExample example;

    public void run(String runName, int limit) {
        example.runDatasetEvaluation(
                "liveRAG",                       // 数据集名
                runName,                         // 实验名
                limit,
                question -> ragAnswer(question), // 1. 你的真实 RAG 调用 → 模型回答
                question -> findTrace(question), // 2. 反查该次运行的 trace/observation
                (q, answer, expected) -> judge(q, answer, expected)); // 3. LLM 判分
    }
}
```

说明：

- `AnswerProvider`：问题 → 模型回答（替换为你的检索问答链路）。
- `TraceLinkResolver`：问题 → `TraceLink(traceId, observationId)`（跑完后按会话/请求 id 反查 Langfuse）。
- `ScoreJudge`：问题/回答/黄金答案 → `ScoreVerdict(value, comment)`（调用 DeepSeek 等 LLM judge）。
- 关联后分数写入数据集 run；v3 下 Langfuse 不会自动执行 experiment 评估规则，评分由应用/脚本调 LLM 后写回；
  升级 v4 后由评估规则自动打分。

## 配置参考

\src/main/resources/application-telemetry.example.yaml\ 是完整的可观测性配置模板（来自 springai-rag 生产宿主），
覆盖 collector / sampling / limits / Spring AI / OpenObserve / Langfuse 全部配置面，每个键都有中文注释。

- 示例应用本身只加载 \pplication.yml\ 的最小配置（默认 localhost + 环境变量占位）。
- 需要完整配置时：把 \pplication-telemetry.example.yaml\ 复制到你自己的应用（或合并内容），
  并按需设置 \TELEMETRY_*\ / \LANGFUSE_*\ / \OPENOBSERVE_*\ 环境变量。
- 该文件以 \.example.yaml\ 结尾，Spring Boot 不会自动加载，纯参考用途。