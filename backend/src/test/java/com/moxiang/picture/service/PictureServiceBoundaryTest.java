package com.moxiang.picture.service;

import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.manager.PictureBatchSource;
import com.moxiang.picture.model.dto.picture.PictureQueryRequest;
import com.moxiang.picture.service.impl.PictureServiceImpl;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PictureServiceBoundaryTest {
    @Test void rejectsMetadataThatExceedsSchemaLimits() {
        com.moxiang.picture.model.entity.Picture picture = new com.moxiang.picture.model.entity.Picture();
        picture.setId(1L); picture.setIntroduction(new String(new char[513]).replace('\0', 'a'));
        assertThrows(BusinessException.class, () -> new PictureServiceImpl().validPicture(picture));
    }
    @Test void rejectsUntrustedSortField() {
        PictureQueryRequest query = new PictureQueryRequest();
        query.setSortField("id desc; drop table picture");
        assertThrows(BusinessException.class, () -> new PictureServiceImpl().getQueryWrapper(query));
    }
    @Test void rejectsNullSortDirectionWhenFieldPresent() {
        PictureQueryRequest query = new PictureQueryRequest();
        query.setSortField("createTime");
        query.setSortOrder(null);
        assertThrows(BusinessException.class, () -> new PictureServiceImpl().getQueryWrapper(query));
    }
    @Test void extractsDistinctCandidateUrlsAndPreservesQueryParameters() {
        String html = "<img class='mimg' src='https://example.com/a.jpg?size=small'>"
                + "<img class='mimg' data-src='https://example.com/a.jpg?size=small'>"
                + "<img class='mimg' src='data:image/png;base64,abc'>";
        assertEquals(java.util.Collections.singletonList("https://example.com/a.jpg?size=small"),
                PictureBatchSource.extract(Jsoup.parse(html), 10));
    }
}
