package com.moxiang.picture.service;

import com.moxiang.picture.api.aliyunai.OutPaintingGateway;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.OutPaintingTaskMapper;
import com.moxiang.picture.model.dto.picture.OutPaintingRequest;
import com.moxiang.picture.model.entity.*;
import org.junit.jupiter.api.*;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OutPaintingServiceBoundaryTest {
    PictureService pictures; UserService users; OutPaintingTaskMapper tasks; OutPaintingGateway gateway;
    OutPaintingService service; User user; Picture picture; OutPaintingRequest request;
    String localId = "00000000-0000-0000-0000-000000000001";
    @BeforeEach void setup() {
        pictures = mock(PictureService.class); users = mock(UserService.class); tasks = mock(OutPaintingTaskMapper.class); gateway = mock(OutPaintingGateway.class);
        service = new OutPaintingService(pictures, users, tasks, gateway);
        user = new User(); user.setId(7L);
        picture = new Picture(); picture.setId(9L); picture.setUserId(7L); picture.setUrl("https://pictures.example/a.webp");
        picture.setPicWidth(1024); picture.setPicHeight(1024); picture.setPicSize(2048L); picture.setPicFormat("webp");
        when(pictures.getById(9L)).thenReturn(picture); when(tasks.insert(any(OutPaintingTask.class))).thenReturn(1); when(tasks.updateById(any(OutPaintingTask.class))).thenReturn(1);
        when(tasks.update(any(), any())).thenReturn(1);
        request = new OutPaintingRequest(); request.setPictureId(9L); request.setParameters(new OutPaintingRequest.Parameters()); request.getParameters().setXScale(1.5);
        when(gateway.create(anyString(), any())).thenReturn(remote("PENDING"));
    }
    OutPaintingGateway.Result remote(String status) {
        OutPaintingGateway.Result result = new OutPaintingGateway.Result(); result.setTaskId("vendor-123"); result.setStatus(status); return result;
    }
    OutPaintingTask existing(String status) {
        OutPaintingTask task = new OutPaintingTask(); task.setId(localId); task.setUserId(7L); task.setPictureId(9L);
        task.setRemoteTaskId("vendor-123"); task.setStatus(status); task.setCreateTime(new Date()); when(tasks.selectById(localId)).thenReturn(task); return task;
    }
    @Test void createsOwnedLocalTaskBeforeSubmission() {
        String id = service.create(request, user).getTaskId(); assertEquals(36, id.length()); assertNotEquals("vendor-123", id);
        org.mockito.InOrder order = inOrder(tasks, gateway); order.verify(gateway).requireEnabled(); order.verify(tasks).insert(any(OutPaintingTask.class));
        order.verify(gateway).create(eq(picture.getUrl()), any()); order.verify(tasks).updateById(any(OutPaintingTask.class));
    }
    @Test void unauthorizedSourceCannotCauseBilling() {
        picture.setUserId(8L); assertThrows(BusinessException.class, () -> service.create(request, user)); verifyNoInteractions(gateway, tasks);
    }
    @Test void spaceSourceFailsClosed() {
        picture.setSpaceId(10L); assertThrows(BusinessException.class, () -> service.create(request, user)); verifyNoInteractions(gateway, tasks);
    }
    @Test void rejectsNoOpAndMixedModes() {
        request.getParameters().setXScale(1.0); assertThrows(BusinessException.class, () -> service.create(request, user));
        request.getParameters().setXScale(1.5); request.getParameters().setOutputRatio("16:9"); assertThrows(BusinessException.class, () -> service.create(request, user));
        verifyNoInteractions(gateway, tasks);
    }
    @Test void rejectsNonFiniteScaleAndBadRatio() {
        request.getParameters().setXScale(Double.NaN); assertThrows(BusinessException.class, () -> service.create(request, user));
        request.getParameters().setXScale(null); request.getParameters().setOutputRatio("8:7"); assertThrows(BusinessException.class, () -> service.create(request, user));
    }
    @Test void rejectsInvalidSizeFormatAndAngle() {
        picture.setPicWidth(511); assertThrows(BusinessException.class, () -> service.create(request, user));
        picture.setPicWidth(1024); picture.setPicFormat("svg"); assertThrows(BusinessException.class, () -> service.create(request, user));
        picture.setPicFormat("png"); request.getParameters().setAngle(360); assertThrows(BusinessException.class, () -> service.create(request, user));
    }
    @Test void noSubmissionWhenTaskPersistenceFails() {
        when(tasks.insert(any(OutPaintingTask.class))).thenReturn(0); assertThrows(BusinessException.class, () -> service.create(request, user)); verify(gateway, never()).create(any(), any());
    }
    @Test void recordsUncertainSubmissionWithoutRetry() {
        when(gateway.create(anyString(), any())).thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));
        assertThrows(BusinessException.class, () -> service.create(request, user));
        verify(tasks).updateById(org.mockito.ArgumentMatchers.<OutPaintingTask>argThat(task -> "SUBMISSION_UNKNOWN".equals(task.getStatus()))); verify(gateway, times(1)).create(any(), any());
    }
    @Test void otherUserCannotQueryTask() {
        existing("PENDING"); user.setId(8L); assertThrows(BusinessException.class, () -> service.query(localId, user)); verifyNoInteractions(gateway);
    }
    @Test void terminalStatusAvoidsRemoteRequest() { existing("SUCCEEDED"); assertEquals("SUCCEEDED", service.query(localId, user).getTaskStatus()); verifyNoInteractions(gateway); }
    @Test void pollsAndPersistsSuccessfulResult() {
        existing("RUNNING"); OutPaintingGateway.Result result = remote("SUCCEEDED"); result.setImageUrl("https://result.oss-cn-beijing.aliyuncs.com/a.png");
        when(gateway.query("vendor-123")).thenReturn(result);
        assertEquals(result.getImageUrl(), service.query(localId, user).getOutputImageUrl()); verify(tasks).update(any(), any());
    }
    @Test void rejectsMismatchedVendorTask() {
        existing("PENDING"); OutPaintingGateway.Result result = remote("RUNNING"); result.setTaskId("someone-else"); when(gateway.query("vendor-123")).thenReturn(result);
        assertThrows(BusinessException.class, () -> service.query(localId, user)); verify(tasks, never()).update(any(), any());
    }
    @Test void expiresOldTasksWithoutRemoteRequest() {
        existing("RUNNING").setCreateTime(new Date(System.currentTimeMillis() - 25L * 60 * 60 * 1000));
        assertEquals("EXPIRED", service.query(localId, user).getTaskStatus()); verifyNoInteractions(gateway);
    }
    @Test void rereadsTerminalStatusAfterConcurrentUpdate() {
        OutPaintingTask pending = existing("PENDING"); OutPaintingTask done = new OutPaintingTask(); done.setId(localId); done.setStatus("SUCCEEDED");
        when(tasks.selectById(localId)).thenReturn(pending, done); when(tasks.update(any(), any())).thenReturn(0); when(gateway.query("vendor-123")).thenReturn(remote("RUNNING"));
        assertEquals("SUCCEEDED", service.query(localId, user).getTaskStatus());
    }
    @Test void loginRequiredForCreationAndQuery() {
        assertThrows(BusinessException.class, () -> service.create(request, null)); assertThrows(BusinessException.class, () -> service.query(localId, null)); verifyNoInteractions(gateway);
    }
    @Test void rejectsDeletedSourceOnQuery() {
        existing("SUCCEEDED"); when(pictures.getById(9L)).thenReturn(null); assertThrows(BusinessException.class, () -> service.query(localId, user)); verifyNoInteractions(gateway);
    }
}
