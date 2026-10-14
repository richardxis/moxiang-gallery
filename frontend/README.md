# 墨象协同云图库 · 前端

基于 Vue 3、TypeScript、Vite、Ant Design Vue 与 Pinia，提供公共图库、个人/团队空间、图片编辑、搜索、分享和分析页面。

## 开发

```powershell
npm ci
Copy-Item .env.example .env.local
npm run dev
```

`VITE_API_BASE_URL` 默认填 `http://localhost:8123`，不附加 `/api`。前后端使用相同主机名，按 [安装说明](../docs/SETUP.md) 配置 Cookie 与 Origin。

## 构建

```powershell
npm run build
```

Vite 将结果输出到 dist。部署时配置前端路由回退与 WebSocket 转发。

主要入口：公共图库、我的空间、团队空间、图片详情、以图搜图、空间分析和管理员图片表格。图片详情提供链接复制与分享二维码，空间列表支持主色搜索及批量编辑。
