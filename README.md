# 心晴校园 · Campus Mental Health

校园心理健康关怀 Demo，包含学生自评与打卡、档案与趋势、规则预警、干预随访、角色权限、站内通知，以及 Java Weka 模型实验室。界面使用 Vue 3、Element Plus 和 ECharts；后端使用 Spring Boot、H2 和 Flyway，数据保存后可跨刷新与重启保留。

开发目录可任意命名，不依赖作者的盘符、Class7 父目录、课程文档或 SSH 身份。

## 运行环境

| 工具 | 基准版本 | 说明 |
| --- | --- | --- |
| JDK | Temurin 21.0.12.1 | 必须是 JDK 21，需包含 javac |
| Node.js / npm | 24.15.0 / 11.12.1 | Node >=24.15.0、npm >=11 |
| PowerShell | 7.5.6 | Windows 脚本需要 pwsh 7+ |
| Maven | 3.10.0 | 官方 Wrapper 固定版本，无需全局安装 |
| Git | 2.52.0 | 克隆与个人版本管理 |
| VS Code | 1.141.0 | 可选，仓库只提供扩展推荐 |

后端 Spring Boot 4.1.1、Weka 3.8.6；前端版本由 `package-lock.json` 锁定。无需安装 MySQL、Python 或独立机器学习服务。完整环境复现、编辑器配置和安全卸载见 [开发环境指南](docs/development-environment.md)。

## 克隆与初始化

```powershell
git clone https://github.com/Algrex/campus-mental-health.git
cd campus-mental-health

# 换成你自己的 JDK 根目录，不是 bin。
pwsh -NoProfile -File scripts/setup.ps1 -JdkHome "D:\Tools\jdk-21"

# 下载固定版本的小问卷与十万条生活方式数据；下载内容不进 Git。
pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle
```

若使用基准 Windows x64 JDK ZIP，初始化命令可替换为：

```powershell
pwsh -NoProfile -File scripts/setup.ps1 -JdkArchive "D:\Downloads\OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip" -StrictVersions
```

已下载大数据压缩包时：

```powershell
pwsh -NoProfile -File scripts/download-demo-data.ps1 -LifestyleArchive "D:\Downloads\archive.zip"
```

下载脚本核对 CSV SHA256，不覆盖未经确认的上游新版本。无网络时业务 Demo 仍可启动：缺少小问卷则生成 36 份虚构档案；缺少十万条数据则用小问卷运行逻辑回归与决策树；两者都缺少时可使用业务页面，模型页面提供数据准备入口。

初始化只准备项目本地工具与依赖，不改系统 PATH/JAVA_HOME；在仓库外生成 `<克隆目录名>.local.code-workspace`。项目 `.vscode` 仅有扩展推荐，不携带个人路径、端口、调试配置。

## 启动 Demo

在仓库根目录开两个终端：

```powershell
pwsh -NoProfile -File scripts/dev.ps1 -Service backend
```

```powershell
pwsh -NoProfile -File scripts/dev.ps1 -Service frontend
```

访问 **http://127.0.0.1:5173**。后端默认 **127.0.0.1:8080**。首次启动执行数据库迁移、演示数据初始化和模型训练；十万条实验需要额外等待，进度在后端终端显示。后续启动保留业务数据，模型默认重新训练，避免加载来源不明的模型文件。

| 身份 | 账号 | 初始密码 | 数据范围 |
| --- | --- | --- | --- |
| 心理中心 | center | Campus2026! | 全校档案、预警、模型训练 |
| 学院辅导员 | counselor | Campus2026! | 计算机学院档案与个案，可查看模型实验 |
| 学生 | student | Campus2026! | 林同学的自评、打卡和个人档案 |
| 联系人 | contact | Campus2026! | 指定收件人的最小关怀提醒 |

登录页可选择身份并填入演示账号。账号密码仅面向默认回环地址的本地演示；可通过后端终端 `DEMO_PASSWORD` 修改，`DEMO_ENABLED=false` 停用全部演示账号和数据初始化。不是面向公网的账号注册或真实短信平台。

[演示流程](docs/demo-guide.md)介绍如何展示从自评到预警、受理、干预、复评和关闭的完整闭环。

## 机器学习实验

优先读取 `data/downloads/student_lifestyle_100k.csv`：100,000 条原作者生成的合成生活方式记录，MIT 许可。9 个特征预测 Depression 标签，移除 Student_ID 和目标字段。实现 Weka **Logistic、J48、RandomForest**，另有 ZeroR 多数类基线。

- 同一分层 60% 训练 / 20% 验证 / 20% 测试；种子 42，开发集再划分种子 43。
- 缺失值填充仅拟合训练数据；逻辑回归编码与标准化包含在模型内部。
- 展示统一阈值 0.5 的测试准确率、精确率、召回率、F1、AUC 和混淆矩阵。
- 只用验证集选择 F1 最优阈值，再在独立测试集评估，避免用测试数据调参。
- 模型与 JSON 评估、训练/验证/测试 ARFF 保存到忽略目录 `models/artifacts/`。

只有 101 条匿名问卷时使用分层 5 折交叉验证并进行全量重训，不添加随机森林。两类数据不混合，也不把平台生成的测评历史当训练样本。详见 [机器学习说明](docs/machine-learning.md)。

模型实验与四级规则预警分开：模型预测公开数据标签，规则依据实际提交的自评与打卡触发关怀流程。结果不替代诊断。十万条合成数据的指标不能证明真实校园中的预测效果。

## 构建与检查

修改集中完成后执行：

```powershell
pwsh -NoProfile -File scripts/check.ps1
```

检查包括后端权限与流程测试、Weka 训练/推理测试、Google Java 格式检查，以及前端 lint、交互测试、格式检查与生产构建。产物：`backend/target/campus-mental-health-backend-0.0.1-SNAPSHOT.jar`、`frontend/dist/`。

独立命令、JAR/preview 运行、端口调整、数据库备份与 Linux/macOS 命令见 [运行构建指南](docs/development.md)。

## 目录与持久化

```text
backend/              后端、Maven Wrapper、迁移与测试
frontend/             Vue 页面、共享组件、接口和锁文件
scripts/              初始化、数据准备、工作区生成、运行与检查
config/vscode/        外部工作区的生成模板
docs/                 环境、演示、模型、接口、架构和数据库说明
data/downloads/       原始数据集（忽略 Git）
models/artifacts/     本地模型与评估文件（忽略 Git）
backend/runtime/      H2 业务数据库（忽略 Git）
.tools/               本地工具与缓存（忽略 Git）
```

原始公开数据、虚构演示身份、演示生成历史与用户操作记录分别注明来源。101 份演示档案与十万条建模记录是独立数据，不关联成同一学生。

课程分工、个人报告和本机安装记录放在仓库外，不影响克隆运行。仓库协作约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。
