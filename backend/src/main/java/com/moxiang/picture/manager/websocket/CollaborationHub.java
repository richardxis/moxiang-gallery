package com.moxiang.picture.manager.websocket;

import com.fasterxml.jackson.databind.*;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.model.dto.picture.PictureSaveRequest;
import com.moxiang.picture.model.entity.*;
import javax.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Single JVM only. Room monitor serializes events; lease belongs to a connection, not a user. */
@Component
public class CollaborationHub {
    private static final long LEASE = TimeUnit.SECONDS.toNanos(60), IDLE = TimeUnit.SECONDS.toNanos(90);
    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    private final CollaborationAccess access;
    private final ObjectMapper json;
    private final LongSupplier clock;
    private final PictureWriteGate gate;
    @org.springframework.beans.factory.annotation.Autowired
    public CollaborationHub(CollaborationAccess access, ObjectMapper json,PictureWriteGate gate) { this(access, json, System::nanoTime,gate); }
    CollaborationHub(CollaborationAccess access, ObjectMapper json, LongSupplier clock) { this(access,json,clock,new PictureWriteGate()); }
    CollaborationHub(CollaborationAccess access,ObjectMapper json,LongSupplier clock,PictureWriteGate gate) { this.access = access; this.json = json; this.clock = clock;this.gate=gate; }
    static final class Client {
        final WebSocketSession transport, sender;
        final CollaborationAccess.Binding binding;
        long lastSeen, rateStart; int rateCount;
        Client(WebSocketSession transport, CollaborationAccess.Binding binding, long now) {
            this.transport = transport; this.binding = binding; lastSeen = rateStart = now;
            sender = new ConcurrentWebSocketSessionDecorator(transport, 1000, 64 * 1024);
        }
    }
    static final class Room {
        final String epoch = UUID.randomUUID().toString();
        final Map<String, Client> clients = new LinkedHashMap<>();
        String owner, token; long deadline, version; int rotation; double scale = 1;
        long pictureRevision;String imageUrl,thumbnailUrl;PictureWriteGate.Fence fence;
    }
    public void join(WebSocketSession session) throws IOException {
        gate.withIo(binding(session).pictureId,()->joinLocked(session));
    }
    private void joinLocked(WebSocketSession session) throws IOException {
        CollaborationAccess.Binding binding = binding(session); access.verify(binding);
        Picture initial=access.picture(binding);
        while (true) {
            Room room;
            synchronized (rooms) {
                if (!rooms.containsKey(binding.pictureId) && rooms.size() >= 256) { session.close(CloseStatus.POLICY_VIOLATION); return; }
                room = rooms.get(binding.pictureId);
                if(room==null){room=new Room();pictureState(room,initial);room.fence=new RoomFence(binding.pictureId,room);rooms.put(binding.pictureId,room);gate.register(binding.pictureId,room.fence);}
            }
            synchronized (room) {
                if (rooms.get(binding.pictureId) != room) continue;
                if (room.clients.size() >= 16) { session.close(CloseStatus.POLICY_VIOLATION); return; }
                Client client = new Client(session, binding, clock.getAsLong()); room.clients.put(session.getId(), client);
                send(client, message(room, "WELCOME", session.getId()));
                broadcast(room, "STATE");
                return;
            }
        }
    }
    public void receive(WebSocketSession session, TextMessage message) {
        gate.with(binding(session).pictureId,()->{receiveLocked(session,message);return null;});
    }
    private void receiveLocked(WebSocketSession session,TextMessage message) {
        Room room = rooms.get(binding(session).pictureId); if (room == null) return;
        synchronized (room) {
            Client client = room.clients.get(session.getId()); if (client == null) return;
            try {
                access.verify(client.binding);
                long now = clock.getAsLong();
                if (now - client.rateStart >= TimeUnit.SECONDS.toNanos(1)) { client.rateStart = now; client.rateCount = 0; }
                if (++client.rateCount > 20) { evict(room, client, CloseStatus.POLICY_VIOLATION); broadcast(room, "STATE"); return; }
                if (message.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048) throw new IllegalArgumentException("消息过大");
                JsonNode event = json.readTree(message.getPayload());
                if (event == null || !event.isObject() || !event.path("type").isTextual()) throw new IllegalArgumentException("消息格式错误");
                expire(room, now); client.lastSeen = now;
                String type = event.path("type").asText();
                switch (type) {
                    case "PING": send(client, message(room, "PONG", null)); break;
                    case "SYNC": send(client, message(room, "STATE", null)); break;
                    case "ENTER_EDIT":
                        if (room.owner != null && !session.getId().equals(room.owner)) throw new IllegalArgumentException("另一连接正在编辑");
                        if (room.owner == null) { room.owner = session.getId(); room.token = UUID.randomUUID().toString(); room.version++; }
                        room.deadline = now + LEASE;
                        Map<String, Object> grant = message(room, "LOCK_GRANTED", null); grant.put("lockToken", room.token); send(client, grant); broadcast(room, "STATE"); break;
                    case "RENEW": requireOwner(room, session, event); room.deadline = now + LEASE; send(client, message(room, "RENEWED", null)); break;
                    case "EXIT_EDIT": requireOwner(room, session, event); release(room); broadcast(room, "STATE"); break;
                    case "EDIT_ACTION":
                        requireOwner(room, session, event);
                        if (!event.path("version").isTextual() || !Long.toString(room.version).equals(event.path("version").asText())) throw new IllegalArgumentException("版本过期，请同步后重试");
                        if (!room.epoch.equals(event.path("epoch").asText())) throw new IllegalArgumentException("房间已变化");
                        action(room, event.path("action").asText()); room.version++; broadcast(room, "EDIT_ACTION"); break;
                    default: throw new IllegalArgumentException("未知事件类型");
                }
            } catch (BusinessException e) { evict(room, client, CloseStatus.POLICY_VIOLATION); broadcast(room, "STATE"); }
            catch (IllegalArgumentException | IOException e) { Map<String, Object> error = message(room, "ERROR", null); error.put("message", e instanceof IOException ? "消息解析失败" : e.getMessage()); send(client, error); }
            catch (RuntimeException e) { evict(room, client, CloseStatus.SERVER_ERROR); broadcast(room, "STATE"); }
        }
    }
    private void action(Room room, String action) {
        switch (action) {
            case "ROTATE_LEFT": room.rotation = (room.rotation + 270) % 360; break;
            case "ROTATE_RIGHT": room.rotation = (room.rotation + 90) % 360; break;
            case "ZOOM_IN": room.scale = Math.min(3, Math.round((room.scale + 0.1) * 10) / 10.0); break;
            case "ZOOM_OUT": room.scale = Math.max(0.2, Math.round((room.scale - 0.1) * 10) / 10.0); break;
            case "RESET": room.rotation = 0; room.scale = 1; break;
            default: throw new IllegalArgumentException("无效编辑动作");
        }
    }
    private void requireOwner(Room room, WebSocketSession session, JsonNode event) {
        if (!session.getId().equals(room.owner) || room.token == null || !room.token.equals(event.path("lockToken").asText())) throw new IllegalArgumentException("未持有有效编辑锁");
    }
    private void expire(Room room, long now) { if (room.owner != null && now - room.deadline >= 0) release(room); }
    private void release(Room room) { room.owner = null; room.token = null; room.deadline = 0; room.version++; }
    private Map<String, Object> message(Room room, String type, String sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>(); payload.put("type", type); payload.put("epoch", room.epoch); payload.put("version", Long.toString(room.version));
        payload.put("rotation", room.rotation); payload.put("scale", room.scale); payload.put("ownerSessionId", room.owner);
        Client owner = room.owner == null ? null : room.clients.get(room.owner);
        payload.put("ownerUserId", owner == null ? null : owner.binding.userId.toString());
        payload.put("leaseRemainingMillis", room.owner == null ? 0 : Math.max(0, TimeUnit.NANOSECONDS.toMillis(room.deadline - clock.getAsLong())));
        payload.put("pictureRevision",Long.toString(room.pictureRevision));
        payload.put("imageUrl",room.imageUrl);payload.put("thumbnailUrl",room.thumbnailUrl);
        if (sessionId != null) payload.put("sessionId", sessionId);
        return payload;
    }
    private void broadcast(Room room, String type) {
        // Iterate snapshots because failures/revocation may remove clients and release the owner.
        for (Client client : new ArrayList<>(room.clients.values())) {
            try { access.verify(client.binding); }
            catch (RuntimeException e) { evict(room, client, CloseStatus.POLICY_VIOLATION); }
        }
        Map<String, Object> payload = message(room, type, null);
        for (Client client : new ArrayList<>(room.clients.values())) send(client, payload);
    }
    private void send(Client client, Map<String, Object> payload) {
        try { if (client.sender.isOpen()) client.sender.sendMessage(new TextMessage(json.writeValueAsString(payload))); }
        catch (IOException | RuntimeException e) { try { client.transport.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) { } }
    }
    private void evict(Room room, Client client, CloseStatus status) {
        room.clients.remove(client.transport.getId());
        if (client.transport.getId().equals(room.owner)) release(room);
        try { client.transport.close(status); } catch (IOException ignored) { }
    }
    public void leave(WebSocketSession session) {
        gate.with(binding(session).pictureId,()->{leaveLocked(session);return null;});
    }
    private void leaveLocked(WebSocketSession session) {
        CollaborationAccess.Binding binding = binding(session);
        Room room = rooms.get(binding.pictureId); if (room == null) return;
        synchronized (room) {
            room.clients.remove(session.getId()); if (session.getId().equals(room.owner)) release(room);
            if (room.clients.isEmpty()) {rooms.remove(binding.pictureId, room);gate.unregister(binding.pictureId,room.fence);} else broadcast(room, "STATE");
        }
    }
    /** Called every 5s; no assumption that a TCP close always arrives. */
    public void sweep() {
        for (Map.Entry<Long, Room> entry : new ArrayList<>(rooms.entrySet())) {
            gate.with(entry.getKey(),()->{
            Room room = entry.getValue();
            if(rooms.get(entry.getKey())!=room)return null;
            synchronized (room) {
                expire(room, clock.getAsLong());
                for (Client client : new ArrayList<>(room.clients.values())) {
                    if (!client.transport.isOpen() || clock.getAsLong() - client.lastSeen >= IDLE) evict(room, client, CloseStatus.GOING_AWAY);
                }
                if (room.clients.isEmpty()) {rooms.remove(entry.getKey(), room);gate.unregister(entry.getKey(),room.fence);} else broadcast(room, "STATE");
            }
            return null;});
        }
    }
    private void pictureState(Room room,Picture picture){room.pictureRevision=PictureWriteGate.revision(picture);room.imageUrl=picture.getUrl();room.thumbnailUrl=picture.getThumbnailUrl();}
    private final class RoomFence implements PictureWriteGate.Fence {
        private final Long pictureId;private final Room room;
        RoomFence(Long pictureId,Room room){this.pictureId=pictureId;this.room=room;}
        @Override public void ordinary(){synchronized(room){expire(room,clock.getAsLong());Client owner=room.owner==null?null:room.clients.get(room.owner);if(owner!=null){try{access.verify(owner.binding);if(!owner.transport.isOpen())evict(room,owner,CloseStatus.GOING_AWAY);}catch(RuntimeException e){evict(room,owner,CloseStatus.POLICY_VIOLATION);}}if(room.owner!=null)throw new BusinessException(ErrorCode.CONFLICT_ERROR,"图片正在协作编辑，请释放编辑锁后再修改");}}
        @Override public PictureWriteGate.Snapshot validate(PictureSaveRequest request,User user,HttpSession session){synchronized(room){
            expire(room,clock.getAsLong());Client client=room.clients.get(request.getConnectionId());
            if(client==null||session==null||user==null||!client.transport.isOpen()||!Objects.equals(client.binding.userId,user.getId())||!Objects.equals(client.binding.session.getId(),session.getId()))throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"保存身份与协作连接不一致");
            access.verify(client.binding);
            if(!Objects.equals(room.owner,request.getConnectionId())||room.token==null||!Objects.equals(room.token,request.getLockToken())||!Objects.equals(room.epoch,request.getEpoch())||!Long.toString(room.version).equals(request.getDraftVersion())||request.getExpectedRevision()==null||request.getExpectedRevision()!=room.pictureRevision)throw new BusinessException(ErrorCode.CONFLICT_ERROR,"编辑锁、草稿或图片版本已变化");
            return new PictureWriteGate.Snapshot(room.rotation,room.scale,room.pictureRevision);
        }}
        @Override public void committed(Picture picture,String requestId){synchronized(room){pictureState(room,picture);room.rotation=0;room.scale=1;if(room.owner!=null)release(room);else room.version++;Map<String,Object> packet=message(room,requestId==null?"PICTURE_CHANGED":"PICTURE_SAVED",null);if(requestId!=null)packet.put("requestId",requestId);for(Client c:new ArrayList<>(room.clients.values())){try{access.verify(c.binding);}catch(RuntimeException e){evict(room,c,CloseStatus.POLICY_VIOLATION);}}for(Client c:new ArrayList<>(room.clients.values()))send(c,packet);}}
        @Override public void deleted(){synchronized(room){for(Client c:new ArrayList<>(room.clients.values()))evict(room,c,CloseStatus.POLICY_VIOLATION);rooms.remove(pictureId,room);gate.unregister(pictureId,this);}}
    }
    private CollaborationAccess.Binding binding(WebSocketSession session) {
        Object value = session.getAttributes().get(CollaborationHandshake.BINDING);
        if (!(value instanceof CollaborationAccess.Binding)) throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        return (CollaborationAccess.Binding) value;
    }
}
