package com.example.myapplication;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.Base64;

import static org.junit.Assert.assertTrue;

/** 验证固定红外码公共解密路径能够直接处理真实 assets 数据。 */
public final class FixedIrDecoderTest {
    /** 验证电视型号的电源键可以解密成全为正数的波形。 */
    @Test
    public void decodesRealTelevisionPowerCode() throws Exception {
        File file = new File("src/main/assets/1_TV/models/xm_1_1227.json");
        if (!file.isFile()) file = new File("app/src/main/assets/1_TV/models/xm_1_1227.json");
        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String encrypted = root.getAsJsonObject("data").getAsJsonObject("key")
                    .get("power").getAsString();
            int[] pattern = FixedIrDecoder.decodeCiphertext(Base64.getDecoder().decode(encrypted));
            assertTrue(pattern.length > 10);
            for (int duration : pattern) assertTrue(duration > 0);
        }
    }
}
