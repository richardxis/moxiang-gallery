package com.moxiang.picture.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.model.dto.file.UploadPictureResult;
import com.moxiang.picture.model.dto.picture.PictureSaveRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.PictureVO;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.transaction.support.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CollaborativeSaveBoundaryTest {
    PictureMutationService mutations;SpaceService spaces;PictureAssetService assets;PictureObjectCleanupService cleanup;PictureEditSaveMapper saves;PictureMapper pictures;TransactionTemplate tx;PictureWriteGate gate;CollaborativePictureService service;
    User actor;MockHttpSession session;MockMultipartFile file;PictureSaveRequest request;PictureAssetService.Prepared prepared;AtomicReference<Picture> database;Map<String,PictureEditSave> operations;AtomicInteger validations,commits;int failAt;
    @BeforeEach void setup()throws Exception{
        mutations=mock(PictureMutationService.class);spaces=mock(SpaceService.class);assets=mock(PictureAssetService.class);cleanup=mock(PictureObjectCleanupService.class);saves=mock(PictureEditSaveMapper.class);pictures=mock(PictureMapper.class);tx=mock(TransactionTemplate.class);gate=new PictureWriteGate();operations=new HashMap<>();validations=new AtomicInteger();commits=new AtomicInteger();
        actor=new User();actor.setId(7L);session=new MockHttpSession();Picture current=new Picture();current.setId(10L);current.setSpaceId(20L);current.setUserId(7L);current.setRevision(0L);current.setPicWidth(8);current.setPicHeight(6);current.setPicSize(100L);current.setUrl("https://pictures.example/old.webp");database=new AtomicReference<>(current);
        when(mutations.require(10L)).thenAnswer(c->database.get());when(mutations.lockCurrent(any())).thenAnswer(c->database.get());Space team=new Space();team.setSpaceType(1);when(spaces.requirePermission(eq(20L),eq(actor),anyString())).thenReturn(team);
        gate.with(10L,()->{gate.register(10L,new PictureWriteGate.Fence(){public void ordinary(){throw new BusinessException(ErrorCode.CONFLICT_ERROR);}public PictureWriteGate.Snapshot validate(PictureSaveRequest r,User u,javax.servlet.http.HttpSession h){if(failAt>0&&validations.incrementAndGet()>=failAt)throw new BusinessException(ErrorCode.CONFLICT_ERROR);return new PictureWriteGate.Snapshot(0,1,0);}public void committed(Picture p,String id){commits.incrementAndGet();}public void deleted(){}});return null;});
        PictureCleanupTask task=new PictureCleanupTask();task.setId("upload-task");prepared=new PictureAssetService.Prepared("space/20","space/20/2026-10-09_00000000-0000-0000-0000-000000000007",task);when(assets.prepare(anyString())).thenReturn(prepared);
        UploadPictureResult result=new UploadPictureResult();result.setUrl("https://pictures.example/new.webp");result.setThumbnailUrl(result.getUrl());result.setPicWidth(8);result.setPicHeight(6);result.setPicSize(80L);result.setPicFormat("webp");when(assets.upload(any(),eq(prepared))).thenReturn(result);
        doAnswer(c->{database.set(c.getArgument(0));return null;}).when(spaces).saveUploaded(any(),any(),eq(actor));
        when(saves.selectById(anyString())).thenAnswer(c->operations.get(c.getArgument(0)));when(saves.insert(any(PictureEditSave.class))).thenAnswer(c->{PictureEditSave op=c.getArgument(0);operations.put(op.getRequestId(),op);return 1;});
        when(tx.execute(any())).thenAnswer(c->{Picture old=database.get();Map<String,PictureEditSave> previous=new HashMap<>(operations);try{return ((TransactionCallback<?>)c.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));}catch(RuntimeException e){database.set(old);operations.clear();operations.putAll(previous);throw e;}});
        service=new CollaborativePictureService(mutations,spaces,assets,cleanup,saves,pictures,tx,gate,new ObjectMapper());file=png(8,6);
        request=new PictureSaveRequest();request.setId(10L);request.setExpectedRevision(0L);request.setRequestId(UUID.randomUUID().toString());request.setConnectionId("connection");request.setLockToken(UUID.randomUUID().toString());request.setEpoch(UUID.randomUUID().toString());request.setDraftVersion("1");
    }
    MockMultipartFile png(int w,int h)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB),"png",bytes);return new MockMultipartFile("file","edited.png","image/png",bytes.toByteArray());}
    @Test void savesOneRevisionAndRegistersCleanupInCommit(){PictureVO result=service.save(file,request,actor,session);assertEquals(1L,result.getRevision());assertEquals(80L,database.get().getPicSize());assertEquals(1,operations.size());assertEquals(1,commits.get());verify(cleanup).committed("upload-task");verify(cleanup).retire(any());verify(cleanup,never()).failed(any());}
    @Test void replayDoesNotUploadOrConsumeQuotaTwice(){service.save(file,request,actor,session);clearInvocations(assets,spaces);PictureVO result=service.save(file,request,actor,session);assertEquals(1L,result.getRevision());assertEquals(1,commits.get());verify(assets,never()).prepare(anyString());verify(spaces,never()).saveUploaded(any(),any(),any());}
    @Test void sameRequestWithDifferentBytesIsRejected()throws Exception{service.save(file,request,actor,session);clearInvocations(assets);MockMultipartFile different=png(8,6);byte[] bytes=different.getBytes();bytes[bytes.length-1]^=1;different=new MockMultipartFile("file","edited.png","image/png",bytes);BusinessException e=assertThrows(BusinessException.class,()->service.save(new MockMultipartFile("file","edited.png","image/png",bytes),request,actor,session));assertEquals(40900,e.getCode());verify(assets,never()).prepare(anyString());}
    @Test void staleImageRevisionFailsBeforeCloud(){request.setExpectedRevision(99L);assertEquals(40900,assertThrows(BusinessException.class,()->service.save(file,request,actor,session)).getCode());verifyNoInteractions(assets);}
    @Test void invalidLockFailsBeforeCloud(){failAt=1;assertThrows(BusinessException.class,()->service.save(file,request,actor,session));verifyNoInteractions(assets);}
    @Test void lostLeaseAfterUploadQueuesNewObjectCleanup(){failAt=2;assertThrows(BusinessException.class,()->service.save(file,request,actor,session));assertEquals(0L,database.get().getRevision());assertTrue(operations.isEmpty());verify(cleanup).failed(prepared.task);verify(cleanup,never()).committed(anyString());assertEquals(0,commits.get());}
    @Test void expiryImmediatelyBeforeCommitRollsBackAndCompensates(){failAt=3;assertThrows(BusinessException.class,()->service.save(file,request,actor,session));assertEquals(0L,database.get().getRevision());assertTrue(operations.isEmpty());verify(cleanup).failed(prepared.task);assertEquals(0,commits.get());}
    @Test void quotaFailureDoesNotPublish(){doThrow(new BusinessException(ErrorCode.OPERATION_ERROR)).when(spaces).saveUploaded(any(),any(),eq(actor));assertThrows(BusinessException.class,()->service.save(file,request,actor,session));verify(cleanup).failed(prepared.task);verify(cleanup,never()).committed(anyString());assertEquals(0,commits.get());}
    @Test void uploadFailureStillQueuesCompensation(){when(assets.upload(any(),eq(prepared))).thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR));assertThrows(BusinessException.class,()->service.save(file,request,actor,session));verify(cleanup).failed(prepared.task);verifyNoInteractions(tx);}
    @Test void malformedRequestIsRejectedBeforeFileWork(){request.setRequestId("wrong");assertThrows(BusinessException.class,()->service.save(file,request,actor,session));verifyNoInteractions(assets,spaces);}
    @Test void nonPngIsRejectedBeforeCloud(){MockMultipartFile bad=new MockMultipartFile("file","bad.png","image/png",new byte[]{1,2,3});assertThrows(BusinessException.class,()->service.save(bad,request,actor,session));verifyNoInteractions(assets);}
    @Test void partialCropIsRejected(){request.setCropWidth(8);assertThrows(BusinessException.class,()->service.save(file,request,actor,session));verifyNoInteractions(assets);}
    @Test void cropOutputDimensionsMustMatch(){request.setCropX(0);request.setCropY(0);request.setCropWidth(4);request.setCropHeight(3);assertThrows(BusinessException.class,()->service.save(file,request,actor,session));verifyNoInteractions(assets);}
    @Test void viewerCannotSave(){when(spaces.requirePermission(eq(20L),eq(actor),anyString())).thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR));assertEquals(40101,assertThrows(BusinessException.class,()->service.save(file,request,actor,session)).getCode());verifyNoInteractions(assets);}
    @Test void resultCanBeRecoveredWithoutAStillActiveLock(){service.save(file,request,actor,session);assertEquals(1L,service.result(request.getRequestId(),actor).getRevision());}
    @Test void anotherUserCannotReadOperationResult(){service.save(file,request,actor,session);User other=new User();other.setId(9L);assertEquals(40101,assertThrows(BusinessException.class,()->service.result(request.getRequestId(),other)).getCode());}
    @Test void imagePixelsAreCheckedWithoutDecodingAllPixels()throws Exception{assertThrows(BusinessException.class,()->service.save(png(8193,1),request,actor,session));verifyNoInteractions(assets);}
}
