package com.moxiang.picture.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.config.ImageSearchProperties;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.manager.search.BaiduImageSearchGateway;
import com.moxiang.picture.model.vo.PictureSearchVO.ExternalResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in network probe: only an existing approved public picture URL is submitted. */
class ImageSearchLiveProbeTest {
    @Test void queryExistingPublicImage()throws Exception{
        assumeTrue(Boolean.getBoolean("moxiang.image-search.live"),"External probe requires an explicit switch");
        String config=System.getProperty("moxiang.analysis.mysql.config");assumeTrue(config!=null,"Provide a private MySQL YAML path");
        YamlPropertiesFactoryBean yaml=new YamlPropertiesFactoryBean();yaml.setResources(new FileSystemResource(config));StandardEnvironment environment=new StandardEnvironment();environment.getPropertySources().addLast(new PropertiesPropertySource("private",yaml.getObject()));
        DriverManagerDataSource source=new DriverManagerDataSource(environment.getRequiredProperty("spring.datasource.url"),environment.getRequiredProperty("spring.datasource.username"),environment.getProperty("spring.datasource.password",""));
        Properties options=new Properties();options.setProperty("connectTimeout","2000");options.setProperty("socketTimeout","3000");source.setConnectionProperties(options);
        String url=null;String format=null;
        try(Connection connection=source.getConnection()){connection.setReadOnly(true);try(Statement sql=connection.createStatement();ResultSet rows=sql.executeQuery("SELECT url,originalKey,picFormat FROM picture WHERE spaceId IS NULL AND reviewStatus=1 AND isDelete=0 ORDER BY id LIMIT 1")){if(rows.next()){com.moxiang.picture.model.entity.Picture picture=new com.moxiang.picture.model.entity.Picture();picture.setUrl(rows.getString(1));picture.setOriginalKey(rows.getString(2));url=PictureDiscoveryService.searchSourceUrl(picture);format=rows.getString(3);}}}
        assumeTrue(url!=null,"No approved public source picture is available");
        ImageSearchProperties properties=new ImageSearchProperties();properties.setEnabled(true);
        Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("scope","one real external search using an existing approved public image; no business writes");evidence.put("time",java.time.Instant.now().toString());evidence.put("displayFormat",format);evidence.put("sourceIsWebp",url.toLowerCase(java.util.Locale.ROOT).contains(".webp"));
        List<Map<String,Object>> steps=new ArrayList<>();evidence.put("steps",steps);
        try{
            ExternalResult result=new BaiduImageSearchGateway(properties,new ObjectMapper(),(method,uri,form)->{
                Map<String,Object> step=new LinkedHashMap<>();step.put("method",method);step.put("path",uri.getPath());steps.add(step);
                HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(3000);connection.setReadTimeout(5000);connection.setRequestMethod(method);connection.setRequestProperty("User-Agent","Mozilla/5.0 MoxiangGallery/1.0");
                try{
                    if(form!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream output=connection.getOutputStream()){output.write(form.getBytes(StandardCharsets.UTF_8));}}
                    int status=connection.getResponseCode();step.put("httpStatus",status);if(status!=200)throw new IOException("Provider returned a non-200 response");
                    try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                        byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(output.size()+count>2*1024*1024)throw new IOException("Response size limit");output.write(buffer,0,count);}
                        String body=new String(output.toByteArray(),StandardCharsets.UTF_8);
                        try{com.fasterxml.jackson.databind.JsonNode node=new ObjectMapper().readTree(body);if(node.has("status"))step.put("providerStatus",node.path("status").asText());}catch(IOException ignored){}
                        return body;
                    }
                }finally{connection.disconnect();}
            }).search(url,12);
            evidence.put("status",result.getStatus());evidence.put("results",result.getResults().size());evidence.put("resultPageReceived",result.getPageUrl()!=null);evidence.put("upstreamAvailable",true);
            assertTrue(Arrays.asList("COMPLETE","OPEN_PROVIDER").contains(result.getStatus()));
        }catch(BusinessException failure){evidence.put("upstreamAvailable",false);evidence.put("businessCode",failure.getCode());throw failure;}
        finally{Path output=Paths.get("target","image-search-live-probe.json");Files.createDirectories(output.getParent());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),evidence);}
    }
}
