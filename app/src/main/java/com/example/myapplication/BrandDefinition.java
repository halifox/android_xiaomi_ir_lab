package com.example.myapplication;

/**
 * 表示二级页面中的设备品牌。
 *
 * <p>品牌来自指定设备的根索引。电视索引中的地区重复项按
 * {@code deviceId + brandId} 合并，首次出现的位置决定列表顺序。</p>
 */
public final class BrandDefinition {
    /** 所属设备类型编号。 */
    private final int deviceId;
    /** 小米码库中的品牌编号。 */
    private final int brandId;
    /** 当前界面使用的品牌名称。 */
    private final String name;
    /** 品牌在设备索引中的优先级。 */
    private final int priority;
    /** 品牌详细数据文件的 assets 路径。 */
    private final String assetPath;
    /** 进入型号页面后需要展示的品牌资源错误。 */
    private final String childLoadError;

    /**
     * 创建一个品牌定义。
     *
     * @param deviceId 所属设备编号
     * @param brandId 品牌编号
     * @param name 品牌名称
     * @param priority 原始索引优先级
     * @param assetPath 品牌文件路径
     * @param childLoadError 下一级资源错误；没有错误时为 null
     */
    public BrandDefinition(int deviceId, int brandId, String name, int priority,
                           String assetPath, String childLoadError) {
        this.deviceId = deviceId;
        this.brandId = brandId;
        this.name = name;
        this.priority = priority;
        this.assetPath = assetPath;
        this.childLoadError = childLoadError;
    }

    public int getDeviceId() { return deviceId; }
    public int getBrandId() { return brandId; }
    public String getName() { return name; }
    public int getPriority() { return priority; }
    public String getAssetPath() { return assetPath; }
    public String getChildLoadError() { return childLoadError; }
    public String getStableId() { return deviceId + ":" + brandId; }
}
