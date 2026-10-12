# Campus Mental Health

校园心理健康监测与异常预警系统，仓库名称为 **`campus-mental-health`**。

本项目位于 `Class7/campus-mental-health/`，该子目录是 Git 仓库根目录；`Class7` 是工作区父目录。项目提供可构建的工程骨架：Java 后端、JavaScript 前端、H2/Flyway 持久化基础、关闭业务访问的安全配置、六人模块边界与 CI。测评、风险建模、预警和干预业务仍待各成员实现。

## 技术栈

- Java 21、Spring Boot 4.1.1、Maven Wrapper（Maven 3.10.0）。
- Spring Security、Spring Data JPA、H2 文件数据库、Flyway。
- Vue 3、Vite、Vue Router、Element Plus、ECharts。
- Java Weka 依赖已声明，训练与推理功能待 C 实现。
- Java Google 格式检查、ESLint、Prettier、JUnit 集成测试、Vitest。

版本固定在 `backend/pom.xml`、Wrapper 配置和 `frontend/package-lock.json`；环境要求依据 [Spring Boot 官方文档](https://docs.spring.io/spring-boot/system-requirements.html) 与 [Vite 官方指南](https://vite.dev/guide/)。

## 开始开发

需要 JDK 21、Node.js 24.15 或更高版本、npm 11 和 Git。Maven 由 Wrapper 获取，无需全局安装。首次安装依赖需要联网。

在仓库根目录运行：

```powershell
npm.cmd --prefix frontend ci --offline=false
pwsh -File scripts/dev.ps1 -Service backend
```

另开一个终端运行前端：

```powershell
pwsh -File scripts/dev.ps1 -Service frontend
```

- 前端：`http://127.0.0.1:5173`。
- 后端健康检查：`http://127.0.0.1:8080/actuator/health`。
- 工程模块信息：`http://127.0.0.1:8080/api/v1/system/info`。

开发脚本会先使用系统 `JAVA_HOME`，其缺失时可识别工作区 `.tools/jdk` 内的便携 JDK。`.tools` 不提交到 Git，其他成员仍需按环境清单准备自己的 JDK。后端在 `backend` 下运行，H2 文件保存到 `backend/runtime/`。

Linux/macOS 可分别在 `backend` 运行 `./mvnw spring-boot:run`，在 `frontend` 运行 `npm ci --offline=false` 和 `npm run dev`。

## 检查与构建

```powershell
pwsh -File scripts/check.ps1
```

该命令依次执行后端集成测试及格式检查、前端 lint、测试、格式检查与生产构建。后端产物在 `backend/target/`，前端产物在 `frontend/dist/`。

修改 Java 后可在 `backend` 执行 `./mvnw spotless:apply`，修改前端后可运行 `npm.cmd --prefix frontend run format`。详见 [开发指南](docs/development.md)。

## 目录与协作

```text
backend/           Java 后端，A—D 按业务模块协作
frontend/          JavaScript 前端，E 学生端、F 工作端
docs/              架构、接口、数据库、测试与 A—F 报告框架
data/              匿名样例与私有数据位置，当前没有业务数据
models/            训练产物位置，当前没有训练完成的模型
scripts/           PowerShell 启动与检查入口
.github/workflows/ GitHub Actions 构建检查
AGENTS.md          需求、协作约束和总进度
分工文档.md        A—F 详细任务
环境配置清单.md    本机实测、安装与配置任务
```

阅读 [架构说明](docs/architecture.md)、[接口说明](docs/api/README.md) 和 [分工文档](分工文档.md) 后开展对应模块工作。原始课程资料保留在父工作区的 `../course-materials/`，不属于代码仓库。

## 当前功能边界

仅健康检查与工程信息允许匿名 GET。其他路径默认拒绝访问，未创建演示账号；账号与数据权限由 A 后续实现。前端各业务入口是明确标注的占位页，不提供假数据或假成功操作。

数据库仅有学生档案基础表；模型依赖并不代表模型已训练。真实多源数据与课程报告格式仍需确认。私有数据、数据库文件、凭据、工具缓存及构建产物均排除在 Git 外。

Git 身份按用户要求配置于本仓库：`Algrex <333242616+Algrex@users.noreply.github.com>`。本地默认分支为 `main`；远程 `origin` 是 `git@github.com:Algrex/campus-mental-health.git`，已验证 SSH 认证和远程读取。连接配置完成不代表代码已推送。

本机使用现有 `id_ed25519_github` 密钥，仅在 Git 本地配置中指定，不复制密钥到仓库。其他成员使用自己的 GitHub 身份和 SSH 配置。
