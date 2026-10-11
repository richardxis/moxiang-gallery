create database if not exists yu_picture;

use yu_picture;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '图片 url',
    thumbnailUrl varchar(512)                       null comment '缩略图 url',
    picColor     varchar(32)                        null comment '图片主色调',
    spaceId      bigint                             null comment '空间 id，null 为公共图库',
    revision     bigint default 0                    not null comment '持久化图片版本',
    storageBucket varchar(128)                       null,
    originalKey varchar(512)                         null,
    compressedKey varchar(512)                       null,
    thumbnailKey varchar(512)                        null,
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                      null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    reviewStatus int default 0                      not null comment '审核状态：0-待审核; 1-通过; 2-拒绝',
    reviewMessage varchar(512)                      null comment '审核信息',
    reviewerId   bigint                             null comment '审核人 id',
    reviewTime   datetime                           null comment '审核时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId),
    INDEX idx_reviewStatus (reviewStatus),
    INDEX idx_public_listing (spaceId, reviewStatus, createTime, id)
) comment '图片' collate = utf8mb4_unicode_ci;

-- 已有数据库不能仅重跑 CREATE TABLE 来增加字段，请应用 migrations/V002__picture_optimization.sql。

-- 与 migrations/V003__picture_ai_task.sql 保持一致；已有库使用迁移脚本。
CREATE TABLE IF NOT EXISTS picture_ai_task (
    id varchar(36) NOT NULL PRIMARY KEY,
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

-- 与 migrations/V004__personal_space.sql 同步；不会自动修复已存在的表。
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
) ENGINE=InnoDB COMMENT '个人及团队空间；暂不支持空间删除';

-- 与 V005 保持一致；个人和团队共用 space 表，仍不支持删除空间。
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

-- Same schema as V006; existing DBs need the additive migration.
CREATE TABLE IF NOT EXISTS picture_edit_save (
 requestId VARCHAR(36) NOT NULL PRIMARY KEY,pictureId BIGINT NOT NULL,userId BIGINT NOT NULL,
 expectedRevision BIGINT NOT NULL,fingerprint CHAR(64) NOT NULL,resultRevision BIGINT NOT NULL,
 resultJson MEDIUMTEXT NOT NULL,createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_save_picture(pictureId,createTime),INDEX idx_save_owner(userId,createTime)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS picture_object_cleanup (
 id VARCHAR(36) NOT NULL PRIMARY KEY,bucket VARCHAR(128) NOT NULL,objectKeys TEXT NOT NULL,
 status VARCHAR(16) NOT NULL,attempts INT NOT NULL DEFAULT 0,nextRunTime DATETIME NOT NULL,
 claimToken VARCHAR(36) NULL,lastErrorType VARCHAR(128) NULL,
 createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_cleanup_poll(status,nextRunTime)
) ENGINE=InnoDB;
