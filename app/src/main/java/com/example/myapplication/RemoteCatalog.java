package com.example.myapplication;

import android.content.Context;
import android.content.res.AssetManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按“设备类型→品牌→遥控器型号”关系读取 assets 码库。
 *
 * <p>每一级只能使用上一级 JSON 中的显式 ID。目录扫描仅用于把已知 ID 解析成唯一文件名，
 * 不会把未被索引引用的文件加入界面。解析结果按页面粒度缓存，避免启动时读取全部码库。</p>
 */
public final class RemoteCatalog {
    /** Gson 数据绑定实例。 */
    private static final Gson GSON = new Gson();
    /** Android assets 访问器。 */
    private final AssetManager assets;
    /** 设备列表缓存。 */
    private volatile List<DeviceDefinition> deviceCache;
    /** 设备编号到品牌列表的缓存。 */
    private final Map<Integer, List<BrandDefinition>> brandCache = new LinkedHashMap<>();
    /** 品牌稳定编号到遥控器列表的缓存。 */
    private final Map<String, List<RemoteSummary>> remoteCache = new LinkedHashMap<>();

    /**
     * 创建严格索引码库仓库。
     *
     * @param context Android 上下文
     */
    public RemoteCatalog(Context context) {
        this.assets = context.getApplicationContext().getAssets();
    }

    /**
     * 加载一级设备类型列表。
     *
     * @return 保持 devices.json 原始顺序的设备列表
     * @throws IOException 文件缺失、目录冲突或 JSON 无效
     */
    public synchronized List<DeviceDefinition> loadDevices() throws IOException {
        if (deviceCache != null) return deviceCache;
        DevicesResponse response = read("devices.json", DevicesResponse.class);
        if (response == null || response.data == null) throw new IOException("devices.json 缺少 data 数组");
        String[] rootEntries = requireList("");
        List<DeviceDefinition> result = new ArrayList<>();
        for (DeviceDto dto : response.data) {
            String prefix = dto.deviceId + "_";
            String index = uniqueIndex(rootEntries, prefix);
            String directory = index.substring(0, index.length() - ".json".length());
            result.add(new DeviceDefinition(dto.deviceId, localizedName(dto.language, "设备 " + dto.deviceId),
                    index, directory, dto.longPressedMatch != 0));
        }
        deviceCache = List.copyOf(result);
        return deviceCache;
    }

    /**
     * 加载指定设备的二级品牌列表。
     *
     * @param device 一级页面选择的设备
     * @return 按索引顺序去重后的品牌列表
     * @throws IOException 品牌索引或品牌文件关系不完整
     */
    public synchronized List<BrandDefinition> loadBrands(DeviceDefinition device) throws IOException {
        List<BrandDefinition> cached = brandCache.get(device.getDeviceId());
        if (cached != null) return cached;
        JsonElement data = readObject(device.getIndexAssetName()).get("data");
        if (data == null || !data.isJsonArray()) {
            List<BrandDefinition> empty = List.of();
            brandCache.put(device.getDeviceId(), empty);
            return empty;
        }
        String[] files = requireList(device.getAssetDirectory());
        LinkedHashMap<Integer, BrandDefinition> unique = new LinkedHashMap<>();
        for (JsonElement element : data.getAsJsonArray()) {
            BrandDto dto = GSON.fromJson(element, BrandDto.class);
            if (unique.containsKey(dto.brandId)) continue;
            String suffix = "_" + dto.brandId + ".json";
            String file = uniqueSuffix(files, suffix);
            unique.put(dto.brandId, new BrandDefinition(dto.deviceId, dto.brandId,
                    blankFallback(dto.name, "品牌 " + dto.brandId), dto.priority,
                    device.getAssetDirectory() + "/" + file));
        }
        List<BrandDefinition> result = List.copyOf(unique.values());
        brandCache.put(device.getDeviceId(), result);
        return result;
    }

