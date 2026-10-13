# 心晴校园 · Campus Mental Health

校园心理健康关怀与机器学习实验 Demo。提供学生档案、PHQ-9 自评、日常打卡、关注趋势、预警干预闭环，以及 Java Weka 多模型评估与交互推理。

## 项目构成

- **前端**：Vue 3、Vue Router、Element Plus、ECharts，包含工作端、学生端与联系人端。
- **后端**：Spring Boot 4、Spring Security，会话认证、CSRF 防护及角色/记录范围授权。
- **持久化**：H2 文件数据库、Flyway 迁移，保存测评、打卡、个案、随访及站内通知。
- **机器学习**：Weka Logistic、J48、RandomForest，附 ZeroR 基线、ROC、混淆矩阵和验证阈值比较。

## 环境要求

| 工具 | 要求 |
| --- | --- |
| JDK | 21；开发基准 Temurin 21.0.12.1 |
| Node.js / npm | Node >=24.15.0，npm >=11 |
| Python | >=3.10，用于统一启动脚本，无第三方 Python 依赖 |
| PowerShell | Windows 环境初始化/构建使用 7+ |
| Maven | Wrapper 固定 3.10.0，无需全局安装 |

依赖版本由 `backend/pom.xml`、`frontend/package-lock.json` 固定。数据库为内嵌 H2，无需安装数据库服务。

## 初始化与启动

```powershell
# 首次初始化：指定本机 JDK 21 根目录。
pwsh -NoProfile -File scripts/setup.ps1 -JdkHome "D:\Tools\jdk-21"

# 准备两份数据；已有大数据压缩包可使用 -LifestyleArchive 指定。
pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle

# 启动前后端，并打开浏览器。
python start.py
```

访问 **http://127.0.0.1:5173**。启动脚本准备缺少的前端依赖，按需构建后端，集中管理子进程和日志；首次后端启动会初始化演示档案并训练可用的数据集。

```powershell
python start.py --stop                 # 停止本脚本记录的服务
python start.py --restart              # 重启 Demo
python start.py --no-browser           # 不自动打开浏览器
python start.py --backend-port 8081 --frontend-port 5174
```

在启动终端按 Ctrl+C 也可同时停止前后端。开发时仍可分别运行 `scripts/dev.ps1 -Service backend` / `frontend`。

## 演示账号

四个账号的初始密码均为 **Campus2026!**，可通过后端环境变量 `DEMO_PASSWORD` 修改。

| 账号 | 身份 | 数据范围 |
| --- | --- | --- |
| center | 心理中心 | 全校档案、个案与模型训练 |
| counselor | 学院辅导员 | 计算机学院档案/个案、模型实验查看 |
| student | 学生 | 林同学的个人档案、自评与打卡 |
| contact | 联系人 | 本人收件箱中的必要关怀提醒 |

自评与打卡按规则生成关注等级；工作人员可完成受理、干预、随访、人工复评和关闭。一个学生仅有一个活动个案，待复评时出现风险升级或新的优先复核信号须重新干预。随访区分当前计划与历史，辅导员通知按学生当前学院授权。全部通知为站内演示记录。默认仅监听本机回环地址，`DEMO_ENABLED=false` 可停用演示账号和数据播种。

## 数据与实验

| 数据集 | 性质与规模 | 实验协议 |
| --- | --- | --- |
| Student Mental health | 101 条公开匿名自报问卷，CC0 | 6 特征，分层 5 折交叉验证；Logistic / J48 |
| Student Depression & Lifestyle | 100,000 条原作者生成的合成数据，MIT | 9 特征，分层 60/20/20；增加 RandomForest |

模型实验室可切换两份数据。各自处理、训练与保存，不混合样本，也不把平台生成的测评历史作为监督训练数据。大数据实验只用验证集选择阈值，再在独立测试集上评估；小样本指标不直接与大数据测试指标作泛化能力排名。

模型预测数据集标签，四级预警依据自评与打卡规则；两者用途独立，均不替代诊断。详细特征、参数、评估与限制见 [机器学习说明](docs/machine-learning.md)。

## 工程目录

```text
backend/          Java 服务、Maven Wrapper、数据库迁移与测试
frontend/         Vue 页面、共享组件、接口与前端测试
scripts/          环境初始化、数据准备、构建与编辑器配置
config/vscode/    本机工作区生成模板
docs/             架构、API、数据、实验与运行说明
start.py          统一启动入口
```

本地数据在 `data/downloads/`，模型与评估在 `models/artifacts/{survey,lifestyle}/`，业务数据库在 `backend/runtime/`，当前运行日志在 `.tools/logs/`。这些产物被 Git 忽略；仓库 `.vscode` 只保留扩展推荐。

## 构建与文档

```powershell
pwsh -NoProfile -File scripts/check.ps1
```

执行后端测试与格式检查，以及前端 lint、交互测试、格式检查和生产构建。构建产物位于 `backend/target/`、`frontend/dist/`。

- [演示路线](docs/demo-guide.md)
- [开发环境](docs/development-environment.md) · [运行与构建](docs/development.md)
- [系统架构](docs/architecture.md) · [REST API](docs/api/README.md) · [数据库](docs/database/README.md)
- [机器学习实验](docs/machine-learning.md) · [协作规则](CONTRIBUTING.md)
