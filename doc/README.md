# 文档目录

本目录集中存放 llm-observability 的重要文档与效果图。

## 文档

- [llm-observability-README.md](./llm-observability-README.md)：核心模块完整 README（事件过滤器链、生命周期、配置、接口扩展等）。
- 根目录 [README](../README.md)：项目总览与快速开始。

## 效果图

以下截图来自 springai-rag 宿主项目接入 llm-observability 后的真实运行效果：

| 图 | 说明 |
|---|---|
| [openobserve-traces.png](./images/openobserve-traces.png) | OpenObserve Traces 页面：span 列表、耗时、状态、`gen_ai.*` 等属性 |
| [langfuse-traces.png](./images/langfuse-traces.png) | Langfuse Traces 页面：trace 列表、trace 名、数量、环境 |
| [langfuse-trace.png](./images/langfuse-trace.png) | Langfuse 单个 trace 详情：输入/输出、token 用量、耗时、模型 |

## 截图脚本

[capture-ui-screenshots.cjs](./scripts/capture-ui-screenshots.cjs)：基于 Playwright 的无头截图脚本，
可在 `mcp/playwright` Docker 容器内运行（登录 OpenObserve / Langfuse 后截取 Traces 页面）。

```bash
docker cp doc/scripts/capture-ui-screenshots.cjs quirky_keldysh:/tmp/capture.cjs
docker exec -e NODE_PATH=/app/node_modules quirky_keldysh node /tmp/capture.cjs
docker cp quirky_keldysh:/tmp/screenshots/openobserve-traces.png doc/images/
docker cp quirky_keldysh:/tmp/screenshots/langfuse-traces.png doc/images/
docker cp quirky_keldysh:/tmp/screenshots/langfuse-trace.png doc/images/
```
