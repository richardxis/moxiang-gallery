# 墨象协同云图库 · 后端

Spring Boot 服务提供用户、图片、空间权限、公共缓存、实时协作、图片搜索、批量编辑、空间分析与异步扩图接口。

## 构建与运行

```powershell
mvn '-Dtest=*BoundaryTest' package
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\check-release.ps1 -SkipGitIndex
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\start-local.ps1 -ConfigFile '.\config\application-local.yml'
```

配置项与数据库步骤见 [安装说明](../docs/SETUP.md)，接口说明见 [功能文档](../docs/FEATURES.md)。

## 关键机制

- Sa-Token 校验空间权限，HttpSession 维护登录。
- 公共分页缓存使用条件摘要、共享版本与随机 TTL。
- 普通编辑、批量编辑及协作保存共享图片写入门控。
- expectedRevision 校验并发修改，requestId 登记协作保存结果。
- 云对象通过清理任务处理失败补偿和旧对象回收。
- 真实配置通过外部文件或环境变量加载，发布 JAR 排除私有 profile。

[架构](../docs/ARCHITECTURE.md) · [测试](../docs/VALIDATION.md) · [部署约束](../docs/LIMITATIONS.md)
