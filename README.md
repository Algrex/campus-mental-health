# Campus Mental Health

校园心理健康监测与异常预警系统。仓库提供 Java 后端、Vue 前端、关系数据库基础、机器学习依赖、安全配置、构建检查和开发工具入口。目前是工程骨架：账号、测评、风险模型、预警与干预业务仍待实现。

克隆后的目录可以任意命名、放在任意可写位置；运行不依赖作者的电脑路径、父目录、课程资料或 SSH 配置。

## 环境与版本

| 工具 | 已验证基准 | 要求与获取方式 |
| --- | --- | --- |
| JDK | Temurin 21.0.12.1 | JDK 21；可指定自己的安装目录，也可使用基准 ZIP |
| Node.js / npm | 24.15.0 / 11.12.1 | Node >=24.15.0、npm >=11；官方 Node 基准包自带相应 npm |
| PowerShell | 7.5.6 | Windows 辅助脚本需要 PowerShell 7；命令名 `pwsh` |
| Git | 2.52.0 | 用于克隆与协作，各人使用自己的 Git 身份 |
| Maven | 3.10.0 | 官方 Wrapper 固定版本和 SHA256，无需全局安装 |
| VS Code | 1.141.0 | 可选；8 个开发扩展的基准版本记录在清单中 |

完整工具清单见 [dev-environment.json](dev-environment.json)。后端依赖版本由 `backend/pom.xml` 和 Spring Boot BOM 管理；前端由 `frontend/package-lock.json` 锁定。默认 H2 嵌入式数据库，无需另外安装数据库服务。

## 克隆后快速开始（Windows）

先准备 Git、PowerShell 7、Node.js 24.15.0，以及 JDK 21。详细下载链接、校验和可选编辑器安装见 [开发环境复现指南](docs/development-environment.md)。

```powershell
git clone https://github.com/Algrex/campus-mental-health.git
cd campus-mental-health

# 替换为你自己的 JDK 根目录，不是 bin 子目录。
pwsh -NoProfile -File scripts/setup.ps1 -JdkHome "D:\Tools\jdk-21"

# 初始化后，验证后端与前端。
pwsh -NoProfile -File scripts/check.ps1
```

如果下载的是清单中的 Windows x64 JDK ZIP，可以用以下命令替代上面的初始化命令；脚本会验证校验值、解压到本仓库的忽略目录：

```powershell
pwsh -NoProfile -File scripts/setup.ps1 -JdkArchive "D:\Downloads\OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip" -StrictVersions
```

初始化会记录本地 JDK 选择、准备 Maven、按锁文件安装前端依赖，并在**仓库外**生成 `<克隆文件夹名>.local.code-workspace`。它不会修改系统 PATH/JAVA_HOME，也不会自动安装 VS Code 扩展；需要扩展时添加 `-InstallEditorExtensions`。

## 运行与构建

在仓库根目录的两个终端分别运行：

```powershell
pwsh -NoProfile -File scripts/dev.ps1 -Service backend
```

```powershell
pwsh -NoProfile -File scripts/dev.ps1 -Service frontend
```

前端：`http://127.0.0.1:5173`；健康检查：`http://127.0.0.1:8080/actuator/health`；工程信息：`http://127.0.0.1:8080/api/v1/system/info`。前端开发代理默认连接本机 8080 后端。

`pwsh -NoProfile -File scripts/check.ps1` 执行后端集成测试与 Google Java 格式检查，以及前端 lint、测试、格式检查和生产构建。产物分别位于 `backend/target/` 和 `frontend/dist/`。

独立命令、JAR 运行、数据库位置、端口配置、Linux/macOS 命令及常见问题见 [运行与构建指南](docs/development.md)。

## 编辑器配置原则

仓库 `.vscode` **仅有 extensions.json 扩展推荐**，不会覆盖 JDK 路径、终端环境、格式化设置、端口或调试配置。`config/vscode/workspace-template.json` 只是生成器使用的模板，不会自动成为你的编辑器设置。

初始化生成的工作区文件包含这次克隆的实际路径、任务和调试入口。用 VS Code 打开它；扩展安装后重载窗口并新建终端。也可以直接打开仓库并自行配置编辑器，命令行构建不需要 VS Code。

## 工程目录

```text
backend/              Spring Boot 后端、官方 Maven Wrapper、迁移与测试
frontend/             JavaScript / Vue 前端与锁文件
config/vscode/        可移植的工作区生成模板
scripts/              初始化、本机工作区生成、启动、检查与格式化
.github/workflows/    GitHub Actions 构建检查
docs/                 环境复现、运行构建、架构、接口、数据库与测试
data/                匿名样例与本地私有数据位置
models/               本地可信模型产物位置
dev-environment.json  工具与编辑器扩展基准
AGENTS.md             仓库开发约定
```

课程分工、个人报告模板、课程排期和作者的本机安装记录在仓库之外，不作为项目运行依赖。`.tools`、依赖缓存、运行数据库和私有资料不加入 Git。

## 功能与交付边界

仅健康与工程信息允许匿名 GET，其他业务路径默认拒绝访问；没有演示账号。前端业务页面明确标为待开发，数据库只有学生基础表，Weka 依赖不代表已训练模型。

前端开发服务器和 `vite preview` 用于开发或预览。后端 JAR 与前端静态产物可分别运行；正式静态托管、登录权限及真实通知渠道需后续实现和配置。

协作规则见 [CONTRIBUTING.md](CONTRIBUTING.md)，实现前查阅 [架构](docs/architecture.md)、[接口](docs/api/README.md) 与 [数据库说明](docs/database/README.md)。
