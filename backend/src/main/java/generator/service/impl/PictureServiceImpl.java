package generator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import generator.domain.Picture;
import generator.service.PictureService;
import generator.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
* @author Richard
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-03-21 17:48:09
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

}




