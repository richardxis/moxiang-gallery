package com.moxiang.picture.manager.search;

import com.moxiang.picture.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ColorSimilarityBoundaryTest {
    @ParameterizedTest @ValueSource(strings={"#ff0000","0xFF0000","ff0000"," #FF0000 "})void normalizesSupportedColors(String color){assertEquals("#FF0000",ColorSimilarity.normalize(color));}
    @Test void supportsShortCosHexValues(){assertEquals("#00000A",ColorSimilarity.normalize("0xa"));}
    @ParameterizedTest @ValueSource(strings={"red","#FFF","#GG0000","-1","16777216","0xFFFFFFFF",""})void rejectsMalformedOrOverflow(String color){assertThrows(BusinessException.class,()->ColorSimilarity.parse(color));}
    @Test void identicalColorIsOneAndBlackWhiteAreZero(){assertEquals(1.0,ColorSimilarity.similarity(0xff0000,0xff0000));assertEquals(0.0,ColorSimilarity.similarity(0,0xffffff),1e-12);}
    @Test void distanceIsSymmetricAndCloseColorRanksHigher(){assertEquals(ColorSimilarity.similarity(0x123456,0xabcdef),ColorSimilarity.similarity(0xabcdef,0x123456));assertTrue(ColorSimilarity.similarity(0xff0000,0xfe0101)>ColorSimilarity.similarity(0xff0000,0x00ff00));}
}
