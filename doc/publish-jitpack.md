# 使用 JitPack 发布（免 token，推荐）

只要 GitHub 仓库是 public，打上 tag 后 JitPack 会自动构建，任何人无需 token 就能通过 Maven 拉取。

## 1. 前提

- 仓库为 public（本项目已是 public）。
- 根 POM 保持多模块结构即可，**无需**额外配置 `distributionManagement` 或凭据。

## 2. 发布流程

改完代码提交后，打一个新 tag（版本号建议与 POM 版本一致）：

```powershell
git tag 0.1.1
git push origin 0.1.1
```

首次可访问 <https://jitpack.io/#apprentice-ol/llm-observability/0.1.1> 查看构建状态，
或直接调用构建 API 触发：

```powershell
curl.exe "https://jitpack.io/api/build/com/github/apprentice-ol/llm-observability/0.1.1"
```

构建成功后即可被任何项目拉取。

## 3. 消费方使用

在 `pom.xml` 声明 JitPack 仓库：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

核心 starter：

```xml
<dependency>
    <groupId>com.github.apprentice-ol.llm-observability</groupId>
    <artifactId>llm-observability</artifactId>
    <version>0.1.1</version>
</dependency>
```

后端适配（可选）：

```xml
<dependency>
    <groupId>com.github.apprentice-ol.llm-observability</groupId>
    <artifactId>llm-observability-backends</artifactId>
    <version>0.1.1</version>
</dependency>
```

## 4. 注意事项

- JitPack 按 tag 构建一次；改了代码必须打**新 tag**（如 `0.2.0`），旧版本不会原地更新。
- 想每次都跟随 main 最新代码，可用 `main-SNAPSHOT`，但不稳定，生产不建议。
- 需要匿名拉取时 JitPack 是最终方案；GitHub Packages 需要 token
  （见 [publish-github-packages.md](./publish-github-packages.md)），Maven Central 需要 Sonatype 账号与域名验证。
