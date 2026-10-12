# 本地开发与检查

## 运行配置

后端在 `backend` 目录运行。可通过进程环境变量设置 `SERVER_ADDRESS`、`SERVER_PORT`、`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。默认只监听 `127.0.0.1`，数据库为 `./runtime/campus-mental-health`，不开放 H2 控制台。

前端 `.env.example` 提供 API 地址样例。开发默认使用同源 Vite 代理，无需复制 `.env`；若自定义 API 地址，后续须设计跨域、会话和授权方案。

## Java 工具

`scripts/dev.ps1` 与 `scripts/check.ps1` 优先检查 `JAVA_HOME`，其缺失时识别工作区 `.tools/jdk`，最后尝试 PATH 中的 `javac`。找到编译器后要求主版本 21；脚本只修改当前进程环境。

项目使用官方 Maven Wrapper。脚本将 Wrapper 缓存放到 `.tools/maven-user`，并将 Maven 依赖放到 `.tools/m2`，都在 Git 忽略范围内；不会修改用户全局 Maven 配置。

## 检查命令

根目录执行 `pwsh -File scripts/check.ps1`；每个步骤失败时立即停止并保留退出码。首次先运行 `npm.cmd --prefix frontend ci --offline=false`。

后端分别使用 `mvnw.cmd verify`、`mvnw.cmd spotless:apply`；前端分别使用 `npm.cmd run lint`、`test`、`format:check`、`build`。在对应目录运行这些命令。依赖缓存需可写。

`npm.cmd ci` 使用锁文件；当个人 npm 配置启用了离线模式且无缓存时，加 `--offline=false`。不要为绕过下载错误关闭 TLS 校验。

## 后续开发约定

接口以 `/api/v1` 开头，DTO 与实体分离。业务实现前更新 `docs/api` 契约、数据字典和相关测试。保留当前 deny-all 配置，只有配套角色和数据范围测试通过后才放行业务端点。

模型训练、分级规则、量表计分与通知渠道仍按计划核实；不要将占位路由或依赖下载作为业务已完成的证据。
