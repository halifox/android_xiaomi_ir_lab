package com.example.myapplication;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 验证全部 KK 空调型号的 Java 能力解析和 type=1 固定码解密。 */
public final class AcConfigurationTest {
    /** 验证 1760 套 type=2 配置都能创建可交互的初始状态。 */
    @Test
    public void parsesEveryStatefulAcConfiguration() throws Exception {
        int count = 0;
        for (File file : modelFiles()) {
            JsonObject data = data(file);
            if (integer(data, "type") != 2) continue;
            AcConfiguration configuration = AcConfiguration.parse(
                    string(data, "_id"), data.getAsJsonObject("key"));
            AcState state = configuration.createInitialState();
            assertFalse(file.getName(), configuration.getModes().isEmpty());
            assertTrue(file.getName(), configuration.mode(state.getMode()) != null);
            for (AcExtraFunction function : configuration.getExtraFunctions()) {
                assertFalse(file.getName() + ":" + function.getFunctionId(), function.getStates().isEmpty());
            }
            count++;
        }
        assertEquals(1760, count);
    }

    /** 验证全部 type=2 空调的初始电源操作均能由 JSON/LuaJ 管线生成波形。 */
    @Test
    public void encodesEveryStatefulAcPowerCommandWithLua() throws Exception {
        int count = 0;
        for (File file : modelFiles()) {
            JsonObject data = data(file);
            if (integer(data, "type") != 2) continue;
            AcConfiguration configuration = AcConfiguration.parse(
                    string(data, "_id"), data.getAsJsonObject("key"));
            AcState state = configuration.createInitialState();
            int[] pattern;
            try {
                pattern = KkAcLuaEncoder.encode(configuration, state, 1);
            } catch (RuntimeException error) {
                throw new AssertionError(file.getName() + ": " + error.getMessage(), error);
            }
            assertTrue(file.getName(), pattern.length > 0);
            assertEquals(file.getName(), 0, pattern.length % 2);
            count++;
        }
        assertEquals(1760, count);
    }

    /** 验证 68 套 type=1 空调的全部正向固定按键都能真实解密。 */
    @Test
    public void decodesEveryFixedAcCommand() throws Exception {
        int models = 0;
        int commands = 0;
        for (File file : modelFiles()) {
            JsonObject data = data(file);
            if (integer(data, "type") != 1) continue;
            models++;
            for (java.util.Map.Entry<String, JsonElement> entry
                    : data.getAsJsonObject("key").entrySet()) {
                if (entry.getKey().endsWith("_r") || !entry.getValue().isJsonPrimitive()) continue;
                int[] pattern = FixedIrDecoder.decodeCiphertext(
                        Base64.getDecoder().decode(entry.getValue().getAsString()));
                assertTrue(file.getName() + ":" + entry.getKey(), pattern.length > 0);
                commands++;
            }
        }
        assertEquals(68, models);
        assertEquals(685, commands);
    }

    /** @return 当前工程中的全部空调型号文件。 */
    private static File[] modelFiles() {
        File directory = new File("src/main/assets/3_AC/models");
        if (!directory.isDirectory()) directory = new File("app/src/main/assets/3_AC/models");
        File[] files = directory.listFiles((ignored, name) -> name.endsWith(".json"));
        if (files == null) throw new IllegalStateException("找不到空调型号目录");
        return files;
    }

    /** @return 读取一个型号文件的 data 对象。 */
    private static JsonObject data(File file) throws Exception {
        try (FileReader reader = new FileReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("data");
        }
    }

    /** @return 安全读取整数字段。 */
    private static int integer(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsInt() : 0;
    }

    /** @return 安全读取字符串字段。 */
    private static String string(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsString() : "";
    }
}
