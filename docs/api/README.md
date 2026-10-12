# 接口契约

当前可用端点：

| 方法 / 路径 | 授权 | 响应 |
| --- | --- | --- |
| GET `/actuator/health` | 匿名 | Spring 健康状态，无数据库详细信息 |
| GET `/api/v1/system/info` | 匿名 | `name`、`stage` 与 `modules` 工程元数据 |

`stage` 为 `SCAFFOLD`，每个模块含 `key`、`title`、`owner`、`status`，状态为 `PLANNED`。这不是监测业务数据。

所有其他路径默认拒绝访问；当前无可登录账号。A 实现认证后与各模块确定授权策略，不通过全局 `permitAll` 开放档案数据。

后续契约至少包含：负责人、调用方、方法与路径、请求/响应字段、校验、分页和时间语义、错误格式、角色与数据范围、幂等及事务要求、测试样例。待规划接口不能写成“已实现”。

拟定模块前缀：`auth`、`students`、`imports`、`assessments`、`check-ins`、`risks`、`alerts`、`notifications`、`interventions`、`follow-ups`。
