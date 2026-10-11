package com.moxiang.picture.manager.websocket;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class CollaborationHandler extends TextWebSocketHandler {
    private final CollaborationHub hub;
    public CollaborationHandler(CollaborationHub hub) { this.hub = hub; }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.setTextMessageSizeLimit(2048);
        try { hub.join(session); } catch (RuntimeException e) { session.close(CloseStatus.POLICY_VIOLATION); }
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) { hub.receive(session, message); }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { hub.leave(session); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception { hub.leave(session); session.close(CloseStatus.SERVER_ERROR); }
}
