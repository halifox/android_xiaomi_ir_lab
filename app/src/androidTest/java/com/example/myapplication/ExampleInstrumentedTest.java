package com.example.myapplication;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Android 设备上的应用包名冒烟测试。 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    /** 验证安装到 Android 设备上的目标应用包名。 */
    @Test
    public void useAppContext() {
        assertEquals(
                "com.example.myapplication",
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName()
        );
    }
}
