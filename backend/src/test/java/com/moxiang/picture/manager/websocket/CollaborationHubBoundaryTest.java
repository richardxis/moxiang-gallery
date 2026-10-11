package com.moxiang.picture.manager.websocket;

import com.fasterxml.jackson.databind.*;
import com.moxiang.picture.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.socket.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CollaborationHubBoundaryTest {
    ObjectMapper json = new ObjectMapper(); CollaborationAccess access; CollaborationHub hub; AtomicLong now;
    WebSocketSession a, b; Map<String, List<JsonNode>> messages;
    @BeforeEach void setup() throws Exception {
        access = mock(CollaborationAccess.class); now = new AtomicLong(); hub = new CollaborationHub(access, json, now::get); messages = new ConcurrentHashMap<>();
        when(access.picture(any())).thenAnswer(call->{com.moxiang.picture.model.entity.Picture picture=new com.moxiang.picture.model.entity.Picture();picture.setId(((CollaborationAccess.Binding)call.getArgument(0)).pictureId);picture.setRevision(0L);picture.setUrl("https://pictures.example/image.webp");return picture;});
        a = client("a", 7L, 10L); b = client("b", 8L, 10L); hub.join(a); hub.join(b);
    }
    WebSocketSession client(String id, Long userId, Long pictureId) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class); when(session.getId()).thenReturn(id); when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>(); attributes.put(CollaborationHandshake.BINDING, new CollaborationAccess.Binding(new MockHttpSession(), userId, pictureId, 20L)); when(session.getAttributes()).thenReturn(attributes);
        List<JsonNode> sent = Collections.synchronizedList(new ArrayList<>()); messages.put(id, sent);
        doAnswer(call -> { sent.add(json.readTree(((TextMessage)call.getArgument(0)).getPayload())); return null; }).when(session).sendMessage(any(TextMessage.class));
        return session;
    }
    JsonNode last(WebSocketSession session) { List<JsonNode> list = messages.get(session.getId()); return list.get(list.size()-1); }
    JsonNode lastType(WebSocketSession session, String type) { List<JsonNode> list = messages.get(session.getId()); for (int i=list.size()-1; i>=0; i--) if (type.equals(list.get(i).path("type").asText())) return list.get(i); throw new AssertionError(type); }
    void event(WebSocketSession session, String type, String token, String action, JsonNode state) throws Exception {
        Map<String, Object> data = new HashMap<>(); data.put("type", type); if (token != null) data.put("lockToken", token); if (action != null) data.put("action", action);
        if (state != null) { data.put("version", state.path("version").asText()); data.put("epoch", state.path("epoch").asText()); }
        hub.receive(session, new TextMessage(json.writeValueAsString(data)));
    }
    String enter(WebSocketSession session) throws Exception { event(session, "ENTER_EDIT", null, null, null); return lastType(session, "LOCK_GRANTED").path("lockToken").asText(); }
    @Test void welcomeCarriesAuthoritativeSnapshotAndConnectionId() { assertEquals("a", lastType(a,"WELCOME").path("sessionId").asText()); assertEquals(0, last(a).path("rotation").asInt()); }
    @Test void secondConnectionCannotAcquireHeldPicture() throws Exception { enter(a); event(b,"ENTER_EDIT",null,null,null); assertEquals("ERROR",last(b).path("type").asText()); assertEquals("a",last(b).path("ownerSessionId").asText()); }
    @Test void sameUserSecondTabCannotBorrowLock() throws Exception { WebSocketSession second = client("c",7L,10L); hub.join(second); String token=enter(a); event(second,"EDIT_ACTION",token,"ROTATE_RIGHT",last(a)); assertEquals("ERROR",last(second).path("type").asText()); }
    @Test void ownerActionIsBroadcastAsSnapshotIncludingSender() throws Exception { String token=enter(a); event(a,"EDIT_ACTION",token,"ROTATE_RIGHT",last(a)); assertEquals(90,last(a).path("rotation").asInt()); assertEquals(last(a),last(b)); }
    @Test void staleVersionRejectedWithoutDoubleApply() throws Exception { String token=enter(a); JsonNode before=last(a); event(a,"EDIT_ACTION",token,"ROTATE_RIGHT",before); event(a,"EDIT_ACTION",token,"ROTATE_RIGHT",before); assertEquals("ERROR",last(a).path("type").asText()); assertEquals(90,last(a).path("rotation").asInt()); }
    @Test void wrongEpochAndTokenRejected() throws Exception { String token=enter(a); event(a,"EDIT_ACTION","wrong","ROTATE_RIGHT",last(a)); assertEquals(0,last(a).path("rotation").asInt()); JsonNode state=last(a).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)state).put("epoch","old"); event(a,"EDIT_ACTION",token,"ROTATE_RIGHT",state); assertEquals("ERROR",last(a).path("type").asText()); }
    @Test void unknownActionRejectedWithoutStateChange() throws Exception { String token=enter(a); event(a,"EDIT_ACTION",token,"UPLOAD_RAW",last(a)); assertEquals(0,last(a).path("rotation").asInt()); }
    @Test void explicitReleaseAllowsNewTokenAndOwner() throws Exception { String old=enter(a); event(a,"EXIT_EDIT",old,null,null); String fresh=enter(b); assertNotEquals(old,fresh); assertEquals("b",last(b).path("ownerSessionId").asText()); }
    @Test void delayedOldReleaseCannotReleaseAnotherOwner() throws Exception { String old=enter(a); event(a,"EXIT_EDIT",old,null,null); enter(b); event(a,"EXIT_EDIT",old,null,null); assertEquals("b",last(a).path("ownerSessionId").asText()); }
    @Test void leaseExpiresWithoutCleanDisconnect() throws Exception { enter(a); now.addAndGet(TimeUnit.SECONDS.toNanos(61)); hub.sweep(); assertTrue(last(b).path("ownerSessionId").isNull()); enter(b); assertEquals("b",last(b).path("ownerSessionId").asText()); }
    @Test void pingDoesNotRenewEditingLease() throws Exception { enter(a); now.addAndGet(TimeUnit.SECONDS.toNanos(40)); event(a,"PING",null,null,null); now.addAndGet(TimeUnit.SECONDS.toNanos(21)); hub.sweep(); assertTrue(last(a).path("ownerSessionId").isNull()); }
    @Test void validRenewExtendsLease() throws Exception { String token=enter(a); now.addAndGet(TimeUnit.SECONDS.toNanos(40)); event(a,"RENEW",token,null,null); now.addAndGet(TimeUnit.SECONDS.toNanos(21)); hub.sweep(); assertEquals("a",last(b).path("ownerSessionId").asText()); }
    @Test void expiredTokenCannotRenew() throws Exception { String token=enter(a); now.addAndGet(TimeUnit.SECONDS.toNanos(61)); event(a,"RENEW",token,null,null); assertEquals("ERROR",last(a).path("type").asText()); assertTrue(last(a).path("ownerSessionId").isNull()); }
    @Test void closingUnrelatedConnectionDoesNotUnlockOwner() throws Exception { enter(a); hub.leave(b); event(a,"SYNC",null,null,null); assertEquals("a",last(a).path("ownerSessionId").asText()); }
    @Test void closingOwnerReleasesLock() throws Exception { enter(a); hub.leave(a); assertTrue(last(b).path("ownerSessionId").isNull()); enter(b); }
    @Test void lastConnectionLeavingDropsDraftAndEpoch() throws Exception { String token=enter(a); event(a,"EDIT_ACTION",token,"ROTATE_RIGHT",last(a)); String epoch=last(a).path("epoch").asText(); hub.leave(a); hub.leave(b); hub.join(a); assertNotEquals(epoch,last(a).path("epoch").asText()); assertEquals(0,last(a).path("rotation").asInt()); }
    @Test void reconnectReceivesCurrentDraftButNotPreviousLock() throws Exception { String token=enter(a); event(a,"EDIT_ACTION",token,"ZOOM_IN",last(a)); hub.leave(a); WebSocketSession fresh=client("c",7L,10L); hub.join(fresh); assertEquals(1.1,last(fresh).path("scale").asDouble(),0.001); assertFalse(last(fresh).has("lockToken")); }
    @Test void pictureRoomsAreIndependent() throws Exception { enter(a); WebSocketSession other=client("c",8L,11L); hub.join(other); enter(other); assertEquals("c",last(other).path("ownerSessionId").asText()); assertEquals("a",last(a).path("ownerSessionId").asText()); }
    @Test void revokedConnectionIsClosedAndDoesNotReceiveAction() throws Exception { String token=enter(a); CollaborationAccess.Binding binding=(CollaborationAccess.Binding)b.getAttributes().get(CollaborationHandshake.BINDING); doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR)).when(access).verify(binding); int count=messages.get("b").size(); event(a,"EDIT_ACTION",token,"ROTATE_RIGHT",last(a)); verify(b).close(CloseStatus.POLICY_VIOLATION); assertEquals(count,messages.get("b").size()); }
    @Test void revokedOwnerIsEvictedAndLockReleased() throws Exception { enter(a); CollaborationAccess.Binding binding = (CollaborationAccess.Binding)a.getAttributes().get(CollaborationHandshake.BINDING); doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR)).when(access).verify(binding); hub.sweep(); assertTrue(last(b).path("ownerSessionId").isNull()); }
    @Test void malformedOrOversizedPayloadReturnsErrorWithoutMutation() { hub.receive(a,new TextMessage("not-json")); assertEquals("ERROR",last(a).path("type").asText()); hub.receive(a,new TextMessage(new String(new char[2049]).replace('\0','x'))); assertEquals("ERROR",last(a).path("type").asText()); hub.receive(a,new TextMessage(new String(new char[700]).replace('\0','图'))); assertEquals("消息过大",last(a).path("message").asText()); }
    @Test void messageRateBoundClosesConnection() throws Exception { for(int i=0;i<21;i++) event(a,"PING",null,null,null); verify(a).close(CloseStatus.POLICY_VIOLATION); }
    @Test void idleConnectionIsClosed() throws Exception { now.addAndGet(TimeUnit.SECONDS.toNanos(91)); hub.sweep(); verify(a).close(CloseStatus.GOING_AWAY); verify(b).close(CloseStatus.GOING_AWAY); }
    @Test void lockTokenNeverBroadcastToAnotherConnection() throws Exception { String token=enter(a); assertFalse(last(b).has("lockToken")); assertFalse(lastType(b,"WELCOME").has("lockToken")); assertEquals(token,lastType(a,"LOCK_GRANTED").path("lockToken").asText()); }
    @Test void roomCapacityRejectsSeventeenthConnection() throws Exception { for(int i=0;i<14;i++) hub.join(client("extra"+i,20L+i,10L)); WebSocketSession overflow=client("overflow",50L,10L); hub.join(overflow); verify(overflow).close(CloseStatus.POLICY_VIOLATION); assertTrue(messages.get("overflow").isEmpty()); }
    @Test void failedRecipientDoesNotPreventOtherRecipientDelivery() throws Exception { String token=enter(a); doThrow(new java.io.IOException("simulated send failure")).when(b).sendMessage(any(TextMessage.class)); event(a,"EDIT_ACTION",token,"ROTATE_RIGHT",last(a)); assertEquals(90,last(a).path("rotation").asInt()); verify(b).close(CloseStatus.SERVER_ERROR); }
    @Test void springCanConstructHubWithRuntimeDependencies() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withBean(CollaborationAccess.class,()->access).withBean(ObjectMapper.class,()->json).withBean(com.moxiang.picture.manager.write.PictureWriteGate.class).withUserConfiguration(CollaborationHub.class)
                .run(context -> { assertNull(context.getStartupFailure()); assertNotNull(context.getBean(CollaborationHub.class)); });
    }
    @Test void simultaneousLockRequestsHaveOneWinner() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(2); CountDownLatch gate=new CountDownLatch(1);
        try { Future<?> fa=workers.submit(() -> { try { gate.await(); event(a,"ENTER_EDIT",null,null,null); } catch(Exception e) { throw new RuntimeException(e); } }); Future<?> fb=workers.submit(() -> { try { gate.await(); event(b,"ENTER_EDIT",null,null,null); } catch(Exception e) { throw new RuntimeException(e); } }); gate.countDown(); fa.get(5,TimeUnit.SECONDS); fb.get(5,TimeUnit.SECONDS);
            long grants=messages.values().stream().flatMap(Collection::stream).filter(n -> "LOCK_GRANTED".equals(n.path("type").asText())).count(); assertEquals(1,grants);
        } finally { workers.shutdownNow(); }
    }
    private com.moxiang.picture.manager.write.PictureWriteGate writeGate(){return (com.moxiang.picture.manager.write.PictureWriteGate)org.springframework.test.util.ReflectionTestUtils.getField(hub,"gate");}
    private com.moxiang.picture.model.dto.picture.PictureSaveRequest ticket(WebSocketSession session,String token){
        com.moxiang.picture.model.dto.picture.PictureSaveRequest request=new com.moxiang.picture.model.dto.picture.PictureSaveRequest();request.setId(10L);request.setConnectionId(session.getId());request.setLockToken(token);request.setEpoch(last(session).path("epoch").asText());request.setDraftVersion(last(session).path("version").asText());request.setExpectedRevision(0L);return request;
    }
    @Test void validSaveTicketNeedsBoundHttpSessionAndUser() throws Exception {
        String token=enter(a);com.moxiang.picture.model.dto.picture.PictureSaveRequest request=ticket(a,token);com.moxiang.picture.model.entity.User user=new com.moxiang.picture.model.entity.User();user.setId(7L);CollaborationAccess.Binding binding=(CollaborationAccess.Binding)a.getAttributes().get(CollaborationHandshake.BINDING);
        assertEquals(0,writeGate().with(10L,()->writeGate().validate(request,user,binding.session)).revision);
        assertThrows(BusinessException.class,()->writeGate().with(10L,()->writeGate().validate(request,user,new MockHttpSession())));
        user.setId(8L);assertThrows(BusinessException.class,()->writeGate().with(10L,()->writeGate().validate(request,user,binding.session)));
    }
    @Test void ordinaryWritesAreBlockedEvenForCurrentLockOwner()throws Exception{enter(a);assertEquals(40900,assertThrows(BusinessException.class,()->writeGate().with(10L,()->{writeGate().ordinary(10L);return null;})).getCode());}
    @Test void expiredLeaseCannotSave()throws Exception{String token=enter(a);com.moxiang.picture.model.dto.picture.PictureSaveRequest request=ticket(a,token);now.addAndGet(TimeUnit.SECONDS.toNanos(61));com.moxiang.picture.model.entity.User user=new com.moxiang.picture.model.entity.User();user.setId(7L);CollaborationAccess.Binding binding=(CollaborationAccess.Binding)a.getAttributes().get(CollaborationHandshake.BINDING);assertThrows(BusinessException.class,()->writeGate().with(10L,()->writeGate().validate(request,user,binding.session)));}
    @Test void committedSaveBroadcastsNewRevisionAndReleasesLock()throws Exception{String token=enter(a);event(a,"EDIT_ACTION",token,"ROTATE_RIGHT",last(a));com.moxiang.picture.model.entity.Picture saved=new com.moxiang.picture.model.entity.Picture();saved.setId(10L);saved.setRevision(1L);saved.setUrl("https://pictures.example/new.webp");writeGate().with(10L,()->{writeGate().committed(saved,"request-id");return null;});assertEquals("PICTURE_SAVED",last(b).path("type").asText());assertEquals("1",last(b).path("pictureRevision").asText());assertEquals(0,last(b).path("rotation").asInt());assertTrue(last(b).path("ownerSessionId").isNull());assertFalse(last(b).has("lockToken"));}
    @Test void ordinaryChangeBroadcastsAndDeletionClosesRoom()throws Exception{com.moxiang.picture.model.entity.Picture saved=new com.moxiang.picture.model.entity.Picture();saved.setId(10L);saved.setRevision(3L);saved.setUrl("https://pictures.example/other.webp");writeGate().with(10L,()->{writeGate().committed(saved,null);return null;});assertEquals("PICTURE_CHANGED",last(b).path("type").asText());writeGate().with(10L,()->{writeGate().deleted(10L);return null;});verify(a).close(CloseStatus.POLICY_VIOLATION);verify(b).close(CloseStatus.POLICY_VIOLATION);}
}
