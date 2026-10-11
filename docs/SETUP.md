# 安装与启动

## 环境与数据库

本地已验证 JDK 21.0.5、Maven 3.9.4、Node 20.19.3；编译目标 Java 8 不表示已测 Java 8 运行。准备 MySQL 和自己的 COS/数据万象资源，Redis、AI 按开关启用。

先阅读 backend/sql/create_table.sql，再顺序核对 migrations/V002 到 V006。默认库名 yu_picture 为兼容当前 SQL；更名时必须统一配置 URL 与 SQL 的数据库选择。新环境使用隔离测试库，生产库先备份与审批；脚本不自动迁移或清空。

## 后端配置

公共 application.yml 只带占位和非敏感默认值。application-local.example.yml 是模板，不会自动加载；将其复制到外部私有目录填写，启动时指定 ConfigFile。不要覆盖已有配置或提交秘密。

| 环境变量 | 用途 |
| --- | --- |
| MOXIANG_DB_URL / USER / PASSWORD | MySQL 地址与最小权限账号 |
| MOXIANG_COS_HOST / SECRET_ID / SECRET_KEY / REGION / BUCKET | COS 域名、凭据和资源 |
| MOXIANG_REDIS_ENABLED / HOST / PORT / PASSWORD | Redis 缓存开关与连接 |
| MOXIANG_FRONTEND_ORIGINS | 精确前端 Origin，含协议和端口 |
| MOXIANG_AI_ENABLED / API_KEY | 可选模型调用，默认关闭 |
| MOXIANG_REMOTE_ALLOWED_HOSTS | 远程导入允许域名，默认空 |

COS Host 是 HTTP(S) Origin，不带密码、路径、查询或 fragment。建议部署使用 HTTPS；缺配置时错误只报告变量名。

```powershell
cd backend
mvn '-Dtest=*BoundaryTest' package
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\start-local.ps1 -ConfigFile '自己的完整路径\application-local.yml'
```

脚本默认回环地址 8123、local profile、AI 关闭、SQL 参数日志关闭，不迁移或改桶。可传 -Java / -Port；端口冲突时报错，不结束其他服务。

IDE 激活 local；JAR 排除所有真实 profile，仍需外部配置或环境变量，不能只激活 local 就以为配置在包里。不要发布整个 target/classes。

## 前端

```powershell
cd frontend
npm ci
if (-not (Test-Path .env.local)) { Copy-Item .env.example .env.local }
npm run dev
```

VITE_API_BASE_URL 示例为 http://localhost:8123，不含 /api。前后端统一 localhost 或 127.0.0.1；所有 VITE_* 可能进入浏览器，禁止填服务器秘密。改配置后重启或重新构建。

```powershell
npm run type-check
npm run build
```

生产需路由回退、HTTPS、Cookie/CORS 与 WebSocket Upgrade 转发。preview 不是正式托管。

## 常见排查

- 缺 COS 项：按变量名补外部配置，不贴真实值。
- 数据库失败：核对服务、库、账号权限和迁移，不删除旧表。
- 登录失效：检查主机名、Cookie 与 CORS。
- 保存冲突：刷新图片/房间版本，不用旧 expectedRevision 强写。
- 私有空间直链可读：检查桶策略，API 权限不能改变公有读对象。
