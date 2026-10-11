# 墨象协同云图库 · 前端

Vue 3 + TypeScript + Vite + Pinia + Ant Design Vue。后端位于相邻的 backend 目录，业务介绍见 [项目首页](../README.md)。

```powershell
npm ci
if (-not (Test-Path .env.local)) { Copy-Item .env.example .env.local }
npm run dev
```

后端默认 Origin 为 http://localhost:8123，`.env.local` 的 VITE_API_BASE_URL 不再追加 /api。前后端保持同一主机名；Cookie、HTTP CORS 和 WebSocket Origin 都需匹配。不要把任何服务端秘密填进 VITE_*。

```powershell
npm run type-check
npm run build
```

使用墨象标识和中文页面元信息；Vue DevTools 只在开发 serve 模式启用。dist 是静态产物，不意味着业务服务已部署。

协作使用图片级排他锁，广播旋转/缩放，裁剪在本地预览后提交；不是多用户同时编辑任意像素的 PS。修改现有图片携带 expectedRevision，保存未知结果保持同一 requestId 查询或重试。

完整配置、生产反向代理要求与限制见 [安装说明](../docs/SETUP.md) 和 [能力边界](../docs/LIMITATIONS.md)。
