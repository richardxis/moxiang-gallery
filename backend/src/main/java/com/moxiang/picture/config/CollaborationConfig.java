package com.moxiang.picture.config;
import com.moxiang.picture.manager.websocket.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@EnableScheduling
public class CollaborationConfig implements WebSocketConfigurer {
    private final CollaborationHandler handler;
    private final CollaborationHandshake handshake;
    private final CollaborationHub hub;
    public CollaborationConfig(CollaborationHandler handler, CollaborationHandshake handshake, CollaborationHub hub) { this.handler = handler; this.handshake = handshake; this.hub = hub; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/picture/edit").addInterceptors(handshake).setAllowedOrigins(handshake.allowedOrigins());
    }
    @Scheduled(fixedDelay = 5000) public void sweep() { hub.sweep(); }
}
