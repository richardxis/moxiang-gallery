package com.moxiang.picture.manager.search;

import com.moxiang.picture.exception.*;
import java.util.Locale;

public final class ColorSimilarity {
    private ColorSimilarity() { }
    public static int parse(String color) {
        if (color == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择有效的 RGB 颜色");
        String value = color.trim();
        if (value.matches("#[0-9a-fA-F]{6}")) value = value.substring(1);
        else if (value.matches("0[xX][0-9a-fA-F]{1,6}")) value = value.substring(2);
        else if (!value.matches("[0-9a-fA-F]{6}")) throw new BusinessException(ErrorCode.PARAMS_ERROR, "颜色格式须为 #RRGGBB 或 0xRRGGBB");
        return Integer.parseInt(value, 16);
    }
    public static String normalize(String color) { return String.format(Locale.ROOT, "#%06X", parse(color)); }
    public static double similarity(int left, int right) {
        int r = ((left >> 16) & 255) - ((right >> 16) & 255);
        int g = ((left >> 8) & 255) - ((right >> 8) & 255);
        int b = (left & 255) - (right & 255);
        return 1.0 - Math.sqrt((double)r*r + (double)g*g + (double)b*b) / Math.sqrt(3.0*255*255);
    }
}
