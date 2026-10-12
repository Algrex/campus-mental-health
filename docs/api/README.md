# REST 接口

除健康、系统信息和会话获取外，均需登录。写请求发送 `X-XSRF-TOKEN`（由会话接口返回），带同源 Cookie。时间由本地后端记录，JSON 日期 `YYYY-MM-DD`、日期时间 ISO 字符串；不包含时区偏移的业务时间用于本机演示。

错误一般为 `{ "message": "可读原因" }`；400 输入/状态无效，401 未登录，403 角色或记录越权，409 重复键/并发状态/模型未准备。框架解析失败保留 HTTP 状态，前端不会把失败显示为成功。

| 方法与路径（/api/v1 前缀） | 权限 | 用途 |
| --- | --- | --- |
| GET /system/info | 匿名 | name、stage=DEMO、version、功能名称 |
| GET /auth/session | 匿名 | csrfToken、user（匿名为空） |
| POST /auth/login | 匿名 + CSRF | URL 编码 username/password，建立会话 |
| POST /auth/logout | 已登录 + CSRF | 销毁会话 |
| GET /dashboard | 中心/辅导员 | 范围内统计、趋势、等级分布、优先个案、随访 |
| GET /students?q=&college=&level= | 中心/辅导员 | 范围内学生列表，前端每页 12 条 |
| GET /students/{uuid} | 中心/本学院辅导员/本人 | 基础档案、测评、打卡、趋势；学生不返回工作人员预警记录 |
| POST /students | 中心/辅导员 | 新增档案，辅导员只能本学院 |
| PUT /students/{uuid} | 中心/本学院辅导员 | 修改档案 |
| POST /students/import | 中心/辅导员 | `{csv}`，表头固定、1–500 行、逐行成功/错误 |
| GET /profile | 学生本人 | 个人健康档案 |
| POST /assessments | 学生本人 | `{answers:[9 个 0–3 整数],requestId}`，幂等提交 |
| POST /check-ins | 学生本人 | `{mood,sleepHours,stress,note}`，当天插入或更新 |
| GET /alerts?status= | 中心/辅导员 | 范围内预警，优先联系/重点关注排序 |
| GET /alerts/{uuid} | 中心/本学院辅导员 | 个案、档案、干预时间线 |
| POST /alerts/{uuid}/accept | 中心/本学院辅导员 | PENDING → ACCEPTED，body `{}` |
| POST /alerts/{uuid}/interventions | 中心/本学院辅导员 | `{kind,content,nextDate}`；受理后进入 INTERVENING |
| POST /alerts/{uuid}/review | 中心/本学院辅导员 | INTERVENING → AWAITING_REASSESSMENT |
| POST /alerts/{uuid}/close | 中心/本学院辅导员 | `{reason}` 5–600 字符；复评后 CLOSED |
| GET /follow-ups | 中心/辅导员 | 范围内干预与下次随访记录 |
| GET /notifications | 四类已登录用户 | 只返回自己账号收件箱 |
| POST /notifications/{uuid}/read | 收件人本人 | 标记已读 |
| GET /ml/summary | 中心/辅导员 | 来源、特征、数据划分、参数、指标、ROC、模型解释 |
| POST /ml/train | 中心 | 重新读取当前 CSV，执行训练与评估，body `{}` |
| POST /ml/predict | 中心/辅导员 | 手工实验特征，返回模型概率与验证阈值后的标签 |

`GET /actuator/health` 匿名，位于上述前缀之外，仅返回健康状态。

## 档案与导入

档案 DTO：`studentCode` 1–64、`displayName/college/major/contactName` 1–100 字符；`yearName` 1–30、`gender` 1–20，`age` 16–80。字段必填，学号唯一。CSV UTF-8，可带 BOM，表头为 `学号,姓名,学院,专业,年级,性别`，最多 200,000 字符，返回 `{imported,failed,errors:[{row,message}]}`；行号包含表头。

## 规则预警

PHQ 总分 <10 常规关注、10–14 适度、15–19 重点、>=20 优先联系；第 9 项非零直接优先人工复核。近 7 天至少 3 条睡眠 <5 小时提升到至少重点；最近心情 <=2 且压力 >=4 提升到至少适度。采用更高关注等级。

一名学生已有未关闭个案时不重复建个案，只在等级提升时再次提醒。当前学生演示账号有固定联系人；优先联系只发送最小关怀摘要，不发送答案、分数、打卡或诊断。全部“通知”都是本地站内记录，没有真实邮件、短信或外部发送。

## 模型输入

生活方式模式（9 特征）：`age` 16–80，`gender` Female/Male，`department` Science/Engineering/Arts/Medical/Business，`cgpa` 0–4，`sleepHours/studyHours/socialMediaHours` 0–24，`physicalActivity` 0–10080 分钟/周，`stressLevel` 0–10。所有特征必填。

小问卷模式：`age`、`gender`、`courseGroup` Engineering/Computing/Business/Other、`studyYear` 1–4、`cgpa` 区间中点、`married` No/Yes。实际输入应接近训练分布；数值合法不代表模型对超出样本分布的情况可靠。模型输入不写入个人档案，也不生成预警。
