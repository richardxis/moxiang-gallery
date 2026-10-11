package com.moxiang.picture.api.aliyunai;

import com.moxiang.picture.config.OutPaintingProperties;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.model.dto.picture.OutPaintingRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import java.net.SocketTimeoutException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OutPaintingGatewayBoundaryTest {
    OutPaintingProperties properties;
    AliyunOutPaintingGateway gateway;
    MockRestServiceServer server;
    final String base = "https://dashscope.aliyuncs.com";
    @BeforeEach void setup() {
        properties = new OutPaintingProperties(); properties.setEnabled(true); properties.setApiKey("test-key-not-real");
        RestTemplate http = new RestTemplate(); server = MockRestServiceServer.bindTo(http).build();
        gateway = new AliyunOutPaintingGateway(properties, http);
    }
    OutPaintingRequest.Parameters parameters() {
        OutPaintingRequest.Parameters parameters = new OutPaintingRequest.Parameters(); parameters.setXScale(1.5); return parameters;
    }
    @Test void sendsSnakeCaseAndAsyncHeaders() {
        server.expect(requestTo(base + "/api/v1/services/aigc/image2image/out-painting"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-DashScope-Async", "enable"))
                .andExpect(header("Authorization", "Bearer test-key-not-real"))
                .andExpect(content().json("{\"model\":\"image-out-painting\",\"input\":{\"image_url\":\"https://pictures.example/a.webp\"},\"parameters\":{\"x_scale\":1.5,\"add_watermark\":true,\"limit_image_size\":true}}"))
                .andRespond(withSuccess("{\"output\":{\"task_id\":\"vendor-123\",\"task_status\":\"PENDING\"}}", MediaType.APPLICATION_JSON));
        assertEquals("PENDING", gateway.create("https://pictures.example/a.webp", parameters()).getStatus()); server.verify();
    }
    @Test void parsesSuccessfulResult() {
        respond("{\"output\":{\"task_id\":\"vendor-123\",\"task_status\":\"SUCCEEDED\",\"output_image_url\":\"https://result.oss-cn-beijing.aliyuncs.com/a.png\"}}");
        assertTrue(gateway.query("vendor-123").getImageUrl().endsWith("a.png")); server.verify();
    }
    @Test void mapsHttpFailureWithoutLeakingBody() {
        server.expect(requestTo(base + "/api/v1/tasks/vendor-123")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("vendor-private-data"));
        BusinessException exception = assertThrows(BusinessException.class, () -> gateway.query("vendor-123"));
        assertEquals(ErrorCode.AI_UPSTREAM_ERROR.getCode(), exception.getCode()); assertFalse(exception.getMessage().contains("private")); server.verify();
    }
    @Test void rejectsVendorErrorEvenOnHttp200() {
        respond("{\"code\":\"InvalidApiKey\",\"message\":\"private message\"}");
        assertThrows(BusinessException.class, () -> gateway.query("vendor-123")); server.verify();
    }
    @Test void rejectsMissingTaskOutput() { respond("{}"); assertThrows(BusinessException.class, () -> gateway.query("vendor-123")); server.verify(); }
    @Test void rejectsUnrecognizedStatus() {
        respond("{\"output\":{\"task_id\":\"vendor-123\",\"task_status\":\"NEW_VENDOR_STATE\"}}");
        assertThrows(BusinessException.class, () -> gateway.query("vendor-123")); server.verify();
    }
    @Test void rejectsUnsafeResultUrl() {
        respond("{\"output\":{\"task_id\":\"vendor-123\",\"task_status\":\"SUCCEEDED\",\"output_image_url\":\"javascript:alert(1)\"}}");
        assertThrows(BusinessException.class, () -> gateway.query("vendor-123")); server.verify();
    }
    @Test void classifiesTimeoutAndDoesNotRetry() {
        server.expect(requestTo(base + "/api/v1/tasks/vendor-123"))
                .andRespond(request -> { throw new SocketTimeoutException("test timeout"); });
        BusinessException exception = assertThrows(BusinessException.class, () -> gateway.query("vendor-123"));
        assertEquals(ErrorCode.AI_TIMEOUT.getCode(), exception.getCode()); server.verify();
    }
    @Test void rejectsPathInjectionBeforeNetwork() { assertThrows(BusinessException.class, () -> gateway.query("../secrets")); server.verify(); }
    @Test void disabledConfigurationNeverCallsNetwork() {
        properties.setEnabled(false);
        assertEquals(ErrorCode.AI_DISABLED.getCode(), assertThrows(BusinessException.class,
                () -> gateway.create("https://pictures.example/a.webp", parameters())).getCode()); server.verify();
    }
    @Test void rejectsUntrustedConfiguredHost() {
        properties.setBaseUrl("https://attacker.example"); assertThrows(BusinessException.class, () -> gateway.requireEnabled()); server.verify();
    }
    @Test void acceptsWorkspaceHost() {
        properties.setBaseUrl("https://workspace123.cn-beijing.maas.aliyuncs.com"); assertDoesNotThrow(() -> gateway.requireEnabled());
    }
    void respond(String json) { server.expect(requestTo(base + "/api/v1/tasks/vendor-123")).andRespond(withSuccess(json, MediaType.APPLICATION_JSON)); }
}
