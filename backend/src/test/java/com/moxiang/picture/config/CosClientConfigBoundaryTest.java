package com.moxiang.picture.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CosClientConfigBoundaryTest {
    private CosClientConfig valid() {
        CosClientConfig config = new CosClientConfig();
        config.setHost("https://assets.example.invalid");
        config.setSecretId("synthetic-id");
        config.setSecretKey("synthetic-key");
        config.setRegion("ap-guangzhou");
        config.setBucket("synthetic-bucket");
        return config;
    }

    @ParameterizedTest
    @ValueSource(strings = {"host", "secretId", "secretKey", "region", "bucket"})
    void missingSettingsFailWithoutDisclosingValues(String field) {
        CosClientConfig config = valid();
        switch (field) {
            case "host": config.setHost(null); break;
            case "secretId": config.setSecretId(" "); break;
            case "secretKey": config.setSecretKey(null); break;
            case "region": config.setRegion(" "); break;
            case "bucket": config.setBucket(null); break;
            default: fail("Unexpected test field");
        }
        IllegalStateException error = assertThrows(IllegalStateException.class, config::validateConfiguration);
        assertTrue(error.getMessage().contains("MOXIANG_COS_"));
        assertFalse(error.getMessage().contains("synthetic-id"));
        assertFalse(error.getMessage().contains("synthetic-key"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-url", "ftp://assets.example.invalid", "https://person:password@assets.example.invalid",
            "https://assets.example.invalid/folder", "https://assets.example.invalid?token=synthetic", "https://assets.example.invalid#fragment"})
    void hostMustBeAnOriginWithoutEmbeddedSecrets(String host) {
        CosClientConfig config = valid(); config.setHost(host);
        IllegalStateException error = assertThrows(IllegalStateException.class, config::validateConfiguration);
        assertTrue(error.getMessage().contains("MOXIANG_COS_HOST"));
        assertFalse(error.getMessage().contains(host));
        assertNull(error.getCause());
    }

    @Test void validSettingsDoNotRequireACloudConnectionAndMaskCredentials() {
        CosClientConfig config = valid();
        assertDoesNotThrow(config::validateConfiguration);
        assertFalse(config.toString().contains("synthetic-id"));
        assertFalse(config.toString().contains("synthetic-key"));
    }

    @Test void malformedHostDoesNotLeakTheRawValueOrParseException() {
        CosClientConfig config = valid(); config.setHost("https://invalid host/synthetic-secret");
        IllegalStateException error = assertThrows(IllegalStateException.class, config::validateConfiguration);
        assertFalse(error.getMessage().contains("synthetic-secret"));
        assertNull(error.getCause());
    }
}
