package com.example.myapplication;

import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 验证格力码库加载、结构化配置编码以及 JSON 扩展功能规则。 */
public class JsonLuaIrEngineTest {
    /** @return 从测试环境中的 assets 文件读取到的全部格力配置。 */
    private List<RemoteProfile> profiles() throws Exception {
        File file = new File("src/main/assets/Gree_72.json");
        if (!file.isFile()) file = new File("app/src/main/assets/Gree_72.json");
        try (FileReader reader = new FileReader(file)) {
            return RemoteCatalog.parse(reader);
        }
    }

    /** 验证 Gson 能完整解析配置数量及各编码类型数量。 */
    @Test public void gsonLoadsAllProfiles() throws Exception {
        List<RemoteProfile> profiles = profiles();
        assertEquals(43, profiles.size());
        assertEquals(40, profiles.stream().filter(RemoteProfile::isSendable).count());
        assertEquals(11, profiles.stream().filter(RemoteProfile::isLuaJCompatible).count());
    }

    /** 验证每个结构化配置都能为初始状态生成非空红外波形。 */
    @Test public void everyStructuredProfileBuildsACommand() throws Exception {
        for (RemoteProfile profile : profiles()) {
            if (!profile.isSendable()) continue;
            JsonLuaIrEngine.EncodedCommand command = JsonLuaIrEngine.encode(
                    profile, profile.createInitialState(), AcProtocolConventions.FUNCTION_POWER);
            assertTrue(profile.getId(), command.getPattern().length > 0);
        }
    }

    /** 验证灯光开关由 JSON 的 1017 规则改变状态字节，而不是由 Java 硬编码。 */
    @Test public void lightRuleComesFromJson() throws Exception {
        RemoteProfile profile = profiles().get(0);
        AcRemoteState base = profile.createInitialState().withPower(true);
        int[] off = JsonLuaIrEngine.encode(profile, base.withExtraState(11, 0), 11).getBytes();
        int[] on = JsonLuaIrEngine.encode(profile, base.withExtraState(11, 1), 11).getBytes();
        assertFalse(java.util.Arrays.equals(off, on));
    }
}
