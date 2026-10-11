-- Additive migration only. Run against an isolated test database after V002.
USE yu_picture;
CREATE TABLE IF NOT EXISTS picture_ai_task (
  id varchar(36) NOT NULL PRIMARY KEY COMMENT '本地任务 ID，不对外泄漏供应商任务 ID',
  userId bigint NOT NULL,
  pictureId bigint NOT NULL,
  remoteTaskId varchar(128) NULL,
  status varchar(32) NOT NULL,
  resultUrl varchar(2048) NULL,
  createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ai_owner_created(userId, createTime),
  INDEX idx_ai_picture(pictureId)
) COMMENT 'AI 扩图任务归属和状态';
