package com.moxiang.picture.manager.write;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.model.dto.picture.PictureSaveRequest;
import com.moxiang.picture.model.entity.*;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Single-JVM gate. Always acquire this before a room monitor / DB row lock. */
@Component
public class PictureWriteGate {
    private final ReentrantLock[] stripes=new ReentrantLock[256];
    private final ConcurrentHashMap<Long,Fence> fences=new ConcurrentHashMap<>();
    public PictureWriteGate(){for(int i=0;i<stripes.length;i++)stripes[i]=new ReentrantLock(true);}
    private ReentrantLock lock(Long id){if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAMS_ERROR);return stripes[(id.hashCode()&Integer.MAX_VALUE)%stripes.length];}
    public <T>T with(Long id,Supplier<T> work){ReentrantLock l=lock(id);l.lock();try{return work.get();}finally{l.unlock();}}
    /** Acquire unique stripes in a global order, before taking any database locks. */
    public <T>T withMany(java.util.Collection<Long> ids,Supplier<T> work){
        java.util.SortedSet<Integer> indexes=new java.util.TreeSet<>();
        for(Long id:ids){lock(id);indexes.add((id.hashCode()&Integer.MAX_VALUE)%stripes.length);}
        java.util.List<ReentrantLock> acquired=new java.util.ArrayList<>();
        try{for(Integer index:indexes){ReentrantLock stripe=stripes[index];stripe.lock();acquired.add(stripe);}return work.get();}
        finally{for(int i=acquired.size()-1;i>=0;i--)acquired.get(i).unlock();}
    }
    public void withIo(Long id,IoWork work)throws IOException{ReentrantLock l=lock(id);l.lock();try{work.run();}finally{l.unlock();}}
    public interface IoWork{void run()throws IOException;}
    public interface Fence{
        void ordinary();
        Snapshot validate(PictureSaveRequest request,User user,HttpSession session);
        void committed(Picture picture,String requestId);
        void deleted();
    }
    public static final class Snapshot{
        public final int rotation;public final double scale;public final long revision;
        public Snapshot(int rotation,double scale,long revision){this.rotation=rotation;this.scale=scale;this.revision=revision;}
    }
    public void register(Long id,Fence fence){if(!lock(id).isHeldByCurrentThread())throw new IllegalStateException("gate required");fences.put(id,fence);}
    public void unregister(Long id,Fence fence){fences.remove(id,fence);}
    public void ordinary(Long id){if(!lock(id).isHeldByCurrentThread())throw new IllegalStateException("gate required");Fence f=fences.get(id);if(f!=null)f.ordinary();}
    public Snapshot validate(PictureSaveRequest r,User user,HttpSession session){if(!lock(r.getId()).isHeldByCurrentThread())throw new IllegalStateException("gate required");Fence f=fences.get(r.getId());if(f==null)throw new BusinessException(ErrorCode.CONFLICT_ERROR,"协作连接已失效");return f.validate(r,user,session);}
    public void committed(Picture p,String requestId){Fence f=fences.get(p.getId());if(f!=null)f.committed(p,requestId);}
    public void deleted(Long id){Fence f=fences.get(id);if(f!=null)f.deleted();}
    public static long revision(Picture p){return p.getRevision()==null?0:p.getRevision();}
    public static void expected(Picture p,Long revision){if(revision==null||revision<0)throw new BusinessException(ErrorCode.PARAMS_ERROR,"请携带图片 expectedRevision");if(revision(p)!=revision)throw new BusinessException(ErrorCode.CONFLICT_ERROR);}
    public static long next(Picture p){long n=revision(p);if(n==Long.MAX_VALUE)throw new BusinessException(ErrorCode.CONFLICT_ERROR,"图片版本已达上限");return n+1;}
}
