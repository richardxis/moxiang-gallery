-- Additive; not executed by this coding task. Review target DB before applying.
USE yu_picture;
CREATE TABLE IF NOT EXISTS space (
    id bigint NOT NULL PRIMARY KEY,
    spaceName varchar(128) NOT NULL,
    spaceLevel int NOT NULL DEFAULT 0,
    spaceType int NOT NULL DEFAULT 0,
    maxSize bigint NOT NULL,
    maxCount bigint NOT NULL,
    totalSize bigint NOT NULL DEFAULT 0,
    totalCount bigint NOT NULL DEFAULT 0,
    userId bigint NOT NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_space_owner_type(userId, spaceType)
) ENGINE=InnoDB COMMENT '个人空间基础；本阶段不支持空间删除和团队空间';
