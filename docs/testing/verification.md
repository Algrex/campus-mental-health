# 工程骨架验证记录

日期：2026-10-12，Asia/Shanghai。

## 骨架建立时的检查

| 检查 | 实际结果 |
| --- | --- |
| Java / Maven | Temurin JDK 21.0.12.1、Maven 3.10.0，版本与路径验证通过 |
| 后端构建 | Maven `spotless:apply verify` 成功，生成可运行 Spring Boot JAR |
| 后端集成测试 | 3 项通过：健康与元数据、匿名业务访问拒绝、Flyway/JPA 持久化 |
| Java 格式 | Spotless Google Java Format 检查通过 |
| 前端 lint | ESLint 通过 |
| 前端测试 | 1 个测试文件、2 项测试通过：请求失败与取消信号 |
| 前端格式 | Prettier 检查通过 |
| 前端构建 | Vite 生产构建通过；全量 Element Plus 注册导致入口 JS 超过 500 kB，后续页面实现时优化按需加载 |
| SSH | 指定密钥成功认证为 Algrex；仅输出认证结果，没有读取或输出私钥 |
| 远程仓库 | `origin` 指向指定 SSH 地址，`git ls-remote origin` 成功，远程当前没有 refs |

## 迁移后的复核

工程已迁移到 `Class7/campus-mental-health`，课程资料位于 `Class7/course-materials`。

- 根目录 `scripts/check.ps1` 执行成功：后端 3 项集成测试和格式检查、前端 lint、2 项测试、格式检查、生产构建均通过。
- 分别使用 `scripts/dev.ps1 -Service backend` 和 `-Service frontend` 启动成功。
- 前端页面 HTTP 200，后端健康 HTTP 200，经 Vite 代理读取工程信息 HTTP 200；匿名访问学生业务端点 HTTP 401，符合骨架的默认拒绝策略。
- 文件数据库完成首次迁移；尚未验证带业务记录的重启、备份或恢复。
- 验证完成后停止本次启动的服务，不占用后续开发端口。

骨架检查不代表量表、真实多源数据、模型训练、预警通知和干预业务已经完成。GitHub Actions 工作流已配置，但尚未推送代码，因此未执行远程 CI。
