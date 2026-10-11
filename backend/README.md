# 墨象协同云图库 · 后端

Spring Boot 2.7.6 后端，提供用户、图片、空间、缓存、实时协作与可选 AI 扩图能力。完整产品介绍见 [项目首页](../README.md)。

## 启动前

准备 MySQL 和 COS/数据万象；Redis 由开关决定。按 [安装说明](../docs/SETUP.md) 核对建表与迁移，并以环境变量或外部私有配置提供凭据。

```powershell
mvn '-Dtest=*BoundaryTest' package
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\check-release.ps1 -SkipGitIndex
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\start-local.ps1 -ConfigFile '自己的完整路径\application-local.yml'
```

发布审查时使用 `check-release.ps1 -ForPublication -FrontendRoot '..\frontend'`；不能跳过 Git 索引，也不能把找不到索引当成安全通过。脚本识别后端独立仓库和 backend/frontend 根仓库布局。

## 重要边界

- HttpSession、协作房间和编辑锁为单实例；Redis 缓存不等于分布式会话。
- 普通图片修改和协作保存都校验 expectedRevision；重试保留稳定 requestId。
- 云对象上传不参与 MySQL 事务，使用精确对象清理任务补偿；不按整个前缀盲删。
- public 基础配置无真实凭据。JAR 排除所有 profile，IDE 可用 target/classes local，但不能把整个 target 作为发布包。
- COS 缺配置时启动报出所需变量名，不输出 SecretId/SecretKey；不自动 Mock 云连接。
- 密码保留旧原型兼容逻辑，正式开放前需要迁移方案；API 鉴权不替代私有对象存储策略。

测试范围与未实现能力详见 [验证说明](../docs/VALIDATION.md)、[限制](../docs/LIMITATIONS.md) 和 [架构](../docs/ARCHITECTURE.md)。
