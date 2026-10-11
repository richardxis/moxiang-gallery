package com.moxiang.picture.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {

        /**
         * 域名
         */
        private String host;

        /**
         * secretId
         */
        @lombok.ToString.Exclude
        private String secretId;

        /**
         * 密钥（注意不要泄露）
         */
        @lombok.ToString.Exclude
        private String secretKey;

        /**
         * 区域
         */
        private String region;

        /**
         * 桶名
         */
        private String bucket;

        @Bean
        public COSClient cosClient() {
            validateConfiguration();
            // 初始化用户身份信息(secretId, secretKey)
            COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
            // 设置bucket的区域, COS地域的简称请参照 https://www.qcloud.com/document/product/436/6224
            ClientConfig clientConfig = new ClientConfig(new Region(region));
            // 生成cos客户端
            return new COSClient(cred, clientConfig);
        }

        /** Fail early with setting names only; never echo credentials or raw URLs. */
        void validateConfiguration() {
            requireSetting(host, "MOXIANG_COS_HOST");
            requireSetting(secretId, "MOXIANG_COS_SECRET_ID");
            requireSetting(secretKey, "MOXIANG_COS_SECRET_KEY");
            requireSetting(region, "MOXIANG_COS_REGION");
            requireSetting(bucket, "MOXIANG_COS_BUCKET");
            try {
                URI origin = new URI(host);
                String scheme = origin.getScheme();
                if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                        || origin.getHost() == null || origin.getUserInfo() != null
                        || origin.getQuery() != null || origin.getFragment() != null
                        || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
                    throw new IllegalStateException("MOXIANG_COS_HOST must be an HTTP(S) origin without credentials, path, query or fragment");
                }
            } catch (URISyntaxException e) {
                throw new IllegalStateException("MOXIANG_COS_HOST must be a valid HTTP(S) origin");
            }
        }

        private static void requireSetting(String value, String environmentVariable) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalStateException("Missing COS setting: " + environmentVariable
                        + "; supply an environment variable or an external private configuration");
            }
        }
    }
