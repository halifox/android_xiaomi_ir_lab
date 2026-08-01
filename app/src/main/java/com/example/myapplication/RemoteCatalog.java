package com.example.myapplication;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 使用 Gson 加载 assets 中所有同结构的空调码库。 */
public final class RemoteCatalog {
    /** 所有码库 JSON 共用的 Gson 解析器。 */
    private static final Gson GSON = new Gson();

    private RemoteCatalog() {}

    /**
     * 扫描 assets 根目录并加载所有符合码库结构的 JSON 文件。
     *
     * @param context Android 上下文，用于访问 AssetManager
     * @return 按文件名、配置顺序和配置编号排序的遥控器配置
     * @throws IOException assets 列表或文件读取失败
     */
    public static List<RemoteProfile> loadAll(Context context) throws IOException {
        String[] names = context.getAssets().list("");
        List<RemoteProfile> profiles = new ArrayList<>();
        if (names == null) return profiles;
        for (String name : names) {
            if (!name.toLowerCase().endsWith(".json")) continue;
            try (Reader reader = new InputStreamReader(context.getAssets().open(name), StandardCharsets.UTF_8)) {
                for (RemoteProfile profile : parse(reader)) {
                    profile.setAssetName(name);
                    profiles.add(profile);
                }
            } catch (RuntimeException ignored) {
                // assets 中可以存在其他用途的 JSON；不符合空调码库结构时直接跳过。
            }
        }
        profiles.sort(Comparator.comparing(RemoteProfile::getAssetName)
                .thenComparingInt(RemoteProfile::getOrder)
                .thenComparing(RemoteProfile::getId));
        return profiles;
    }

    /**
     * 从字符流解析码库响应中的 others 配置数组。
     *
     * @param reader JSON 字符流
     * @return 遥控器配置列表；结构不完整时返回空列表
     */
    public static List<RemoteProfile> parse(Reader reader) {
        CatalogResponse response = GSON.fromJson(reader, CatalogResponse.class);
        if (response == null || response.data == null || response.data.others == null) return List.of();
        return response.data.others;
    }

    /** 对应码库 JSON 的最外层响应对象，仅供 Gson 反序列化使用。 */
    private static final class CatalogResponse {
        /** 服务端状态码原值。 */
        int status;
        /** 响应声明的语言。 */
        String language;
        /** 响应声明的字符编码。 */
        String encoding;
        /** 实际码库数据容器。 */
        CatalogData data;
    }

    /** 对应最外层 data 字段的 Gson 数据模型。 */
    private static final class CatalogData {
        /** 可选择的遥控器配置数组。 */
        List<RemoteProfile> others;
        /** 原始分类树；当前仅保留以兼容完整 JSON 结构。 */
        JsonObject tree;
    }
}
