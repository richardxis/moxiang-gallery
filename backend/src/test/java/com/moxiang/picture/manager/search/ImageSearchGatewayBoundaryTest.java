package com.moxiang.picture.manager.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.config.ImageSearchProperties;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.model.vo.PictureSearchVO.ExternalResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageSearchGatewayBoundaryTest {
    ImageSearchProperties properties(){ImageSearchProperties properties=new ImageSearchProperties();properties.setEnabled(true);return properties;}
    @Test void followsThreeStepsAndDecodesJsonEscapedFirstUrl() {
        List<String> methods=new ArrayList<>();
        BaiduImageSearchGateway gateway=new BaiduImageSearchGateway(properties(),new ObjectMapper(),(method,uri,form)->{
            methods.add(method);
            if(methods.size()==1){assertTrue(form.contains("image=https%3A%2F%2Fimages.example%2Fa.png"));return "{\"status\":0,\"data\":{\"url\":\"https://graph.baidu.com/s?sign=test\"}}";}
            if(methods.size()==2)return "<script>var data={\"firstUrl\":\"https:\\/\\/graph.baidu.com\\/ajax\\/pcsimi?sign=test\\u0026limit=30\"};</script>";
            assertEquals("sign=test&limit=30",uri.getQuery());
            return "{\"data\":{\"list\":[{\"thumbUrl\":\"https://img.example/a.png\",\"fromUrl\":\"https://source.example/a\"}]}}";
        });
        ExternalResult result=gateway.search("https://images.example/a.png",12);
        assertEquals(Arrays.asList("POST","GET","GET"),methods);assertEquals("COMPLETE",result.getStatus());assertEquals(1,result.getResults().size());
    }
    @Test void verificationPageFallsBackToOpeningProviderWithoutInventingResults(){
        int[] calls={0};BaiduImageSearchGateway gateway=new BaiduImageSearchGateway(properties(),new ObjectMapper(),(method,uri,form)->++calls[0]==1?
                "{\"status\":0,\"data\":{\"url\":\"/s?sign=test\"}}":"<html>verification required</html>");
        ExternalResult result=gateway.search("https://images.example/a.png",12);assertEquals("OPEN_PROVIDER",result.getStatus());assertTrue(result.getResults().isEmpty());assertEquals(2,calls[0]);
    }
    @Test void decodesWholeEncodedPageUrl(){
        int[] calls={0};BaiduImageSearchGateway gateway=new BaiduImageSearchGateway(properties(),new ObjectMapper(),(method,uri,form)->++calls[0]==1?
                "{\"status\":0,\"data\":{\"url\":\"https%3A%2F%2Fgraph.baidu.com%2Fs%3Fsign%3Da\"}}":"<html></html>");
        assertEquals("https://graph.baidu.com/s?sign=a",gateway.search("https://images.example/a.png",12).getPageUrl());
    }
    @ParameterizedTest @ValueSource(strings={"http://graph.baidu.com/s","https://graph.baidu.com.evil.example/s","https://127.0.0.1/s","https://user@graph.baidu.com/s","https://graph.baidu.com:8443/s","file:///etc/passwd","javascript:alert(1)",""})
    void rejectsArbitraryProviderHops(String url){assertThrows(BusinessException.class,()->BaiduImageSearchGateway.providerUri(url));}
    @Test void disabledMakesNoExternalCall(){ImageSearchProperties properties=new ImageSearchProperties();BaiduImageSearchGateway gateway=new BaiduImageSearchGateway(properties,new ObjectMapper(),(m,u,f)->{fail("External request while disabled");return null;});assertEquals(50311,assertThrows(BusinessException.class,()->gateway.search("https://images.example/a.png",12)).getCode());}
    @Test void timeoutHasSpecificBusinessCode(){BaiduImageSearchGateway gateway=new BaiduImageSearchGateway(properties(),new ObjectMapper(),(m,u,f)->{throw new SocketTimeoutException();});assertEquals(50411,assertThrows(BusinessException.class,()->gateway.search("https://images.example/a.png",12)).getCode());}
    @Test void failedUploadHasNoFakeSuccess(){BaiduImageSearchGateway gateway=new BaiduImageSearchGateway(properties(),new ObjectMapper(),(m,u,f)->"{\"status\":1,\"msg\":\"rejected\"}");assertEquals(50211,assertThrows(BusinessException.class,()->gateway.search("https://images.example/a.png",12)).getCode());}
    @Test void malformedPayloadHasNoParserDetailsInMessage(){BaiduImageSearchGateway gateway=new BaiduImageSearchGateway(properties(),new ObjectMapper(),(m,u,f)->"<html>private upstream diagnostics</html>");assertFalse(assertThrows(BusinessException.class,()->gateway.search("https://images.example/a.png",12)).getMessage().contains("diagnostics"));}
    @Test void sanitizesAndDeduplicatesLinksAndAppliesLimit(){
        int[] calls={0};String row="{\"thumbUrl\":\"https://img.example/a.png\",\"fromUrl\":\"https://source.example/a\"}";
        BaiduImageSearchGateway gateway=new BaiduImageSearchGateway(properties(),new ObjectMapper(),(m,u,f)->{
            switch(++calls[0]){case 1:return "{\"status\":0,\"data\":{\"url\":\"/s\"}}";case 2:return "<script>{\"firstUrl\":\"/ajax/pcsimi\"}</script>";default:return "{\"data\":{\"list\":[{\"thumbUrl\":\"javascript:alert(1)\",\"fromUrl\":\"https://a.example\"},"+row+","+row+",{\"thumbUrl\":\"https://img.example/b.png\",\"fromUrl\":\"https://source.example/b\"}]}}";}
        });
        assertEquals(1,gateway.search("https://images.example/a.png",1).getResults().size());
    }
}
