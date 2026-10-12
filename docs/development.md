# 运行、构建与故障处理

本文命令从仓库根目录开始，不依赖仓库父目录。首次使用先完成 [环境初始化](development-environment.md)。

## Windows 日常开发

启动后端：

```powershell
pwsh -NoProfile -File scripts/dev.ps1 -Service backend
```

另开终端启动前端：

```powershell
pwsh -NoProfile -File scripts/dev.ps1 -Service frontend
```

默认监听 `127.0.0.1`，后端 8080、前端 5173。停止时在对应终端按 Ctrl+C；Windows 批处理询问是否终止时确认终止。不要结束其他项目的 Java/Node 进程。

用浏览器访问前端或用 PowerShell 检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:5173/api/v1/system/info
```

健康状态正常返回 UP；工程信息返回 SCAFFOLD / PLANNED。这不是业务风险评估。匿名访问尚未开放的业务路径返回 401 是当前安全设计，没有可登录账号。

## 统一检查与构建

```powershell
pwsh -NoProfile -File scripts/check.ps1
```

依次执行：后端 `verify`（测试、打包、Google Java 格式检查），前端 `lint`、`test`、`format:check`、`build`。失败时停止并返回非零结果。

仅后端检查或运行其他 Maven goal：

```powershell
# 在仓库根目录，载入工具函数；只影响当前进程。
. ./scripts/common.ps1
Set-ProjectJava
Push-Location backend
try {
  ./mvnw.cmd "-Dmaven.repo.local=$taskRepoRoot/.tools/m2" verify
} finally {
  Pop-Location
}
```

Java 格式化使用 `pwsh -NoProfile -File scripts/format-java.ps1`。前端可独立运行：

```powershell
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run format
npm.cmd --prefix frontend run format:check
npm.cmd --prefix frontend run build
```

新增或修改依赖时由负责人更新依赖清单和锁文件；正常复现用 `npm ci`，不要用 `npm install` 无意更新锁定依赖。

## 使用构建产物

后端可运行 JAR：`backend/target/campus-mental-health-backend-0.0.1-SNAPSHOT.jar`。

```powershell
. ./scripts/common.ps1
Set-ProjectJava
Push-Location backend
try {
  & (Join-Path $env:JAVA_HOME 'bin/java.exe') -jar target/campus-mental-health-backend-0.0.1-SNAPSHOT.jar
} finally {
  Pop-Location
}
```

从 `backend` 运行保证相对数据库路径一致。没有全局 JAVA_HOME 时，不要直接假定其他终端里的 `java` 能找到项目 JDK。

前端静态构建在 `frontend/dist`。可运行 `npm.cmd --prefix frontend run preview` 本机预览，默认 4173；当前配置的 preview 会继承开发代理，因此后端启动后可本机联调。这不会自动打包后端，也不是生产托管。正式静态服务器需要为 `/api`、`/actuator` 设置反向代理，或者在构建前设置 `VITE_API_BASE_URL` 并设计后端跨域与授权。[Vite 官方 preview 配置](https://vite.dev/config/preview-options)说明其代理继承关系。

前端使用 history 路由，正式托管需将未命中的页面路由回退到 index.html。现阶段没有完整生产部署脚本，不把预览命令当成生产部署。

## 运行配置与数据

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| SERVER_ADDRESS | 127.0.0.1 | 后端监听地址 |
| SERVER_PORT | 8080 | 后端端口 |
| DB_URL | jdbc:h2:file:./runtime/campus-mental-health | 相对后端运行目录的数据库 |
| DB_USERNAME | sa | 默认本地数据库用户 |
| DB_PASSWORD | 空 | 仅当前本地骨架默认；共享部署须重新设计 |
| VITE_API_BASE_URL | 空 | 前端使用同源 /api，开发代理指向 8080 |
| VITE_DEV_PORT | 5173 | 本机前端开发端口 |
| VITE_BACKEND_URL | http://127.0.0.1:8080 | 开发及 preview 的代理目标 |

后端变量在启动终端通过 `$env:变量名` 设置。前端 `.env.example` 是样例，需要时复制到不提交的 `.env.local`；前端变量在开发启动或构建时读取，不能在其中保存密码或私密令牌。

默认 strictPort 不自动跳到其他端口。端口占用时可设置本地变量，不修改共享源码，例如后端终端设置 `$env:SERVER_PORT='8081'`，前端终端设置 `$env:VITE_DEV_PORT='5174'` 和 `$env:VITE_BACKEND_URL='http://127.0.0.1:8081'`，再启动各自脚本。生成 VS Code 工作区时可加 `-BackendPort 8081 -FrontendPort 5174`，同步任务、代理及浏览器调试地址。

H2 文件在 `backend/runtime`，测试使用独立内存库。备份文件数据库前停止写入或关闭服务；不得在未验证完整性时把在线复制当作可靠恢复方案。不要把数据库文件或真实学生资料提交 Git。

## Linux / macOS

准备平台对应 JDK 21、Node 24.15.0/npm 11.12.1，设置本机 JAVA_HOME；Windows setup/configure 脚本不在这些平台使用。命令行构建：

```bash
cd backend
./mvnw --batch-mode verify
# 运行后端时用 ./mvnw spring-boot:run
cd ../frontend
npm ci --offline=false
npm run lint
npm test
npm run format:check
npm run build
# 运行前端时用 npm run dev
```

Wrapper 可执行位保存在 Git 中；若通过 ZIP 下载丢失权限，可执行 `chmod +x backend/mvnw`。平台不同，工具目录、扩展路径和生成产物也不同。CI 使用 Linux 验证 Java/前端兼容性；它不等于已验证每个桌面系统的图形调试器。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| 找不到 pwsh | 安装 PowerShell 7，重新打开终端；不是调用 powershell.exe |
| Java 版本不是 21 | 使用 setup.ps1 -JdkHome 明确选择；检查 `.tools/environment.local.json` 中路径 |
| clone 后 JDK 路径无效 | 不复制其他人的 `.tools`；按自己的位置重新初始化 |
| npm ENOTCACHED | 个人 npm 开了离线模式；初始化已带 --offline=false，手动 ci 也带该参数 |
| Wrapper 或依赖获取失败 | 确认网络、代理及写权限；setup 的 Node 下载通道验证固定 SHA256；不要关闭 TLS 校验 |
| 401 / H2 控制台不可见 | 骨架未实现登录，业务默认拒绝，控制台关闭；不要用全局 permitAll 绕过 |
| 前端显示后端未连接 | 确认后端启动、8080 可用和开发代理地址一致 |
| 保存格式与检查冲突 | Java 用 Spotless，前端用项目 Prettier；重新生成外部工作区并重载 |
| 生产构建提示大文件 | 当前完整注册 Element Plus 导致体积提醒，构建仍可通过；后续页面开发再做按需加载 |
| 旧终端环境没更新 | 编辑器重载后新建终端；改变工作区设置不会更新已存在终端 |

更改 API、授权、数据模型或模型特征时同步相应文档与测试；当前骨架通过检查不代表完整业务已经验收。
