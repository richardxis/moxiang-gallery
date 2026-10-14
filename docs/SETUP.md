# 安装与启动

## 环境

推荐使用 JDK 21、Maven 3.9、Node.js 20 与 MySQL 8。Redis 按缓存开关启用；图片上传需要腾讯云 COS 和数据万象。

## 数据库

新数据库参考 `backend/sql/create_table.sql`，已有数据库按 `backend/sql/migrations` 顺序核对字段与表。默认库名为 `yu_picture`，修改时同步数据源 URL 与 SQL 中的数据库名。

## 后端配置

参考 `backend/src/main/resources/application-local.example.yml` 创建外部配置文件，例如 `backend/config/application-local.yml`。也可以设置环境变量：

| 环境变量 | 用途 |
| --- | --- |
| MOXIANG_DB_URL / MOXIANG_DB_USER / MOXIANG_DB_PASSWORD | MySQL 连接 |
| MOXIANG_COS_HOST / MOXIANG_COS_SECRET_ID / MOXIANG_COS_SECRET_KEY / MOXIANG_COS_REGION / MOXIANG_COS_BUCKET | COS 域名及资源凭据 |
| MOXIANG_REDIS_ENABLED / MOXIANG_REDIS_HOST / MOXIANG_REDIS_PORT / MOXIANG_REDIS_PASSWORD | Redis 缓存 |
| MOXIANG_CACHE_MODE | NONE、REDIS 或 MULTILEVEL |
| MOXIANG_FRONTEND_ORIGINS | 前端 Origin 列表 |
| MOXIANG_AI_ENABLED / MOXIANG_AI_API_KEY | 可选 AI 扩图 |
| MOXIANG_IMAGE_SEARCH_ENABLED | 公共图片以图搜图，默认 false |
| MOXIANG_REMOTE_ALLOWED_HOSTS | URL 导入允许的域名 |

COS Host 使用完整 HTTP(S) Origin，不带路径。配置文件与凭据保存在仓库之外，VITE_* 仅用于公开的前端配置。

```powershell
cd backend
mvn '-Dtest=*BoundaryTest' package
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\start-local.ps1 -ConfigFile '.\config\application-local.yml'
```

后端默认地址为 `http://localhost:8123/api`。启动脚本默认关闭 AI，可通过部署配置单独启动相关能力。

## 前端

```powershell
cd frontend
npm ci
Copy-Item .env.example .env.local
npm run dev
```

`.env.local` 中填写 `VITE_API_BASE_URL=http://localhost:8123`，地址不附加 `/api`。前后端统一使用 localhost 或 127.0.0.1，以便 Cookie 会话与 Origin 配置匹配。

生产构建：

```powershell
npm run build
```

## 常见问题

- 配置未加载：检查 ConfigFile 路径和环境变量。
- 登录后仍提示未登录：检查 Cookie、CORS、主机名与代理路径。
- 修改提示冲突：刷新图片版本和协作状态。
- 以图搜图未启用：设置对应开关；验证页可使用“打开识图结果页”。
- 颜色搜索无结果：检查图片是否保存主色调、阈值和空间范围。
- 空间图片直链可读：检查 COS 桶与 CDN 访问策略。
