# 运行与构建

## 统一启动

```bash
python start.py
```

默认同时启动后端 8080 和前端 5173，打开浏览器。首次缺少前端依赖时执行锁定安装；后端 JAR 缺失或源码较新时自动构建。启动只打包，不自动运行完整测试；日常检查单独执行 `scripts/check.ps1`。

```bash
python start.py --no-browser
python start.py --restart
python start.py --stop
python start.py --backend-port 8081 --frontend-port 5174
```

启动终端按 Ctrl+C 同时停止两个子进程。`--stop` 核对已记录的 PID 与启动时间，不终止其他项目进程。若默认端口已经运行本项目，直接打开已有服务；其他应用占用端口时报错，可显式换端口。

当前日志为 `.tools/logs/backend.log` 和 `frontend.log`；自动构建日志为 `build.log`，每次启动覆盖同名日志，不无限积累历史文件。后端用 `.tools/demo-run/app.jar` 运行副本，避免锁住 Maven 构建产物。

## 独立开发服务

```powershell
pwsh -NoProfile -File scripts/dev.ps1 -Service backend
pwsh -NoProfile -File scripts/dev.ps1 -Service frontend
```

两条命令在不同终端运行。前端使用同源 `/api` 代理后端，会话 Cookie 与 CSRF 令牌由请求层处理。健康检查为 `/actuator/health`，系统信息为 `/api/v1/system/info`。

## 构建与检查

```powershell
pwsh -NoProfile -File scripts/check.ps1
```

执行后端 `verify`，前端 lint、测试、Prettier 检查和生产构建。格式化入口：`scripts/format-java.ps1`、`npm --prefix frontend run format`。

后端产物为 `backend/target/campus-mental-health-backend-0.0.1-SNAPSHOT.jar`；前端产物为 `frontend/dist/`。手动 JAR 启动时从 `backend` 目录执行，保持数据库及数据集相对路径一致。

Linux/macOS 手动构建：

```bash
cd backend
./mvnw --batch-mode verify
cd ../frontend
npm ci --offline=false
npm run lint && npm test && npm run format:check && npm run build
```

`vite preview` 可本机预览生产静态产物，正式静态托管需 history 路由回退和同源 API 反向代理。

## 配置项

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| SERVER_ADDRESS / SERVER_PORT | 127.0.0.1 / 8080 | 后端监听 |
| VITE_DEV_PORT / VITE_BACKEND_URL | 5173 / http://127.0.0.1:8080 | 前端与开发代理 |
| DB_URL | jdbc:h2:file:./runtime/campus-mental-health | H2 文件库 |
| DB_USERNAME / DB_PASSWORD | sa / 空 | 本机嵌入式数据库 |
| DEMO_ENABLED / DEMO_PASSWORD | true / Campus2026! | 演示账号与初始化 |
| DEMO_DATASET | ../data/downloads/student-mental-health.csv | 小问卷与首次档案映射 |
| ML_DATASET | ../data/downloads/student_lifestyle_100k.csv | 生活方式实验数据 |
| ML_AUTO_TRAIN | true | 启动时训练可用的两份数据 |
| ML_ARTIFACTS | ../models/artifacts | 按 survey/lifestyle 分开保存产物 |

启动脚本的端口参数优先于对应环境变量。其他后端变量从启动终端继承，前端不存放账号密码或私密令牌。

## 数据与故障处理

两份模型数据通过 `scripts/download-demo-data.ps1 -IncludeLifestyle` 准备。数据缺失不阻止业务 Demo；模型页面可切换数据并分别重新训练。业务档案只播种一次，后来下载 CSV 不会覆盖已有操作。

H2 备份前停止后端，再复制 `backend/runtime` 数据文件。清理下载数据、模型或缓存不等于清理数据库。旧中间文件采用可恢复归档，归档清单在 `.tools/archive`。

| 现象 | 处理 |
| --- | --- |
| JDK/Node/Python 缺失 | 按环境说明准备工具，重新初始化 |
| npm ENOTCACHED | 安装使用 `--offline=false`，避免继承个人离线模式 |
| 401 / 403 | 重新登录；检查角色、记录范围及 CSRF，勿绕过授权 |
| 构建/启动失败 | 查看 `.tools/logs/build.log` 或 `backend.log` |
| 模型缺少结果 | 准备对应 CSV，检查格式/哈希后训练该数据集 |
| 图表 chunk 体积提醒 | 已按路由加载，构建可用；提醒不等于失败 |
