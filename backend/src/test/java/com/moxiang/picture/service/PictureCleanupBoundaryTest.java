package com.moxiang.picture.service;
import com.moxiang.picture.config.CosClientConfig;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PictureCleanupBoundaryTest {
    PictureCleanupMapper tasks;PictureMapper pictures;COSClient cos;PictureObjectCleanupService service;PictureCleanupTask task;String root="space/20/2026-10-09_00000000-0000-0000-0000-000000000007";
    @BeforeEach void setup()throws Exception{tasks=mock(PictureCleanupMapper.class);pictures=mock(PictureMapper.class);cos=mock(COSClient.class);CosClientConfig config=new CosClientConfig();config.setBucket("test-bucket");config.setHost("https://pictures.example");service=new PictureObjectCleanupService(tasks,pictures,cos,config,new ObjectMapper());when(tasks.insert(any(PictureCleanupTask.class))).thenReturn(1);when(tasks.update(any(),any())).thenReturn(1);task=service.hold(root);task.setStatus("PENDING");task.setNextRunTime(new Date(0));when(tasks.selectList(any())).thenReturn(Collections.singletonList(task));when(pictures.selectCount(any())).thenReturn(0L);}
    @Test void deletesOnlyTheFivePlannedKeys(){assertEquals(1,service.processDueTasks());verify(cos).deleteObject("test-bucket",root+".png");verify(cos,times(5)).deleteObject(anyString(),anyString());}
    @Test void liveReferencePreventsAnyDeletion(){when(pictures.selectCount(any())).thenReturn(1L);assertEquals(0,service.processDueTasks());verifyNoInteractions(cos);}
    @Test void cancelledTaskCannotBeClaimed(){task.setStatus("CANCELLED");assertEquals(0,service.processDueTasks());verifyNoInteractions(cos);}
    @Test void compareAndSetClaimFailureDoesNotDelete(){when(tasks.update(any(),any())).thenReturn(0);assertEquals(0,service.processDueTasks());verifyNoInteractions(cos);}
    @Test void unsafeKeyOrDifferentBucketDoesNotDelete(){task.setObjectKeys("[\"other/secret\"]");assertEquals(0,service.processDueTasks());verifyNoInteractions(cos);}
    @Test void failedUploadDoesNotReactivateCommittedAssets(){task.setStatus("CANCELLED");when(tasks.selectById(task.getId())).thenReturn(task);clearInvocations(tasks);service.failed(task);verify(tasks,never()).update(any(),any());}
    @Test void legacyOriginalKeyIsNotGuessed(){Picture old=new Picture();old.setUrl("https://pictures.example/"+root+"_compressed.webp");old.setThumbnailUrl("https://pictures.example/"+root+"_thumbnail.webp");List<String> keys=service.knownKeys(old);assertEquals(2,keys.size());assertFalse(keys.contains(root+".png"));}
    @Test void traversalRootIsRejectedBeforeTaskInsert(){clearInvocations(tasks);assertThrows(RuntimeException.class,()->service.hold("space/20/../../other"));verify(tasks,never()).insert(any(PictureCleanupTask.class));}
}