    /**
     * 加载品牌显式引用的三级遥控器列表。
     *
     * @param device 所属设备
     * @param brand 二级页面选择的品牌
     * @return 匹配树型号在前、others 型号在后的去重列表
     * @throws IOException 品牌或型号文件不可读取
     */
    public synchronized List<RemoteSummary> loadRemotes(DeviceDefinition device, BrandDefinition brand)
            throws IOException {
        List<RemoteSummary> cached = remoteCache.get(brand.getStableId());
        if (cached != null) return cached;
        JsonObject brandData = dataObject(readObject(brand.getAssetPath()), brand.getAssetPath());
        LinkedHashMap<String, RemoteReference> references = new LinkedHashMap<>();
        JsonObject tree = objectOrNull(brandData.get("tree"));
        if (tree != null) {
            JsonArray nodes = arrayOrNull(tree.get("nodes"));
            if (nodes != null) for (JsonElement nodeElement : nodes) {
                JsonObject node = nodeElement.getAsJsonObject();
                JsonArray ids = arrayOrNull(node.get("keysetids"));
                if (ids == null) continue;
                for (JsonElement idElement : ids) {
                    String id = idElement.getAsString();
                    references.putIfAbsent(id, new RemoteReference(id, sourceFromId(id), references.size()));
                }
            }
        }
        JsonArray others = arrayOrNull(brandData.get("others"));
        if (others != null) for (JsonElement otherElement : others) {
            JsonObject other = otherElement.getAsJsonObject();
            String id = string(other, "_id", null);
            if (id == null) continue;
            RemoteReference incoming = new RemoteReference(id,
                    string(other, "source", sourceFromId(id)), integer(other, "order", references.size()));
            references.putIfAbsent(id, incoming);
        }

        List<RemoteSummary> result = new ArrayList<>();
        for (RemoteReference reference : references.values()) {
            JsonObject model = loadModelObject(device, reference.modelId);
            JsonObject key = objectOrNull(model.get("key"));
            int frequency = integer(model, "frequency", 0);
            int count = key == null ? 0 : key.size();
            String source = string(model, "source", reference.source);
            String reason = unavailableReason(device.getDeviceId(), source, frequency, count);
            result.add(new RemoteSummary(reference.modelId, source, reference.order, frequency,
                    count, reason == null, reason));
        }
        List<RemoteSummary> immutable = List.copyOf(result);
        remoteCache.put(brand.getStableId(), immutable);
        return immutable;
    }

    /**
     * 加载四级页面的一套完整遥控器控制项。
     *
     * @param device 所属设备
     * @param summary 三级页面选择的型号
     * @return 完整遥控器定义
     * @throws IOException 型号文件缺失或 ID 不一致
     */
    public RemoteDefinition loadRemote(DeviceDefinition device, RemoteSummary summary) throws IOException {
        JsonObject model = loadModelObject(device, summary.getModelId());
        JsonObject key = objectOrNull(model.get("key"));
        String source = string(model, "source", summary.getSource());
        int frequency = integer(model, "frequency", summary.getFrequency());
        String reason = unavailableReason(device.getDeviceId(), source, frequency, key == null ? 0 : key.size());
        List<RemoteCommand> commands = device.getDeviceId() == 3 && "kk".equalsIgnoreCase(source)
                ? acPreviewCommands(key) : fixedCommands(key, reason);
        return new RemoteDefinition(device.getDeviceId(), summary.getModelId(), source,
                frequency, commands, reason);
    }

