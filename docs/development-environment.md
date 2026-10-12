# 开发环境复现指南

本指南只依赖克隆出的仓库内容。目录盘符、用户名、父目录名称和个人 SSH 密钥不参与共享配置。

## 1. 复现范围与基准

`dev-environment.json` 是工具和编辑器扩展的基准清单，首次验证日期为 2026-10-12。基准环境为 Windows x64；后端与前端也提供 Linux/macOS 的独立命令，但 Windows 初始化与工作区脚本不适用于其他平台。

| 工具 | 基准版本 | 必要性 |
| --- | --- | --- |
| Temurin JDK | 21.0.12.1 | 必需；必须有 `javac`，不是只装 JRE |
| Node.js | 24.15.0 | 必需；同时提供 npm 11.12.1 |
| npm | 11.12.1 | 必需；使用锁文件安装前端依赖 |
| PowerShell | 7.5.6 | Windows 脚本必需；最低 7.0，使用 `pwsh` |
| Git | 2.52.0 | 克隆与协作需要；不要求作者的提交身份 |
| Maven | 3.10.0 | Wrapper 自动准备，分发包 SHA256 已固定 |
| VS Code | 1.141.0 | 可选；可以用其他 IDE 或命令行 |

复现基准时使用清单中的 JDK、Node/npm 和编辑器版本。`-StrictVersions` 会严格校验 JDK、Node、npm；普通初始化允许 JDK 21 的其他补丁版本、Node >=24.15.0、npm >=11，并提示差异。Git、PowerShell、IDE 的基准用于记录，脚本不强制它们的补丁版本。

前端项目锁文件固定直接与传递依赖；后端使用固定 Spring Boot 4.1.1、Weka 3.8.6（Java 内训练/推理，无需额外 Python）及 Maven 插件配置。Maven 依赖通过 Maven Central 与 BOM 解析。不同操作系统和工具补丁可能生成不同的二进制产物；本指南复现的是开发与构建配置，不承诺逐字节相同的产物。

## 2. 准备基础工具

官方获取渠道：

