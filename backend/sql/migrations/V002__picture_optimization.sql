-- 手工迁移脚本：先确认目标数据库并备份，再执行。本次开发没有连接或修改数据库。
-- 兼容已有审核字段和更早的 picture 表；重复执行不重复添加字段和索引。
USE yu_picture;
SET @columns = CONCAT_WS(', ',
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'picture' AND column_name = 'thumbnailUrl') = 0, 'ADD COLUMN thumbnailUrl VARCHAR(512) NULL', NULL),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'picture' AND column_name = 'picColor') = 0, 'ADD COLUMN picColor VARCHAR(32) NULL', NULL),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'picture' AND column_name = 'spaceId') = 0, 'ADD COLUMN spaceId BIGINT NULL', NULL),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'picture' AND column_name = 'reviewStatus') = 0, 'ADD COLUMN reviewStatus INT DEFAULT 0 NOT NULL', NULL),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'picture' AND column_name = 'reviewMessage') = 0, 'ADD COLUMN reviewMessage VARCHAR(512) NULL', NULL),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'picture' AND column_name = 'reviewerId') = 0, 'ADD COLUMN reviewerId BIGINT NULL', NULL),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'picture' AND column_name = 'reviewTime') = 0, 'ADD COLUMN reviewTime DATETIME NULL', NULL)
);
SET @migration_sql = IF(@columns = '', 'SELECT 1', CONCAT('ALTER TABLE picture ', @columns));
PREPARE moxiang_migration FROM @migration_sql;
EXECUTE moxiang_migration;
DEALLOCATE PREPARE moxiang_migration;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'picture' AND index_name = 'idx_public_listing') = 0,
    'CREATE INDEX idx_public_listing ON picture (spaceId, reviewStatus, createTime, id)', 'SELECT 1');
PREPARE moxiang_migration FROM @migration_sql;
EXECUTE moxiang_migration;
DEALLOCATE PREPARE moxiang_migration;
