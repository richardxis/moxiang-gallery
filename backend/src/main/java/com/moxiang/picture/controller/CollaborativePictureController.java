package com.moxiang.picture.controller;
import com.moxiang.picture.common.*;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.model.dto.picture.PictureSaveRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.PictureVO;
import com.moxiang.picture.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;

@RestController @RequestMapping("/picture")
public class CollaborativePictureController {
    private final CollaborativePictureService collaboration;private final UserService users;private final PictureService pictures;private final SpaceService spaces;private final PictureAssetService assets;
    public CollaborativePictureController(CollaborativePictureService collaboration,UserService users,PictureService pictures,SpaceService spaces,PictureAssetService assets){this.collaboration=collaboration;this.users=users;this.pictures=pictures;this.spaces=spaces;this.assets=assets;}
    @PostMapping("/collaboration/save") public BaseResponse<PictureVO> save(@RequestPart("file") MultipartFile file,@ModelAttribute PictureSaveRequest body,HttpServletRequest request){return ResultUtils.success(collaboration.save(file,body,users.getLoginUser(request),request.getSession(false)));}
    @GetMapping("/collaboration/result") public BaseResponse<PictureVO> result(@RequestParam String requestId,HttpServletRequest request){return ResultUtils.success(collaboration.result(requestId,users.getLoginUser(request)));}
    @GetMapping("/content") public ResponseEntity<byte[]> content(@RequestParam Long id,HttpServletRequest request){
        User actor=users.getLoginUser(request);Picture picture=pictures.getById(id);ThrowUtils.throwIf(picture==null,ErrorCode.NOT_FOUND_ERROR);
        if(picture.getSpaceId()!=null)spaces.authorized(picture.getSpaceId(),actor);else if(!Integer.valueOf(1).equals(picture.getReviewStatus())&&!actor.getId().equals(picture.getUserId())&&!users.isAdmin(actor))throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        String format=picture.getPicFormat()==null?"":picture.getPicFormat().toLowerCase(java.util.Locale.ROOT);String mime="jpg".equals(format)||"jpeg".equals(format)?"image/jpeg":"png".equals(format)?"image/png":"webp".equals(format)?"image/webp":"application/octet-stream";
        return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Picture-Revision",Long.toString(PictureWriteGate.revision(picture))).header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(mime)).body(assets.readDisplay(picture));
    }
}
