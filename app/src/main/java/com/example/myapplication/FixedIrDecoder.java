package com.example.myapplication;

import android.annotation.SuppressLint;
import android.util.Base64;

import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 将小米固定红外码解密为 Android 可发射的微秒波形。
 *
 * <p>处理顺序与逆向得到的 Miir 公共固定码路径一致：Base64、AES-256-ECB、
 * 去除尾部 ASCII 空格、GZIP、JSON {@code int[]}。空字节属于 GZIP 校验数据，不能
 * 和填充空格一起删除。</p>
 */
public final class FixedIrDecoder {
    /** 固定码公共 AES 密钥的原始 32 字节 ASCII 内容。 */
    private static final byte[] COMMON_KEY =
            "fd7e915003168929c1a9b0ec32a60788".getBytes(StandardCharsets.US_ASCII);
    /** 波形 JSON 解析器。 */
    private static final Gson GSON = new Gson();

    private FixedIrDecoder() {}

    /**
     * 解密并校验一条固定红外码。
     *
     * @param encryptedCode JSON 中的 Base64 加密码
     * @return 交替表示载波开启与关闭时长的微秒数组
     * @throws IllegalArgumentException 加密码或波形结构无效
     */
    @SuppressLint("GetInstance")
    public static int[] decode(String encryptedCode) {
        if (encryptedCode == null || encryptedCode.isBlank()) {
            throw new IllegalArgumentException("红外码为空");
        }
        return decodeCiphertext(Base64.decode(encryptedCode, Base64.DEFAULT));
    }

    /**
     * 解密已经完成 Base64 转换的固定码，供 JVM 测试复用协议核心。
     *
     * @param ciphertext AES 密文字节
     * @return 经过校验的红外波形
     */
    @SuppressLint("GetInstance")
    static int[] decodeCiphertext(byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(COMMON_KEY, "AES"));
            byte[] decrypted = cipher.doFinal(ciphertext);
            int contentLength = decrypted.length;
            while (contentLength > 0 && decrypted[contentLength - 1] == 0x20) contentLength--;
            byte[] json = gunzip(Arrays.copyOf(decrypted, contentLength));
            int[] pattern = GSON.fromJson(new String(json, StandardCharsets.UTF_8), int[].class);
            validatePattern(pattern);
            return pattern;
        } catch (GeneralSecurityException | IOException | RuntimeException error) {
            if (error instanceof IllegalArgumentException) throw (IllegalArgumentException) error;
            throw new IllegalArgumentException("固定红外码解密失败", error);
        }
    }

    /** @return 解压后的 JSON 字节。 */
    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    /** 校验 Android 红外接口要求的非空正时长波形。 */
    private static void validatePattern(int[] pattern) {
        if (pattern == null || pattern.length == 0) {
            throw new IllegalArgumentException("解密后的红外波形为空");
        }
        for (int duration : pattern) {
            if (duration <= 0) throw new IllegalArgumentException("红外波形包含非正时长");
        }
    }
}
