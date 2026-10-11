package com.moxiang.picture.api.aliyunai;

import com.fasterxml.jackson.databind.JsonNode;
import com.moxiang.picture.config.OutPaintingProperties;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import com.moxiang.picture.model.dto.picture.OutPaintingRequest;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.*;

/** Same asynchronous model/API as the prototype; no implicit retry of billed POSTs. */
@Component
public class AliyunOutPaintingGateway implements OutPaintingGateway {
    private final OutPaintingProperties properties;
    private final RestTemplate http;
    @org.springframework.beans.factory.annotation.Autowired
    public AliyunOutPaintingGateway(OutPaintingProperties properties) {
        this(properties, buildHttp(properties));
    }
    AliyunOutPaintingGateway(OutPaintingProperties properties, RestTemplate http) {
        this.properties = properties;
        this.http = http;
    }
    private static RestTemplate buildHttp(OutPaintingProperties properties) {
        if (properties.getConnectTimeoutMillis() < 1 || properties.getReadTimeoutMillis() < 1)
            throw new IllegalArgumentException("AI timeout must be positive");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return new RestTemplate(factory);
    }
    @Override public void requireEnabled() {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().trim().isEmpty())
            throw new BusinessException(ErrorCode.AI_DISABLED, "AI 扩图未配置或未启用");
        URI base;
        try { base = URI.create(properties.getBaseUrl()); }
        catch (Exception e) { throw new BusinessException(ErrorCode.AI_DISABLED, "AI 服务地址配置错误"); }
        String host = base.getHost();
        if (!"https".equals(base.getScheme()) || host == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null
                || (base.getPort() != -1 && base.getPort() != 443)
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))
                || !("dashscope.aliyuncs.com".equals(host)
                    || host.matches("[A-Za-z0-9-]+\\.cn-beijing\\.maas\\.aliyuncs\\.com")))
            throw new BusinessException(ErrorCode.AI_DISABLED, "仅允许北京地域百炼 HTTPS 服务地址");
    }
    @Override public Result create(String imageUrl, OutPaintingRequest.Parameters parameters) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "image-out-painting");
        body.put("input", Collections.singletonMap("image_url", imageUrl));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("angle", parameters.getAngle());
        if (parameters.getOutputRatio() != null) options.put("output_ratio", parameters.getOutputRatio());
        if (parameters.getXScale() != null) options.put("x_scale", parameters.getXScale());
        if (parameters.getYScale() != null) options.put("y_scale", parameters.getYScale());
        options.put("limit_image_size", true);
        options.put("add_watermark", true);
        body.put("parameters", options);
        return exchange("/api/v1/services/aigc/image2image/out-painting", HttpMethod.POST, body);
    }
    @Override public Result query(String taskId) {
        if (taskId == null || !taskId.matches("[A-Za-z0-9-]{1,128}"))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务 ID 无效");
        return exchange("/api/v1/tasks/" + taskId, HttpMethod.GET, null);
    }
    private Result exchange(String path, HttpMethod method, Object body) {
        requireEnabled();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (method == HttpMethod.POST) headers.set("X-DashScope-Async", "enable");
        String base = properties.getBaseUrl().replaceAll("/$", "");
        try {
            JsonNode root = http.exchange(base + path, method, new HttpEntity<>(body, headers), JsonNode.class).getBody();
            if (root == null || root.hasNonNull("code") || !root.path("output").isObject())
                throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "AI 服务返回异常");
            JsonNode output = root.path("output");
            Result result = new Result();
            result.setTaskId(output.path("task_id").asText());
            result.setStatus(output.path("task_status").asText());
            if (!result.getTaskId().matches("[A-Za-z0-9-]{1,128}") || !Arrays.asList(
                    "PENDING", "RUNNING", "SUSPENDED", "SUCCEEDED", "FAILED", "CANCELED", "UNKNOWN").contains(result.getStatus()))
                throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "AI 任务响应格式异常");
            if ("SUCCEEDED".equals(result.getStatus())) {
                String url = output.path("output_image_url").asText();
                if (!safeResultUrl(url)) throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "AI 结果地址异常");
                result.setImageUrl(url);
            }
            return result;
        } catch (ResourceAccessException e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof SocketTimeoutException)
                    throw new BusinessException(ErrorCode.AI_TIMEOUT, "AI 请求超时，提交结果可能未知，请勿自动重试创建");
                cause = cause.getCause();
            }
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "AI 服务暂不可用");
        } catch (RestClientException e) {
            // Do not expose vendor payloads, API keys or input URLs via logs/errors.
            throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "AI 服务调用失败");
        }
    }
    private boolean safeResultUrl(String url) {
        try {
            URI uri = URI.create(url);
            return url.length() <= 2048 && "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                    && uri.getHost() != null && uri.getHost().endsWith(".aliyuncs.com");
        } catch (Exception e) { return false; }
    }
}
