package com.moxiang.picture.manager.upload;

import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

/** 格式签名筛查，最终可解析性由数据万象确认。 */
public final class PictureFileValidation {
    private PictureFileValidation() { }
    public static String detectFormat(File file) throws IOException {
        byte[] header = new byte[12];
        int count;
        try (FileInputStream stream = new FileInputStream(file)) { count = stream.read(header); }
        if (count >= 3 && (header[0] & 255) == 255 && (header[1] & 255) == 216
                && (header[2] & 255) == 255) return "jpg";
        byte[] png = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (count >= 8 && Arrays.equals(Arrays.copyOf(header, 8), png)) return "png";
        if (count >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                && header[10] == 'B' && header[11] == 'P') return "webp";
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 JPEG、PNG、WebP 图片内容");
    }
}
