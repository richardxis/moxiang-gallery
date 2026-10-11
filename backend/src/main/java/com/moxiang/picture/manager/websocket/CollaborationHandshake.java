package com.moxiang.picture.manager.websocket;

import com.moxiang.picture.exception.*;
import com.moxiang.picture.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Component
public class CollaborationHandshake implements HandshakeInterceptor {
    public static final String BINDING = "moxiang.collaboration.binding";
    private final UserService users;
    private final CollaborationAccess access;
    private final Set<String> origins;
    public CollaborationHandshake(UserService users, CollaborationAccess access,
            @Value("${picture.web.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins) {
        this.users = users; this.access = access; origins = new HashSet<>();
        for (String origin : allowedOrigins.split(",")) if (!origin.trim().isEmpty()) origins.add(origin.trim());
        if (origins.isEmpty() || origins.contains("*")) throw new IllegalArgumentException("Explicit WebSocket origins required");
    }
    public String[] allowedOrigins() { return origins.toArray(new String[0]); }
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest) || !origins.contains(request.getHeaders().getOrigin())) {
            response.setStatusCode(HttpStatus.FORBIDDEN); return false;
        }
        HttpServletRequest servlet = ((ServletServerHttpRequest) request).getServletRequest();
        String id = servlet.getParameter("pictureId");
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) { response.setStatusCode(HttpStatus.BAD_REQUEST); return false; }
        try {
            Long pictureId = Long.valueOf(id);
            attributes.put(BINDING, access.bind(servlet.getSession(false), users.getLoginUser(servlet), pictureId));
            return true;
        } catch (NumberFormatException e) { response.setStatusCode(HttpStatus.BAD_REQUEST); return false; }
        catch (BusinessException e) {
            response.setStatusCode(e.getCode() == ErrorCode.NOT_LOGIN_ERROR.getCode() ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN); return false;
        } catch (RuntimeException e) { response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE); return false; }
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) { }
}
