package com.moxiang.picture.manager.write;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PictureBatchGateBoundaryTest {
    @Test void overlappingBatchesWithReverseInputOrderFinishWithoutDeadlock()throws Exception{
        PictureWriteGate gate=new PictureWriteGate();ExecutorService workers=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);AtomicInteger entries=new AtomicInteger();
        try{
            Future<?> left=workers.submit(()->{await(start);for(int i=0;i<100;i++)gate.withMany(Arrays.asList(1L,2L,257L),()->entries.incrementAndGet());});
            Future<?> right=workers.submit(()->{await(start);for(int i=0;i<100;i++)gate.withMany(Arrays.asList(257L,2L,1L),()->entries.incrementAndGet());});
            start.countDown();left.get(5,TimeUnit.SECONDS);right.get(5,TimeUnit.SECONDS);assertEquals(200,entries.get());
        }finally{workers.shutdownNow();}
    }
    @Test void failingBatchReleasesAllStripesForOtherThreads()throws Exception{
        PictureWriteGate gate=new PictureWriteGate();assertThrows(IllegalStateException.class,()->gate.withMany(Arrays.asList(1L,2L),()->{throw new IllegalStateException();}));
        ExecutorService worker=Executors.newSingleThreadExecutor();try{assertEquals(1,worker.submit(()->gate.withMany(Arrays.asList(2L,1L),()->1)).get(2,TimeUnit.SECONDS));}finally{worker.shutdownNow();}
    }
    private static void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new RuntimeException(error);}}
}
