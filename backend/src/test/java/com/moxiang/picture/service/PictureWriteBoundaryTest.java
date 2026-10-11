package com.moxiang.picture.service;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import com.moxiang.picture.model.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PictureWriteBoundaryTest {
    PictureMapper pictures;SpaceMapper rows;SpaceService spaces;UserService users;TransactionTemplate tx;PictureWriteGate gate;PictureMutationService service;User actor;Picture current;
    @BeforeEach void setup(){pictures=mock(PictureMapper.class);rows=mock(SpaceMapper.class);spaces=mock(SpaceService.class);users=mock(UserService.class);tx=mock(TransactionTemplate.class);gate=new PictureWriteGate();actor=new User();actor.setId(7L);current=new Picture();current.setId(10L);current.setSpaceId(20L);current.setUserId(7L);current.setRevision(2L);when(users.getById(7L)).thenReturn(actor);when(pictures.selectById(10L)).thenReturn(current);when(pictures.selectForUpdate(10L)).thenReturn(current);when(rows.selectForUpdate(20L)).thenReturn(new Space());when(tx.execute(any())).thenAnswer(c->((TransactionCallback<?>)c.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));service=new PictureMutationService(pictures,rows,spaces,users,tx,gate,mock(PictureObjectCleanupService.class),mock(PublicPictureCache.class));}
    @Test void metadataNeedsCurrentRevision(){Picture update=new Picture();update.setId(10L);assertEquals(40900,assertThrows(BusinessException.class,()->service.metadata(update,1L,actor)).getCode());verify(spaces,never()).editMetadata(any(),any(),any());}
    @Test void missingRevisionDoesNotDelete(){assertEquals(40000,assertThrows(BusinessException.class,()->service.delete(10L,null,actor)).getCode());verify(spaces,never()).deletePicture(any(),any());}
    @Test void activeCollaborationBlocksOrdinaryMutation(){gate.with(10L,()->{gate.register(10L,new PictureWriteGate.Fence(){public void ordinary(){throw new BusinessException(ErrorCode.CONFLICT_ERROR);}public PictureWriteGate.Snapshot validate(com.moxiang.picture.model.dto.picture.PictureSaveRequest r,User u,javax.servlet.http.HttpSession s){return null;}public void committed(Picture p,String id){}public void deleted(){}});return null;});Picture update=new Picture();update.setId(10L);assertThrows(BusinessException.class,()->service.metadata(update,2L,actor));assertThrows(BusinessException.class,()->service.delete(10L,2L,actor));assertThrows(BusinessException.class,()->service.preflight(current,2L,actor));verifyNoInteractions(tx);}
    @Test void metadataBumpsPersistentRevision(){Picture update=new Picture();update.setId(10L);update.setName("new");assertTrue(service.metadata(update,2L,actor));assertEquals(3L,update.getRevision());verify(spaces).editMetadata(update,20L,actor);}
    @Test void finalScopeChangeIsRejected(){Picture moved=new Picture();moved.setId(10L);moved.setSpaceId(30L);moved.setRevision(2L);when(pictures.selectForUpdate(10L)).thenReturn(moved);assertEquals(40900,assertThrows(BusinessException.class,()->service.delete(10L,2L,actor)).getCode());}
    @Test void expiredAccountCannotWrite(){when(users.getById(7L)).thenReturn(null);assertEquals(40100,assertThrows(BusinessException.class,()->service.preflight(current,2L,actor)).getCode());}
    @Test void longRevisionCannotOverflow(){current.setRevision(Long.MAX_VALUE);assertThrows(BusinessException.class,()->PictureWriteGate.next(current));}
}
