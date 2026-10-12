# 工程架构与模块边界

项目是单仓库、单体 Java 后端与独立 Vue 前端。开发时 Vite 将 `/api` 与 `/actuator` 代理到本机后端；数据库使用 H2 文件模式，由 Flyway 迁移，Hibernate 仅验证结构。

| 模块 | 负责人 | 职责 |
| --- | --- | --- |
| auth、config、common、system | A | 账号、安全、公共错误、工程信息与启动 |
| students、assessments、behavior、academics、checkin | B | 档案、量表与多源记录、数据库设计 |
| risk | C | 数据处理、特征、训练/推理、异常与风险 |
| alerts、notifications、intervention | D | 预警、通知队列、咨询、干预和复评 |
| frontend/src/modules/auth、student | E | 公共前端与学生端 |
| frontend/src/modules/workspace、contact | F | 工作端、联系人页与流程回归 |

后端模块预留 controller、service、repository、dto、domain 包。已有学生实体与 Repository 是持久化基础，没有自动暴露为 REST API。业务事务放在 Service 中，权限同时校验角色与记录归属。

当前只实现 `/actuator/health` 和 `/api/v1/system/info`，后者返回工程阶段和模块负责人。所有业务模块状态为 `PLANNED`，不代表可用功能。

前端共用布局、请求层、路由和占位组件。后续成员将各自业务页面放入对应 `modules` 目录并替换路由组件；导航本身不提供授权保护，后端是权限边界。

持久化、业务模块、前端页面和模型分别推进，关键链路依次为：数据入库 → 风险评估 → 预警投递 → 受理干预 → 随访复评。每步由主责成员提供真实接口与测试，再完成端到端联调。