    /** @return 固定码 JSON 对象转换出的正反码控制项。 */
    private static List<RemoteCommand> fixedCommands(JsonObject key, String modelReason) {
        if (key == null) return List.of();
        List<RemoteCommand> result = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            String name = entry.getKey();
            if (name.endsWith("_r") || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString()) continue;
            String reverseName = name + "_r";
            String reverse = key.has(reverseName) && key.get(reverseName).isJsonPrimitive()
                    ? key.get(reverseName).getAsString() : null;
            String forward = entry.getValue().getAsString();
            String itemReason = modelReason;
            if (itemReason == null) {
                try {
                    FixedIrDecoder.decode(forward);
                    if (reverse != null) FixedIrDecoder.decode(reverse);
                } catch (IllegalArgumentException error) {
                    itemReason = "原始红外码无效：" + error.getMessage();
                }
            }
            result.add(new RemoteCommand(name, RemoteKeyLabels.labelFor(name),
                    itemReason == null ? "原始键名：" + name : itemReason,
                    forward, reverse, itemReason == null));
        }
        return result;
    }

    /** @return 从 KK 空调配置生成仅供浏览的语义控制项。 */
    private static List<RemoteCommand> acPreviewCommands(JsonObject key) {
        List<RemoteCommand> result = new ArrayList<>();
        String reason = "device=3 且 source=kk，当前按要求不发送";
        result.add(disabled("power", "电源", reason));
        result.add(disabled("mode", "运行模式", reason));
        result.add(disabled("temperature", "目标温度", reason));
        result.add(disabled("fan_speed", "风速", reason));
        if (key != null && key.has("1506")) result.add(disabled("wind", "上下风向", reason));
        JsonArray extras = key == null ? null : arrayOrNull(key.get("888888"));
        if (extras != null) for (JsonElement element : extras) {
            JsonObject extra = element.getAsJsonObject();
            int id = integer(extra, "fid", -1);
            String title = string(extra, "fname", "");
            if (title.isBlank()) title = string(extra, "fkey", "功能 " + id);
            result.add(disabled("function_" + id, title, reason));
        }
        return result;
    }

    /** @return 创建一项不可发送的界面控制。 */
    private static RemoteCommand disabled(String key, String title, String reason) {
        return new RemoteCommand(key, title, reason, null, null, false);
    }

    /** @return 根据明确范围规则计算型号不可用原因。 */
    private static String unavailableReason(int deviceId, String source, int frequency, int count) {
        if (deviceId == 3 && "kk".equalsIgnoreCase(source)) return "KK 空调状态编码按要求未实现";
        if (count == 0) return "型号文件没有控制项";
        if (frequency <= 0) return "型号缺少有效载波频率";
        return null;
    }

    /** @return 精确读取并校验 models/modelId.json。 */
    private JsonObject loadModelObject(DeviceDefinition device, String modelId) throws IOException {
        String path = device.getAssetDirectory() + "/models/" + modelId + ".json";
        JsonObject model = dataObject(readObject(path), path);
        String embeddedId = string(model, "_id", null);
        if (embeddedId != null && !modelId.equals(embeddedId)) {
            throw new IOException(path + " 的 data._id 与索引不一致");
        }
        return model;
    }

    /** @return 从 assets 读取 Gson 数据对象。 */
    private <T> T read(String path, Class<T> type) throws IOException {
        try (Reader reader = new InputStreamReader(assets.open(path), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (RuntimeException error) {
            throw new IOException("无法解析 " + path, error);
        }
    }

    /** @return 从 assets 读取 JSON 根对象。 */
    private JsonObject readObject(String path) throws IOException {
        try (Reader reader = new InputStreamReader(assets.open(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("无法解析 " + path, error);
        }
    }

    /** @return 响应根对象中的 data 对象。 */
    private static JsonObject dataObject(JsonObject response, String path) throws IOException {
        JsonObject data = objectOrNull(response.get("data"));
        if (data == null) throw new IOException(path + " 缺少 data 对象");
        return data;
    }

    /** @return assets 目录项；系统返回 null 时抛出明确错误。 */
    private String[] requireList(String path) throws IOException {
        String[] result = assets.list(path);
        if (result == null) throw new IOException("无法列出 assets/" + path);
        return result;
    }

    /**
     * 根据设备编号解析唯一品牌索引文件。
     *
     * <p>空品牌设备的同名目录不会被 Android assets 打包，因此设备资源目录由索引文件名
     * 推导，不能要求根目录列表中一定存在空目录。</p>
     */
    private static String uniqueIndex(String[] entries, String prefix) throws IOException {
        List<String> matches = new ArrayList<>();
        for (String entry : entries) {
            if (entry.startsWith(prefix) && entry.endsWith(".json")) matches.add(entry);
        }
        if (matches.size() != 1) {
            throw new IOException(prefix + " 应唯一对应一个设备索引，实际为 " + matches);
        }
        return matches.get(0);
    }

    /** @return 根据品牌 ID 文件名后缀解析唯一品牌文件。 */
    private static String uniqueSuffix(String[] entries, String suffix) throws IOException {
        String match = null;
        for (String entry : entries) if (entry.endsWith(suffix)) {
            if (match != null) throw new IOException("品牌后缀 " + suffix + " 对应多个文件");
            match = entry;
        }
        if (match == null) throw new IOException("找不到品牌文件 " + suffix);
        return match;
    }

    /** @return 优先选择简体中文的本地化名称。 */
    private static String localizedName(List<LocalizedNameDto> values, String fallback) {
        if (values == null) return fallback;
        for (LocalizedNameDto value : values) if ("cn".equalsIgnoreCase(value.locale)) {
            return blankFallback(value.name, fallback);
        }
        return values.isEmpty() ? fallback : blankFallback(values.get(0).name, fallback);
    }

    /** @return 从型号 ID 前缀推导固定码来源，仅用于 tree 未携带 source 时展示。 */
    private static String sourceFromId(String id) {
        int separator = id == null ? -1 : id.indexOf('_');
        return separator > 0 ? id.substring(0, separator).toLowerCase(Locale.ROOT) : "unknown";
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static JsonObject objectOrNull(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray arrayOrNull(JsonElement value) {
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String string(JsonObject object, String name, String fallback) {
        JsonElement value = object == null ? null : object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static int integer(JsonObject object, String name, int fallback) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /** devices.json 响应数据模型。 */
    private static final class DevicesResponse {
        /** 原始设备数组。 */
        List<DeviceDto> data;
    }

    /** devices.json 中的单个设备数据模型。 */
    private static final class DeviceDto {
        /** 设备类型编号。 */
        @SerializedName("deviceid") int deviceId;
        /** 长按匹配标记。 */
        @SerializedName("long_pressed_match") int longPressedMatch;
        /** 多语言设备名称。 */
        List<LocalizedNameDto> language;
    }

    /** 通用本地化名称数据模型。 */
    private static final class LocalizedNameDto {
        /** 本地化名称。 */
        String name;
        /** 原始字段 local 的语言代码。 */
        @SerializedName("local") String locale;
    }

    /** 设备品牌索引中的品牌数据模型。 */
    private static final class BrandDto {
        /** 所属设备编号。 */
        @SerializedName("deviceid") int deviceId;
        /** 品牌编号。 */
        @SerializedName("brandid") int brandId;
        /** 品牌名称。 */
        String name;
        /** 品牌排序优先级。 */
        int priority;
    }

    /** 品牌文件中指向型号的内部引用。 */
    private static final class RemoteReference {
        /** 型号编号。 */
        final String modelId;
        /** 数据来源。 */
        final String source;
        /** 原始顺序。 */
        final int order;

        /** 创建一条内部型号引用。 */
        RemoteReference(String modelId, String source, int order) {
            this.modelId = modelId;
            this.source = source;
            this.order = order;
        }
    }
}