- [Git for Windows](https://git-scm.com/downloads/win)：安装后新终端可调用 `git`。
- [PowerShell 7 安装说明](https://learn.microsoft.com/en-us/powershell/scripting/install/install-powershell-on-windows)：确认可调用 `pwsh`，不是旧版 `powershell.exe`。
- [Node.js 下载](https://nodejs.org/en/download)；基准 Windows x64 ZIP 为 [Node.js 24.15.0](https://nodejs.org/dist/v24.15.0/node-v24.15.0-win-x64.zip)，可解压到自己的工具目录并让当前终端找到 `node`、`npm.cmd`。
- [Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21)：按操作系统和 CPU 架构选择 JDK。Windows 基准 ZIP 的准确地址和校验值已写入 `dev-environment.json`。
- [VS Code](https://code.visualstudio.com/download)：仅编辑器集成需要。选择与扩展兼容的版本；使用基准版本可减少差异。

检查：

```powershell
git --version
pwsh --version
node --version
npm.cmd --version
```

官方 Node 24.15.0 发布元数据记录其捆绑 npm 11.12.1。若命令实际来自另一套 Node/npm，先核对 `Get-Command node,npm.cmd`，避免只看下载文件名。

JDK 可以在仓库外安装，也可以把基准 ZIP 交给初始化脚本。脚本不会安装 Node、Git、PowerShell 或 VS Code 主程序，不会修改系统环境变量；这些工具应先准备好。

## 3. 克隆与初始化

```powershell
git clone https://github.com/Algrex/campus-mental-health.git
cd campus-mental-health
```

文件夹可改名，也可包含空格。不要复制作者的 `.git/config`、SSH 密钥、父目录配置或 `.tools` 中的绝对路径文件。

JDK 已安装时：

```powershell
pwsh -NoProfile -File scripts/setup.ps1 -JdkHome "D:\Dev Tools\jdk-21"
```

使用基准 ZIP 时：

```powershell
pwsh -NoProfile -File scripts/setup.ps1 -JdkArchive "D:\Downloads\OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip" -StrictVersions
```

基准 ZIP 的 SHA256：

```text
f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e
```

其他 JDK 21 的包不能冒用该校验值；解压或安装后用 `-JdkHome` 指定即可。路径指向 JDK 根目录，不是 `bin`。

初始化依次完成：

1. 识别或解压 JDK、校验 Java/Node/npm 版本。
2. 把本次 JDK 路径写入忽略文件 `.tools/environment.local.json`。
3. 读取官方 Wrapper 的 Maven URL 和 SHA256，下载校验后放入项目缓存；已准备的缓存可复用。
4. 使用 `npm ci` 和锁文件安装前端依赖，显式关闭个人 npm 离线选项。
5. 为当前克隆生成仓库外的 VS Code 工作区和项目本地 Maven 设置。

首次需要联网、可写工具目录和足够磁盘空间。脚本不重写用户的全局 Maven/npm 配置。

参数：

| 参数 | 用途 |
| --- | --- |
| `-JdkHome` / `-JdkArchive` | 二选一；不指定时尝试本地选择、JAVA_HOME、项目便携 JDK、PATH |
| `-StrictVersions` | 对 JDK、Node、npm 强制基准版本 |
| `-WorkspacePath` | 指定仓库外的工作区输出文件 |
| `-SkipFrontendInstall` | 已安装前端依赖时跳过；不适合首次完整初始化 |
| `-SkipEditorWorkspace` | 只准备命令行环境 |
| `-InstallEditorExtensions` | 显式安装记录中的 8 个 VS Code 扩展 |

## 4. 编辑器扩展与本机配置

项目 `.vscode/extensions.json` 只推荐扩展，不强制安装和版本。完整基准：

| 扩展 | 基准版本 |
| --- | --- |
| `redhat.java` | 1.56.0 |
| `vscjava.vscode-java-debug` | 0.59.0 |
| `vscjava.vscode-java-test` | 0.46.0 |
| `vscjava.vscode-maven` | 0.46.0 |
| `vscjava.vscode-java-dependency` | 0.27.6 |
| `vue.volar` | 3.3.13 |
| `dbaeumer.vscode-eslint` | 3.0.34 |
| `esbenp.prettier-vscode` | 12.4.0 |

可以在初始化时加 `-InstallEditorExtensions`，或运行：

```powershell
pwsh -NoProfile -File scripts/install-editor-extensions.ps1
```

扩展安装在 VS Code 当前用户的扩展目录，是用户级组件；脚本按 `ID@版本` 请求安装，记录安装前后清单。既有版本是否被复用或更换以安装后清单为准。若希望保持其他项目的扩展组合，可以跳过此步骤、使用自己的 VS Code Profile，或仅安装所需扩展。

Windows 默认扩展位置是 `%USERPROFILE%\.vscode\extensions`；实际目录受 VS Code 启动选项影响。Red Hat Java 的平台包可带内部运行时，由该扩展管理；本项目仍使用指定的 JDK 21。

工作区默认生成在仓库父目录，文件名为 `<克隆文件夹名>.local.code-workspace`。用 VS Code 打开该文件；不必复制任何父目录 `.vscode`。

重新选择 JDK 或调整输出位置：

```powershell
pwsh -NoProfile -File scripts/configure-workspace.ps1 -JdkHome "E:\Tools\jdk-21" -OutputPath "E:\Workspaces\Campus.local.code-workspace"
```

端口冲突时，在生成命令中增加 `-BackendPort 8081 -FrontendPort 5174`，任务、代理和调试地址会同步调整，改动只写入你的外部工作区。

生成器拒绝把包含机器路径的工作区写入仓库。实际路径从当前脚本位置计算，任务使用固定的逻辑工作区名字，不依赖克隆文件夹名。更新已有输出前会在 `.tools/workspace-backups` 备份；不属于 `Campus:` 的任务、调试配置、额外文件夹和其他设置尽量保留。`Campus:` 生成项会按模板更新，手动改过这些生成项时先保留备份。

工作区中有 JDK、Java 语言服务器、终端环境、Maven 缓存、格式化、启动/检查任务和 Java/Chrome 调试入口。扩展安装后重载窗口，并新建终端。图形语言服务器导入、断点和浏览器调试需在编辑器内使用，不属于命令行检查结果。

直接打开代码文件夹时，仅显示扩展推荐。可以自行配置 IDE；命令行脚本仍可读取本地 JDK 选择运行。不要把自己的 settings.json、launch.json、tasks.json 或工作区文件强行加入 Git。

## 5. 本地目录与选择优先级

| 位置（相对仓库） | 内容 | Git |
| --- | --- | --- |
| `.tools/environment.local.json` | 本机 JDK 选择 | 忽略 |
| `.tools/jdk/` | 从基准 ZIP 解压的 JDK | 忽略 |
| `.tools/maven-user/wrapper/` | 固定 Maven 分发缓存 | 忽略 |
| `.tools/maven-user/settings.xml` | IDE 使用的本地 Maven 配置 | 忽略 |
| `.tools/m2/` | 脚本使用的 Maven 依赖缓存 | 忽略 |
| `.tools/npm-cache/` | 初始化使用的 npm 缓存 | 忽略 |
| `frontend/node_modules/` | 前端依赖 | 忽略 |
| `backend/target/`、`frontend/dist/` | 构建结果 | 忽略 |
| `data/downloads/` | 原始公开 CSV、ZIP、来源与许可记录 | 忽略 |
| `models/artifacts/` | 模型、评估 JSON、ARFF 数据划分 | 忽略 |
| `backend/runtime/` | 本地文件数据库 | 忽略，删除前备份 |

Java 选择顺序是显式 `-JdkHome`、本地选择文件、当前 JAVA_HOME、项目便携 JDK、PATH 中的 javac。识别后要求 Java 主版本 21。若已有 JAVA_HOME 指向 17 或其他不适用版本，用 `setup.ps1 -JdkHome` 明确选择，不必改变系统全局设置。

只使用 JDK 已有路径时不会复制或卸载该 JDK。ZIP 解压的 JDK 属于本项目便携工具。用户级编辑器扩展、系统工具和项目缓存分别管理。

## 6. Git 与个人身份

HTTPS 克隆公开仓库无需复制作者的 SSH 配置。需要 SSH 时，先使用自己的 GitHub 密钥配置认证，再将远程改为对应 SSH 地址。

```powershell
git config --local user.name "你的名字"
git config --local user.email "你的提交邮箱"
git remote -v
```

Git 本地配置不会随 clone 复制。不要使用文档中的作者身份替其他人提交，也不要把私人密钥放入项目。

## 7. 课程或项目结束后的清理

先备份源码、报告、评估/模型和需保留的数据库，停止本项目的任务与调试会话，然后：

- 便携 JDK、Maven/npm 缓存、node_modules 和构建产物可在确认不再使用后清理；它们能重新生成或下载。先删除或更新仓库外工作区的旧 JDK 路径。
- `.tools/environment.local.json` 和仓库外工作区文件只属于本次克隆，可以删除；若其中后来加入其他项目设置，先保留这些内容。
- 外部 `-JdkHome` 指向的共享 JDK不属于本项目，不能因清理缓存就一起删除。
- 扩展可在 VS Code 中逐个卸载，或执行 `code --uninstall-extension <扩展ID>`；先核对安装前后清单和其他项目是否仍需要。不要清空整个用户 `.vscode` 或 VS Code 用户数据目录。
- 原始下载数据和模型属于本项目，可在保留来源/评估记录后删除 `data/downloads` 与 `models/artifacts`；下载脚本和训练可恢复。不要删除自己后来添加到 data/private 的唯一资料。
- 本地数据库不能当缓存直接删除；SSH 密钥、原有 Node/Git/编辑器也不属于项目清理范围。

使用 PowerShell 清理时先核对绝对路径，采用 `Remove-Item -LiteralPath`，避免拼接跨 Shell 的删除命令。项目初始化不会创建全局 JAVA_HOME/PATH，无需系统环境回滚。

## 8. 后续命令

初始化后执行 `pwsh -NoProfile -File scripts/check.ps1`。启动、独立构建、JAR 与前端静态产物使用、Linux/macOS 和故障处理见 [运行与构建指南](development.md)。

## 9. 完整 Demo 数据与运行

初始化后，运行 `pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle` 准备固定哈希的数据。也可使用 `-LifestyleArchive` 指定自己的 ZIP。下载和模型都位于仓库本地忽略目录，不安装数据库服务、Python 环境或机器学习后台。

两个终端分别运行 `scripts/dev.ps1 -Service backend` 和 `scripts/dev.ps1 -Service frontend`；访问 http://127.0.0.1:5173。四个演示账号及完整流程见 [README](../README.md) 与 [演示路线](demo-guide.md)。模型训练进度在后端终端出现；训练完成后才能看到完整实验。
