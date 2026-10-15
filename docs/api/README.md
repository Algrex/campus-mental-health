# REST 接口

除健康、系统信息和会话获取外，均需登录。写请求发送 `X-XSRF-TOKEN`（由会话接口返回），带同源 Cookie。时间由本地后端记录，JSON 日期 `YYYY-MM-DD`、日期时间 ISO 字符串；不包含时区偏移的业务时间用于本机演示。

错误一般为 `{ "message": "可读原因" }`；400 输入无效，401 未登录，403 角色或记录越权，409 重复键/个案当前状态冲突/模型未准备。框架解析失败保留 HTTP 状态，前端不会把失败显示为成功。

| 方法与路径（/api/v1 前缀） | 权限 | 用途 |
| --- | --- | --- |
| GET /system/info | 匿名 | name、stage=DEMO、version、功能名称 |
| GET /auth/session | 匿名 | csrfToken、user（匿名为空） |
| POST /auth/login | 匿名 + CSRF | URL 编码 username/password，建立会话 |
| POST /auth/logout | 已登录 + CSRF | 销毁会话 |
| GET /dashboard | 中心/辅导员 | 范围内统计、趋势、等级分布、优先个案、当前计划随访 |
| GET /students?q=&college=&level= | 中心/辅导员 | 范围内学生列表，前端每页 12 条 |
| GET /students/{uuid} | 中心/本学院辅导员/本人 | 基础档案、测评、打卡、趋势；学生不返回工作人员预警记录 |
| POST /students | 中心/辅导员 | 新增档案，辅导员只能本学院 |
| PUT /students/{uuid} | 中心/本学院辅导员 | 修改档案 |
| POST /students/import | 中心/辅导员 | `{csv}`，表头固定、1–500 行、逐行成功/错误 |
| GET /profile | 学生本人 | 个人健康档案 |
| POST /assessments | 学生本人 | `{answers:[9 个 0–3 整数],requestId}`，幂等提交；返回测评级别及综合规则级别 |
| POST /check-ins | 学生本人 | `{mood,sleepHours,stress,note}`，当天插入或更新；睡眠最多一位小数 |
| GET /alerts?status= | 中心/辅导员 | 范围内预警，优先联系/重点关注排序 |
| GET /alerts/{uuid} | 中心/本学院辅导员 | 个案、档案、干预时间线 |
| POST /alerts/{uuid}/accept | 中心/本学院辅导员 | PENDING → ACCEPTED，body `{}` |
| POST /alerts/{uuid}/interventions | 中心/本学院辅导员 | `{kind,content,nextDate}`；受理后进入 INTERVENING |
| POST /alerts/{uuid}/review | 中心/本学院辅导员 | INTERVENING → AWAITING_REASSESSMENT |
| POST /alerts/{uuid}/close | 中心/本学院辅导员 | 待复评时提交 `{reason}` 5–600 字符的人工复评与关闭说明 → CLOSED |
| GET /follow-ups | 中心/辅导员 | 范围内干预与随访状态；保留被替代或取消的历史 |
| GET /notifications | 四类已登录用户 | 自己账号收件箱；辅导员还须符合学生当前学院范围 |
| POST /notifications/{uuid}/read | 收件人本人 | 标记已读；辅导员还须符合学生当前学院范围 |
| GET /ml/summary?dataset=survey/lifestyle | 中心/辅导员 | 来源、特征、数据划分、参数、指标、ROC、模型解释 |
| POST /ml/train | 中心 | 训练指定数据，body `{dataset:"survey"}` 或 `{dataset:"lifestyle"}`；`{}` 使用默认数据集 |
| POST /ml/predict | 中心/辅导员 | `dataset` 加对应特征，返回该数据集模型的概率与操作阈值标签 |

`GET /actuator/health` 匿名，位于上述前缀之外，仅返回健康状态。

## 档案与导入

档案 DTO：`studentCode` 1–64、`displayName/college/major/contactName` 1–100 字符；`yearName` 1–30、`gender` 1–20，`age` 16–80。字段必填，学号唯一。CSV UTF-8，可带 BOM，表头为 `学号,姓名,学院,专业,年级,性别`，最多 200,000 字符，返回 `{imported,failed,errors:[{row,message}]}`；行号包含表头。

