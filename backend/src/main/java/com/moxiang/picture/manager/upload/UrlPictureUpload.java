package com.moxiang.picture.manager.upload;

import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

/** 默认关闭远程导入，仅允许配置的精确域名，并禁止跟随重定向。 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    @Value("${picture.remote.allowed-hosts:}") private String allowedHosts;
    @Override protected void validate(Object source) { validateUrl(source); }
    public void requireEnabled() {
        if (allowedHosts == null || allowedHosts.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 导入未启用，请配置 picture.remote.allowed-hosts");
        }
    }
    URI validateUrl(Object source) {
        try {
            if (!(source instanceof String) || ((String) source).trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址不能为空");
            }
            URI uri = new URI(((String) source).trim());
            String scheme = uri.getScheme(), host = uri.getHost();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || host == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != ("https".equalsIgnoreCase(scheme) ? 443 : 80))) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅允许标准 HTTP(S) 图片地址");
            }
            Set<String> allowed = Arrays.stream(allowedHosts.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            if (!allowed.contains(host.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片来源域名未启用，请配置允许的域名");
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isPublicAddress(address)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "不允许访问内部网络地址");
            }
            return uri;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址无效或无法解析"); }
    }
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) return (bytes[0] & 0xfe) != 0xfc;
        int a = bytes[0] & 255, b = bytes[1] & 255;
        return a != 0 && a < 224 && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 198 && (b == 18 || b == 19));
    }
    @Override protected String originalName(Object source) {
        String path = URI.create(((String) source).trim()).getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        return name.isEmpty() ? "remote-picture" : name;
    }
    @Override protected void writeSource(Object source, File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) validateUrl(source).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        try {
            if (connection.getResponseCode() != 200) throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片来源返回失败或重定向");
            String type = connection.getContentType();
            if (type == null || !Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp")
                    .contains(type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "远程资源不是支持的图片类型");
            }
            if (connection.getContentLengthLong() > MAX_BYTES) throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片超过 2MB");
            try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(destination)) {
                copyBounded(input, output, deadline);
            }
        } finally { connection.disconnect(); }
    }
    static void copyBounded(InputStream input, OutputStream output, long deadline) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int length;
        while ((length = input.read(buffer)) != -1) {
            total += length;
            if (total > MAX_BYTES) throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片超过 2MB");
            if (System.nanoTime() > deadline) throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片下载超时");
            output.write(buffer, 0, length);
        }
    }
}
