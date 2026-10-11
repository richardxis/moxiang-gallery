package com.moxiang.picture.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.config.CosClientConfig;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.entity.*;
import com.qcloud.cos.COSClient;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/** Durable exact-key journal. Workers never delete a currently referenced asset. */
@Service @Slf4j
public class PictureObjectCleanupService implements InitializingBean,DisposableBean {
    private static final Pattern KEY=Pattern.compile("^(public|space)/[1-9][0-9]{0,18}/[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9a-f-]{36}(?:_compressed\\.webp|_thumbnail\\.webp|\\.(?:jpg|jpeg|png|webp))$");
    private final PictureCleanupMapper tasks;private final PictureMapper pictures;private final COSClient cos;private final CosClientConfig config;private final ObjectMapper json;
    @Value("${picture.cleanup.enabled:true}") private boolean enabled=true;
    @Value("${picture.cleanup.retire-delay-seconds:60}") private int retireDelay=60;
    private ScheduledExecutorService worker;
    public PictureObjectCleanupService(PictureCleanupMapper tasks,PictureMapper pictures,COSClient cos,CosClientConfig config,ObjectMapper json){this.tasks=tasks;this.pictures=pictures;this.cos=cos;this.config=config;this.json=json;}
    private Date later(long millis){return new Date(System.currentTimeMillis()+millis);}
    public static boolean safeKey(String key){return key!=null&&key.length()<=512&&KEY.matcher(key).matches();}
    public PictureCleanupTask hold(String root){return insert(config.getBucket(),Arrays.asList(root+".jpg",root+".png",root+".webp",root+"_compressed.webp",root+"_thumbnail.webp"),"HOLD",later(10*60*1000L));}
    private PictureCleanupTask insert(String bucket,Collection<String> source,String status,Date time){
        List<String> keys=new ArrayList<>(new LinkedHashSet<>(source));
        if(!Objects.equals(bucket,config.getBucket())||keys.isEmpty()||keys.size()>5||keys.stream().anyMatch(k->!safeKey(k)))throw new BusinessException(ErrorCode.PARAMS_ERROR,"对象清理范围无效");
        PictureCleanupTask task=new PictureCleanupTask();task.setId(UUID.randomUUID().toString());task.setBucket(bucket);task.setStatus(status);task.setAttempts(0);task.setNextRunTime(time);task.setCreateTime(new Date());task.setUpdateTime(new Date());
        try{task.setObjectKeys(json.writeValueAsString(keys));}catch(Exception e){throw new IllegalStateException("key encoding failed");}
        if(tasks.insert(task)!=1)throw new BusinessException(ErrorCode.OPERATION_ERROR,"对象任务登记失败");return task;
    }
    public void committed(String id){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("asset cancellation requires picture transaction");
        if(tasks.update(null,new UpdateWrapper<PictureCleanupTask>().eq("id",id).eq("status","HOLD").set("status","CANCELLED").set("updateTime",new Date()))!=1)throw new BusinessException(ErrorCode.CONFLICT_ERROR,"上传任务已过期，请重新保存");
    }
    public void failed(PictureCleanupTask upload){
        try{
            PictureCleanupTask current=tasks.selectById(upload.getId());if(current==null||"CANCELLED".equals(current.getStatus()))return;
            if("HOLD".equals(current.getStatus()))tasks.update(null,new UpdateWrapper<PictureCleanupTask>().eq("id",current.getId()).eq("status","HOLD").set("status","PENDING").set("nextRunTime",new Date()).set("updateTime",new Date()));
            else if(Arrays.asList("DONE","FAILED","RUNNING").contains(current.getStatus()))insert(upload.getBucket(),keys(upload),"PENDING",later(30000));
        }catch(RuntimeException e){log.warn("上传补偿暂不可登记，HOLD 超时任务将重查；异常类型={}",e.getClass().getSimpleName());}
    }
    public void retire(Picture picture){
        List<String> keys=knownKeys(picture);if(keys.isEmpty())return;
        String bucket=picture.getStorageBucket()==null?config.getBucket():picture.getStorageBucket();
        if(!Objects.equals(bucket,config.getBucket())){log.warn("旧对象桶与当前资源不同，需人工审阅");return;}
        insert(bucket,keys,"PENDING",later(Math.max(0,retireDelay)*1000L));
    }
    public List<String> knownKeys(Picture p){
        Set<String> keys=new LinkedHashSet<>();
        for(String key:Arrays.asList(p.getOriginalKey(),p.getCompressedKey(),p.getThumbnailKey(),keyFromUrl(p.getUrl()),keyFromUrl(p.getThumbnailUrl())))if(safeKey(key))keys.add(key);
        return new ArrayList<>(keys);
    }
    public String keyFromUrl(String url){String host=config.getHost()==null?"":config.getHost().replaceAll("/+$","");if(host.isEmpty()||url==null||!url.startsWith(host+"/"))return null;String key=url.substring(host.length()+1);return key.indexOf('?')<0&&key.indexOf('#')<0?key:null;}
    private List<String> keys(PictureCleanupTask task){try{return json.readValue(task.getObjectKeys(),new TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException("invalid key journal");}}
    public int processDueTasks(){
        Date now=new Date();
        tasks.update(null,new UpdateWrapper<PictureCleanupTask>().eq("status","RUNNING").lt("updateTime",new Date(now.getTime()-10*60*1000L)).set("status","PENDING").set("claimToken",null).set("nextRunTime",now));
        List<PictureCleanupTask> due=tasks.selectList(new QueryWrapper<PictureCleanupTask>().in("status",Arrays.asList("PENDING","HOLD")).le("nextRunTime",now).orderByAsc("nextRunTime").last("LIMIT 16"));int done=0;
        for(PictureCleanupTask task:due){String claim=UUID.randomUUID().toString();int attempts=task.getAttempts()==null?1:task.getAttempts()+1;
            if(!Arrays.asList("PENDING","HOLD").contains(task.getStatus()))continue;
            if(tasks.update(null,new UpdateWrapper<PictureCleanupTask>().eq("id",task.getId()).eq("status",task.getStatus()).le("nextRunTime",now).set("status","RUNNING").set("claimToken",claim).set("attempts",attempts).set("updateTime",now))!=1)continue;
            try{
                List<String> list=keys(task);if(!Objects.equals(config.getBucket(),task.getBucket())||list.isEmpty()||list.size()>5||list.stream().anyMatch(k->!safeKey(k)))throw new IllegalStateException("unsafe cleanup resource");
                List<String> urls=new ArrayList<>();for(String key:list)urls.add(config.getHost().replaceAll("/+$","")+"/"+key);
                Long references=pictures.selectCount(new QueryWrapper<Picture>().and(q->q.in("originalKey",list).or().in("compressedKey",list).or().in("thumbnailKey",list).or().in("url",urls).or().in("thumbnailUrl",urls)));
                if(references!=null&&references>0){finish(task,claim,"CANCELLED",null,null);continue;}
                for(String key:list)cos.deleteObject(task.getBucket(),key);
                finish(task,claim,"DONE",null,null);done++;
            }catch(RuntimeException e){finish(task,claim,attempts>=5?"FAILED":"PENDING",e.getClass().getSimpleName(),later(Math.min(300,5L*(1L<<Math.min(6,attempts)))*1000));log.warn("对象清理待重试或人工处理；异常类型={}",e.getClass().getSimpleName());}
        }return done;
    }
    private void finish(PictureCleanupTask task,String claim,String status,String error,Date next){UpdateWrapper<PictureCleanupTask> w=new UpdateWrapper<PictureCleanupTask>().eq("id",task.getId()).eq("status","RUNNING").eq("claimToken",claim).set("status",status).set("claimToken",null).set("lastErrorType",error).set("updateTime",new Date());if(next!=null)w.set("nextRunTime",next);tasks.update(null,w);}
    @Override public void afterPropertiesSet(){if(!enabled)return;worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"moxiang-object-cleanup");t.setDaemon(true);return t;});worker.scheduleWithFixedDelay(()->{try{processDueTasks();}catch(RuntimeException e){log.warn("清理扫描未完成；异常类型={}",e.getClass().getSimpleName());}},5,10,TimeUnit.SECONDS);}
    @Override public void destroy(){if(worker!=null)worker.shutdownNow();}
}
