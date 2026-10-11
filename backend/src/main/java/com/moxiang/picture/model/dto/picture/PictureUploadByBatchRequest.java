package com.moxiang.picture.model.dto.picture;

import lombok.Data;

@Data
public class PictureUploadByBatchRequest {  
  
    /**  
     * 搜索词  
     */  
    private String searchText;  
  
    /**  
     * 抓取数量  
     */  
    private Integer count = 10;
    private String namePrefix;
}
