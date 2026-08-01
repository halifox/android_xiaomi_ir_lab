package com.example.myapplication;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jme.JmePlatform;

/** 验证 1522 脚本使用的 Lua 5.2 bit32 运算在 LuaJ JME 中可用。 */
public class LuaJCompatibilityTest {
    /** 验证 LuaJ JME 能执行 1522 脚本依赖的 Lua 5.2 bit32 运算。 */
    @Test
    public void executesLua52Bit32OperationsUsedByField1522() {
        Globals globals = JmePlatform.standardGlobals();
        LuaValue result = globals.load(
                "local masked = bit32.band(0xAF, 0x0F)\n" +
                "local shifted = bit32.lshift(masked, 4)\n" +
                "return bit32.bor(shifted, bit32.rshift(0x40, 4))"
        ).call();

        assertEquals(0xF4, result.toint());
    }
}
