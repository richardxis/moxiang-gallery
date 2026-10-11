package com.moxiang.picture.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moxiang.picture.model.entity.SpaceUser;
import org.apache.ibatis.annotations.*;

public interface SpaceUserMapper extends BaseMapper<SpaceUser> {
    @Select("SELECT * FROM space_user WHERE spaceId=#{spaceId} AND userId=#{userId}")
    SpaceUser findMembership(@Param("spaceId") Long spaceId, @Param("userId") Long userId);
}
