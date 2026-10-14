# 图片检索、批量编辑与分享

## 以图搜图

登录后，从公共图片详情点击“以图搜图”。服务端检查图片已审核通过，再提交至识图服务，展示相似图片及来源。服务商需要页面验证时，点击“打开识图结果页”继续查看。

配置：`MOXIANG_IMAGE_SEARCH_ENABLED=true`。空间图片不会被此接口发送到公网识图服务。

接口：`POST /api/picture/search/picture`

```json
{"pictureId":"图片ID","topN":12}
```

返回 provider、status、pageUrl、results。status 为 COMPLETE 时展示结果列表，为 OPEN_PROVIDER 时展示页面入口。

## 颜色搜索

公共图库、个人空间和团队空间均提供“按颜色搜索”。选择 RGB 颜色，设置 TopN 与最低相似度。结果按主色相似度降序排列。

接口：`POST /api/picture/search/color`

```json
{"spaceId":"空间ID","picColor":"#FF0000","topN":12,"minSimilarity":0.6}
```

省略 spaceId 搜索审核通过的公共图片；指定空间需具备查看权限。相似度使用 RGB 欧氏距离归一化，1 表示主色相同。

## 批量编辑

个人/团队空间的列表可以多选图片；管理员图片表格可以选择公共图片。点击“批量编辑”，勾选分类、标签或命名字段。

命名支持 `素材{序号}`、`{原名}-{序号}`，序号按选择顺序从 1 开始。未勾选字段保持原值；空分类、空标签数组用于清空对应字段。

接口：`POST /api/picture/edit/batch`

```json
{
  "spaceId":"空间ID",
  "pictureIdList":["图片ID1","图片ID2"],
  "category":"素材",
  "tags":["项目"],
  "nameRule":"素材{序号}",
  "expectedRevisions":{"图片ID1":0,"图片ID2":0}
}
```

省略 spaceId 仅允许系统管理员修改公共图片。每批最多 100 张，所有图片版本校验和写入在同一事务内完成。

## 分享

在图片详情点击“分享”，复制详情页链接或使用二维码。分享链接保留原访问规则：公共图片按审核状态展示，空间图片需要接收者登录并具备对应权限。

## 空间分析

个人/团队页面提供分析入口，系统管理员菜单提供全站与公共图库分析。接口为 `POST /api/space/analyze/{usage,category,tag,size,user,rank}`。

普通分析请求在 spaceId、queryPublic、queryAll 中选择一个范围。排行仅系统管理员可用，TopN 为 1—50。用户分析支持 day、week、month，以及 userId、startDate、endDate。
