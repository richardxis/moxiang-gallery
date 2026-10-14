package com.moxiang.picture.manager.search;

import com.fasterxml.jackson.databind.*;
import com.moxiang.picture.config.ImageSearchProperties;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.model.vo.PictureSearchVO.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

@Component
public class BaiduImageSearchGateway implements ImageSearchGateway {
    private static final String ORIGIN = "https://graph.baidu.com";
    private static final Pattern FIRST_URL = Pattern.compile("\"firstUrl\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")");
    private final ImageSearchProperties properties;
    private final ObjectMapper json;
    private final Transport transport;
    @FunctionalInterface
    public interface Transport { String request(String method, URI uri, String form) throws IOException; }
    @Autowired
    public BaiduImageSearchGateway(ImageSearchProperties properties, ObjectMapper json) {
        this(properties, json, (method, uri, form) -> exchange(properties, method, uri, form));
    }
    public BaiduImageSearchGateway(ImageSearchProperties properties, ObjectMapper json, Transport transport) {
        this.properties=properties; this.json=json; this.transport=transport;
    }
    @Override public ExternalResult search(String imageUrl, int limit) {
        ThrowUtils.throwIf(!properties.isEnabled(), ErrorCode.IMAGE_SEARCH_DISABLED, "以图搜图未启用");
        ThrowUtils.throwIf(limit < 1 || limit > 50, ErrorCode.PARAMS_ERROR);
        try {
            String form="image="+URLEncoder.encode(imageUrl,"UTF-8")+"&tn=pc&from=pc&image_source=PC_UPLOAD_URL";
            JsonNode upload=json.readTree(transport.request("POST", providerUri(ORIGIN+"/upload?uptime="+System.currentTimeMillis()),form));
            if (upload==null || !upload.has("status") || upload.path("status").asInt(-1)!=0 || !upload.path("data").path("url").isTextual()) throw upstream();
            String raw=upload.path("data").path("url").asText();
            if (raw.toLowerCase(Locale.ROOT).startsWith("https%3a")) raw=URLDecoder.decode(raw,"UTF-8");
            URI page=providerUri(raw);
            ExternalResult result=new ExternalResult(); result.setPageUrl(page.toString());
            String html=transport.request("GET",page,null);
            String first=null;
            for (Element script : Jsoup.parse(html).getElementsByTag("script")) {
                Matcher matcher=FIRST_URL.matcher(script.html());
                if (matcher.find()) {first=json.readValue(matcher.group(1),String.class); break;}
            }
            if (first==null) {
                result.setStatus("OPEN_PROVIDER"); result.setMessage("服务商要求在识图页面继续查看，请打开结果页"); return result;
            }
            JsonNode response=json.readTree(transport.request("GET",providerUri(first),null));
            JsonNode list=response==null?null:response.path("data").path("list");
            if (list==null || !list.isArray()) throw upstream();
            Set<String> seen=new HashSet<>();
            for(JsonNode row:list) {
                String thumb=safeResultUrl(row.path("thumbUrl").asText(null));
                String from=safeResultUrl(row.path("fromUrl").asText(null));
                if(thumb==null || from==null || !seen.add(thumb+"\n"+from)) continue;
                ExternalImage image=new ExternalImage();image.setThumbUrl(thumb);image.setFromUrl(from);result.getResults().add(image);
                if(result.getResults().size()>=limit)break;
            }
            return result;
        } catch (SocketTimeoutException e) {throw new BusinessException(ErrorCode.IMAGE_SEARCH_TIMEOUT,"以图搜图服务超时，请稍后重试");}
        catch (BusinessException e) {throw e;}
        catch (IOException | IllegalArgumentException e) {throw upstream();}
    }
    public static URI providerUri(String raw) {
        try {
            if(raw==null||raw.trim().isEmpty())throw upstream();
            URI uri=URI.create(ORIGIN+"/").resolve(raw);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || !"graph.baidu.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)) throw upstream();
            return uri;
        } catch (IllegalArgumentException | NullPointerException e) {throw upstream();}
    }
    private static String safeResultUrl(String raw) {
        try {URI uri=URI.create(raw);return (("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost()!=null && uri.getUserInfo()==null)?uri.toASCIIString():null;}
        catch(RuntimeException e){return null;}
    }
    private static BusinessException upstream(){return new BusinessException(ErrorCode.IMAGE_SEARCH_UPSTREAM,"以图搜图服务暂不可用或结果格式发生变化");}
    private static String exchange(ImageSearchProperties properties,String method,URI uri,String form)throws IOException {
        // Every hop is validated; automatic redirects cannot turn a provider response into an arbitrary fetch.
        providerUri(uri.toString());
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(properties.getConnectTimeoutMillis());connection.setReadTimeout(properties.getReadTimeoutMillis());
        connection.setRequestMethod(method);connection.setRequestProperty("User-Agent","Mozilla/5.0 MoxiangGallery/1.0");
        try{
            if(form!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream output=connection.getOutputStream()){output.write(form.getBytes(StandardCharsets.UTF_8));}}
            if(connection.getResponseCode()!=200)throw upstream();
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(output.size()+count>2*1024*1024)throw upstream();output.write(buffer,0,count);}
                return new String(output.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{connection.disconnect();}
    }
}
