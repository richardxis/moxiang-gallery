package com.moxiang.picture.manager.search;

import com.moxiang.picture.model.vo.PictureSearchVO.ExternalResult;

public interface ImageSearchGateway {
    ExternalResult search(String imageUrl, int limit);
}
