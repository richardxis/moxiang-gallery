-- Additive, idempotent migration. Back up and confirm the target test DB first.
USE yu_picture;
SET @columns = CONCAT_WS(', ',
 IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='picture' AND column_name='revision')=0,'ADD COLUMN revision BIGINT NOT NULL DEFAULT 0',NULL),
 IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='picture' AND column_name='storageBucket')=0,'ADD COLUMN storageBucket VARCHAR(128) NULL',NULL),
 IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='picture' AND column_name='originalKey')=0,'ADD COLUMN originalKey VARCHAR(512) NULL',NULL),
 IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='picture' AND column_name='compressedKey')=0,'ADD COLUMN compressedKey VARCHAR(512) NULL',NULL),
 IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='picture' AND column_name='thumbnailKey')=0,'ADD COLUMN thumbnailKey VARCHAR(512) NULL',NULL)
);
SET @migration_sql=IF(@columns='','SELECT 1',CONCAT('ALTER TABLE picture ',@columns));
PREPARE moxiang_save_migration FROM @migration_sql;
EXECUTE moxiang_save_migration;
DEALLOCATE PREPARE moxiang_save_migration;
CREATE TABLE IF NOT EXISTS picture_edit_save (
 requestId VARCHAR(36) NOT NULL PRIMARY KEY,
 pictureId BIGINT NOT NULL,userId BIGINT NOT NULL,expectedRevision BIGINT NOT NULL,
 fingerprint CHAR(64) NOT NULL,resultRevision BIGINT NOT NULL,resultJson MEDIUMTEXT NOT NULL,
 createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_save_picture(pictureId,createTime),INDEX idx_save_owner(userId,createTime)
) ENGINE=InnoDB COMMENT 'Committed collaboration saves and idempotency snapshots';
CREATE TABLE IF NOT EXISTS picture_object_cleanup (
 id VARCHAR(36) NOT NULL PRIMARY KEY,bucket VARCHAR(128) NOT NULL,objectKeys TEXT NOT NULL,
 status VARCHAR(16) NOT NULL,attempts INT NOT NULL DEFAULT 0,nextRunTime DATETIME NOT NULL,
 claimToken VARCHAR(36) NULL,lastErrorType VARCHAR(128) NULL,
 createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_cleanup_poll(status,nextRunTime)
) ENGINE=InnoDB COMMENT 'Exact-key cleanup journal; HOLD prevents deleting uncommitted uploads too early';
