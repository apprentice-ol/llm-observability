# 发布到 GitHub Packages（Maven）

让 `com.jjx.ai:llm-observability:0.1.0-SNAPSHOT` 变成可被其它项目拉取的“真依赖”，
最快的方式是发布到 GitHub Packages。本项目根 POM 已配置好
`distributionManagement`（仓库地址 `https://maven.pkg.github.com/apprentice-ol/llm-observability`）。

## 1. 生成 GitHub Token

GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token：

- 勾选 `write:packages`
- 勾选 `read:packages`
- 勾选 `repo`（发布 Maven 包需要）

## 2. 配置本机 Maven 凭据

编辑 `~/.m2/settings.xml`（没有就新建），把 Token 通过环境变量注入，不要写死在文件里：

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>apprentice-ol</username>
            <password>${env.GITHUB_TOKEN}</password>
        </server>
    </servers>
</settings>
```

然后在当前终端设置环境变量：

```powershell
$env:GITHUB_TOKEN = "你的 token"
```

## 3. 发布

在项目根目录执行：

```powershell
mvn deploy
```

会发布：

- `com.jjx.ai:llm-observability:0.1.0-SNAPSHOT`
- `com.jjx.ai:llm-observability-backends:0.1.0-SNAPSHOT`

（`llm-observability-examples` 已配置 `maven.deploy.skip`，不会发布。）

## 4. 其它项目引用

消费方 POM 需要先声明 GitHub Packages 仓库：

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/apprentice-ol/llm-observability</url>
    </repository>
</repositories>
```

再引入依赖：

```xml
<dependency>
    <groupId>com.jjx.ai</groupId>
    <artifactId>llm-observability</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

消费方同样要在 `~/.m2/settings.xml` 配置 `id=github` 的凭据（GitHub Packages 拉包也需要认证）。

## 5. 之后发布正式版本

把版本改成 `0.1.0` 后再次 `mvn deploy`，就可以给外部用正式版本号：

```xml
<dependency>
    <groupId>com.jjx.ai</groupId>
    <artifactId>llm-observability</artifactId>
    <version>0.1.0</version>
</dependency>
```

> 如果想要“任何人不配 token 都能直接拉”，最终还是要走 Maven Central（需要 Sonatype 账号、
> 验证 `jjx.ai` 域名所有权、GPG 签名），后续可以再接入。