## 规则预警

PHQ 总分 <10 常规关注、10–14 适度、15–19 重点、>=20 优先联系；第 9 项非零直接优先人工复核。近 7 天至少 3 条睡眠 <5 小时提升到至少重点；最近心情 <=2 且压力 >=4 提升到至少适度。采用更高关注等级。

测评响应的 `riskLevel` 表示该次 PHQ 测评级别，包含第 9 项非零时的优先复核；`ruleRiskLevel` 表示该次提交结合近期打卡后得到的综合规则级别，随测评持久化，幂等重试返回原结果。相同 `requestId` 换用不同答案返回 409。工作台与学生档案顶部使用最新综合规则级别，自评结果及档案中的自评记录保留提交时的综合等级和 PHQ 总分；个人档案的支持提示也参考当前关注等级。活动预警保留已触发的较高等级，等级下降由人工复评处理。例如，总分 0 且第 9 项为 0 的自评为 `LOW`，若近 7 天有 3 条睡眠不足记录，`ruleRiskLevel` 为 `HIGH`。V2 旧记录当时未保存综合规则输入，升级时只能以原 `riskLevel` 作为兼容回填；V3 起的新提交具备完整语义。

测评答案及打卡 `mood/stress` 必须使用整数，不接受小数后静默截断。`mood/stress` 为 1–5，`sleepHours` 为 0–24 且最多一位小数；类型或精度不符直接返回 400，避免保存时转换改变规则边界。同一天再次保存更新原记录；完全相同的重试直接返回，只有备注变化时保存文字但不重复生成规则快照。

一名学生仅有一个未关闭个案。同学生的自评、打卡与个案写操作串行处理；等级提升或新的第 9 项非零自评会刷新活动个案并再次提醒，重复提交同一自评不会重复建个案或提醒。已经进入 `AWAITING_REASSESSMENT` 的个案遇到这些新信号会回到 `ACCEPTED`，须重新干预并转入复评后才能关闭。

`assignedTo` 保留首次受理人；后续干预记录分别保留实际作者。合法关闭要求当前为 `AWAITING_REASSESSMENT`，并提供 5–600 字符的人工复评与关闭说明；不强制新增学生自评。状态前置条件不符返回 409，关闭说明不合规返回 400。`CLOSED` 个案保留历史且不能继续新增干预。关闭不清除学生关注等级；后续有效的新自评或变更打卡重新评估，若仍为非 `LOW` 且无活动个案，会创建新个案。

随访记录的 `followUpStatus` 为 `NONE`（未安排）、`PLANNED`（当前计划）、`SUPERSEDED`（已被后续干预替代）或 `CANCELLED`（个案关闭时取消）。每次新增干预都将旧 `PLANNED` 标记为 `SUPERSEDED`，包括新干预未填写 `nextDate` 的情况；此时新记录为 `NONE`，不再有活动随访计划。工作台与进行中随访只统计 `PLANNED`，历史记录仍可查看。

当前学生演示账号有固定联系人；优先联系只发送最小关怀摘要，不发送答案、分数、打卡或诊断。辅导员读取或标记旧通知时仍按学生当前学院复核权限，学生转出后不可继续访问对应通知。全部“通知”都是本地站内记录，没有真实邮件、短信或外部发送。

## 模型输入

生活方式模式（9 特征）：`age` 16–80，`gender` Female/Male，`department` Science/Engineering/Arts/Medical/Business，`cgpa` 0–4，`sleepHours/studyHours/socialMediaHours` 0–24，`physicalActivity` 0–10080 分钟/周，`stressLevel` 0–10。所有特征必填。

小问卷模式：`age`、`gender`、`courseGroup` Engineering/Computing/Business/Other、`studyYear` 1–4、`cgpa` 区间中点、`married` No/Yes。实际输入应接近训练分布；数值合法不代表模型对超出样本分布的情况可靠。模型输入不写入个人档案，也不生成预警。

两套模型、特征模式、操作阈值和产物独立维护。`dataset` 省略时使用默认实验，不在两份数据之间混用训练对象。
