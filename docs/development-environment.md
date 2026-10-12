# 开发环境

## 版本基准

| 组件 | 基准/要求 |
| --- | --- |
| Temurin JDK | 21.0.12.1；支持其他 JDK 21 补丁版本 |
| Node.js / npm | 24.15.0 / 11.12.1；Node >=24.15.0、npm >=11 |
| Python | >=3.10，仅启动脚本使用标准库 |
| PowerShell | 7.5.6；Windows 辅助脚本需要 7+ |
| Maven | Wrapper 固定 3.10.0 与 SHA256 |
| Git / VS Code | 基准 2.52.0 / 1.141.0；编辑器可选 |

后端 Spring Boot 4.1.1、Weka 3.8.6，由 Maven 管理；前端由锁文件固定。无需独立数据库或 Python 机器学习环境。

获取组件：[Temurin](https://adoptium.net/temurin/releases/?version=21)、[Node.js](https://nodejs.org/en/download)、[Python](https://www.python.org/downloads/)、[PowerShell](https://learn.microsoft.com/en-us/powershell/scripting/install/install-powershell-on-windows)、[Git](https://git-scm.com/downloads)、[VS Code](https://code.visualstudio.com/download)。完整版本清单在 `dev-environment.json`。

## Windows 初始化

```powershell
pwsh -NoProfile -File scripts/setup.ps1 -JdkHome "D:\Tools\jdk-21"
# 或使用清单中的基准 ZIP：
pwsh -NoProfile -File scripts/setup.ps1 -JdkArchive "D:\Downloads\OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip" -StrictVersions

pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle
python start.py
```

初始化记录 JDK 选择，准备固定 Maven、前端依赖和外部 VS Code 工作区，不修改系统 PATH/JAVA_HOME。`-StrictVersions` 检查 JDK、Node、npm 基准；常规模式允许满足最低要求的工具版本。下载脚本核对两份 CSV 哈希，支持 `-LifestyleArchive` 使用本地 ZIP。

## 编辑器

仓库 `.vscode/extensions.json` 推荐 Java、Vue、ESLint、Prettier 扩展。`setup.ps1 -InstallEditorExtensions` 可安装清单版本，也可独立运行 `scripts/install-editor-extensions.ps1`。

```powershell
pwsh -NoProfile -File scripts/configure-workspace.ps1 -JdkHome "D:\Tools\jdk-21"
# 端口调整：增加 -BackendPort 8081 -FrontendPort 5174
```

生成的外部工作区包含 JDK、终端、任务和调试配置；共享仓库不保存机器路径。扩展版本及生成模板在清单和 `config/vscode` 中。

## 本地产物与清理

| 目录 | 用途 |
| --- | --- |
| .tools/jdk、maven-user、m2、npm-cache | 项目便携工具与依赖缓存 |
| .tools/logs | 当前启动/构建日志 |
| .tools/environment-state | 编辑器扩展安装前后清单 |
| .tools/archive | 可恢复的历史中间文件归档 |
| data/downloads | 原始公开数据与来源记录 |
| models/artifacts/survey、lifestyle | 两份独立实验的指标、ARFF 和模型 |
| backend/runtime | 业务数据库 |
| backend/target、frontend/dist、frontend/node_modules | 构建产物与前端依赖 |

项目结束时先停止服务，保留需提交的源码、课程报告、实验评估和数据库。缓存、下载数据和模型可重新生成；文件数据库需明确备份后再处理。编辑器扩展按需逐个卸载，参考安装前后清单；外部共享 JDK、原有 Node/Git、SSH 密钥不属于项目缓存。

本次完善没有新增全局工具或系统服务。便携工具、模型与下载数据均位于项目忽略目录。各开发者使用自己的 Git 身份与认证配置。

## 其他平台

准备 JDK 21、Node >=24.15、npm >=11、Python >=3.10，执行 `python3 start.py`。首次构建通过 `backend/mvnw` 完成；Windows PowerShell 初始化脚本不用于 Linux/macOS。手动命令与配置项见 [运行构建指南](development.md)。Windows 已实机验证，其他平台以实际执行结果为准。
