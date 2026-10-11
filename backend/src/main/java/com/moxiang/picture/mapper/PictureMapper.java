package com.moxiang.picture.mapper;

import com.moxiang.picture.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author Richard
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2026-03-19 14:28:32
* @Entity com.moxiang.picture.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM picture WHERE id=#{id} AND isDelete=0 FOR UPDATE")
    Picture selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);

}




