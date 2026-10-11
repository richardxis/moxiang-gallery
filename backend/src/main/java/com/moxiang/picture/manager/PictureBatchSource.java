package com.moxiang.picture.manager;

import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 按原型通过 Bing 图片页面抓取候选；页面结构变化时显式报错。 */
@Component
public class PictureBatchSource {
    public List<String> search(String keyword, int limit) {
        try {
            String url = "https://www.bing.com/images/async?q=" + URLEncoder.encode(keyword, "UTF-8")
                    + "&first=0&count=" + limit + "&mmasync=1";
            Document document = Jsoup.connect(url).timeout(10000).maxBodySize(2 * 1024 * 1024)
                    .followRedirects(false).get();
            List<String> candidates = extract(document, limit);
            if (candidates.isEmpty()) throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片，搜索源可能已变更");
            return candidates;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片搜索服务暂不可用"); }
    }
    public static List<String> extract(Document document, int limit) {
        Set<String> urls = new LinkedHashSet<>();
        for (Element image : document.select("img.mimg")) {
            String url = image.attr("src");
            if (url.isEmpty()) url = image.attr("data-src");
            if (url.startsWith("//")) url = "https:" + url;
            if (url.startsWith("https://") || url.startsWith("http://")) urls.add(url);
            if (urls.size() >= limit) break;
        }
        return new ArrayList<>(urls);
    }
}
