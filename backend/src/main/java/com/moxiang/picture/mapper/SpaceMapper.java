package com.moxiang.picture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moxiang.picture.model.entity.Space;
import org.apache.ibatis.annotations.*;

public interface SpaceMapper extends BaseMapper<Space> {
    @Select("SELECT * FROM space WHERE id=#{id} FOR UPDATE")
    Space selectForUpdate(@Param("id") Long id);
    @Select("SELECT s.* FROM space s LEFT JOIN space_user su ON su.spaceId=s.id AND su.userId=#{userId} "
            + "WHERE s.spaceType=1 AND (s.userId=#{userId} OR su.userId IS NOT NULL) ORDER BY s.createTime DESC,s.id DESC LIMIT #{size} OFFSET #{offset}")
    java.util.List<Space> joinedTeams(@Param("userId") Long userId, @Param("size") int size, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM space s LEFT JOIN space_user su ON su.spaceId=s.id AND su.userId=#{userId} "
            + "WHERE s.spaceType=1 AND (s.userId=#{userId} OR su.userId IS NOT NULL)")
    long countJoinedTeams(@Param("userId") Long userId);
    @Update("UPDATE space SET totalSize=totalSize+#{sizeDelta}, totalCount=totalCount+#{countDelta}, "
            + "updateTime=CURRENT_TIMESTAMP WHERE id=#{id} AND spaceType IN (0,1) "
            + "AND totalSize+#{sizeDelta} BETWEEN 0 AND maxSize "
            + "AND totalCount+#{countDelta} BETWEEN 0 AND maxCount")
    int adjustUsage(@Param("id") Long id, @Param("sizeDelta") long sizeDelta, @Param("countDelta") long countDelta);
}
