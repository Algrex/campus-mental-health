# Demo 验证记录

验证日期：2026-10-12，Windows 本机。当前文档记录 Demo 与双数据集实验的交付范围。

## 构建与自动检查

- 后端 `clean verify` 成功：7 项测试通过，覆盖角色/学院/个人记录隔离、测评与打卡、CSV 导入、个案闭环，以及 Weka 评估与双数据集上下文隔离。
- 前端 lint、6 项测试、Prettier 检查与生产构建通过。最终界面措辞调整后再次完成格式检查及构建。
- Java Spotless/Google Java Format 检查通过。
- 前端图表 chunk 约 553 kB，构建仍提示体积较大；页面已按路由加载，后续可进一步拆分图表依赖。

## 运行与实际实验

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
