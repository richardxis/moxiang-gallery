-- Additive only: requires compatible V004 space schema with uk_space_owner_type.
USE yu_picture;
CREATE TABLE IF NOT EXISTS space_user (
    id bigint NOT NULL PRIMARY KEY,
    spaceId bigint NOT NULL,
    userId bigint NOT NULL,
    spaceRole varchar(32) NOT NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_space_member(spaceId, userId),
    INDEX idx_member_user(userId, spaceId)
) ENGINE=InnoDB COMMENT '团队空间成员；本阶段物理删除成员，保留空间创建者';
-- No inferred backfill of historical teams or owners. Review legacy schema/data manually.
