# 验证记录

最新功能回归日期：2026-10-15，基线 `e2f9b44 second demo`。

- 后端干净编译与 verify：25 项测试通过，Java 格式检查通过；18 处 Jackson 3 弃用 asText 调用改为 asString，无对应弃用警告。
- 前端：14 项测试、lint、Prettier 和生产构建通过。
- 运行中服务：67 项 HTTP/权限检查通过；浏览器 102 个可见状态检查点通过。
- 已修复带编号初始挂载时抽屉不加载的问题，并通过组件及真实深链接验证。
- 独立数据库中的新增/修改、自评、打卡、五条干预及关闭历史经重启比较一致；原演示数据库 SHA256 未变。隔离服务已停止。

详细模块、用例、复现方法和范围见 [全功能回归测试报告](functional-regression.md)。

本次环境：Windows、JDK 21.0.12.1、Node 24.15.0、Maven Wrapper 3.10.0、Python 3.14、PowerShell 7。Python 启动脚本仅使用标准库，未安装新全局工具。前端仍有图表 chunk 体积提醒。

- `python start.py --restart --no-browser` 完成前后端启动和已记录进程的安全重启；修正了 Maven 可复现打包将 JAR 时间设为 1980 年造成的重复构建判断。
- 实际 101 条问卷与 100,000 条合成生活方式数据均训练成功，分别保存到 `models/artifacts/survey/`、`lifestyle/`。
- 浏览器确认中心工作台、两份实验切换及各自交互推理、学生个人空间可用；保存一条明确标记为虚构的干预与随访记录。
- 演示数据库保留重启前的操作。机器学习只使用原始 CSV，不使用业务演示历史。

## 范围与限制

当前基准为 JDK 21.0.12.1、Node 24.15.0、npm 11.12.1、Maven Wrapper 3.10.0、Python 3.14、PowerShell 7.5.6。Python 启动脚本使用标准库。

此前已完成 Windows 空格路径、干净导出副本和本地工作区生成检查；这次集中检查没有重复整套安装复现。Linux/macOS 启动分支未实机验证，GitHub CI 以实际运行记录为准。

上述检查证明本机 Demo 与实验链路可运行，不代表临床效力、真实学生系统部署或远程仓库已包含未提交的工作区变更。课程截图与实验报告位于仓库外的 workspace。

## 本轮 care workflow 加固验证（2026-10-13，macOS ARM64）

- 本地工具已准备在忽略目录 `.tools/`：Temurin JDK 21.0.12.1、Maven Wrapper 3.10.0 及其本地仓库、Python 3.12.14；系统 Node.js 26.9.0、npm 11.19.1 满足最低版本。未修改全局 `JAVA_HOME`、PATH 或安装系统服务。
- 按 macOS 等效命令完成 `backend/mvnw clean verify`：5 个测试类共 57 项通过，0 失败、0 错误；Spotless/Google Java Format 检查通过。覆盖自评/打卡规则边界、幂等与并发、预警去重、合法状态与竞态、随访历史、通知范围、V2→V3 迁移和既有机器学习/脚手架测试。
- 完成前端 `npm run lint`、`npm test`、`npm run format:check`、`npm run build`：5 个测试文件共 12 项通过，规范与格式通过，生产构建成功。包括自评提交页及个人档案的综合等级、历史提交等级与当前等级区分、后续打卡提高关注时的支持提示，以及随访历史过滤。测试环境提示 jsdom 未实现 `window.scrollTo`，不影响断言；构建保留既有 Chart chunk 体积提示（约 553 kB）。
- 本轮未运行 Windows PowerShell 检查脚本、真实浏览器自动化、外部邮件/短信供应商或公网部署；这些限制与 macOS 环境及本项目站内通知 Demo 设计一致。V2 历史综合规则输入无法恢复，迁移按原 PHQ `risk_level` 兼容回填；异常重复活动个案不自动删除，升级前需人工核对并处理。
此前 2026-10-12 已完成 Demo、双原始数据集训练，以及 Windows 空格路径、干净导出副本和本地工作区生成检查；本次未重复整个安装流程。Linux/macOS、其他浏览器、压力和长期稳定性未在本次实机验证，GitHub CI 以实际运行记录为准。
