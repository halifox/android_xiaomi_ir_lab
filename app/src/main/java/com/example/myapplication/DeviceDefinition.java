package com.example.myapplication;

/**
 * 表示一级页面中的设备类型。
 *
 * <p>设备只能由 {@code devices.json} 创建。索引文件名和资源目录由仓库根据
 * {@code deviceId} 唯一解析，后续页面不得自行扫描其他设备的数据。</p>
 */
public final class DeviceDefinition {
    /** 小米码库中的设备类型编号。 */
    private final int deviceId;
    /** 当前界面使用的设备名称。 */
    private final String name;
    /** 对应的根目录品牌索引文件。 */
    private final String indexAssetName;
    /** 对应的品牌与型号资源目录。 */
    private final String assetDirectory;
    /** 是否允许长按连续发送。 */
    private final boolean longPressEnabled;
    /** 进入品牌页面后需要展示的资源关系错误。 */
    private final String childLoadError;

    /**
     * 创建一个设备类型。
     *
     * @param deviceId 设备类型编号
     * @param name 界面显示名称
     * @param indexAssetName 品牌索引文件名
     * @param assetDirectory 品牌与型号资源目录
     * @param longPressEnabled 是否允许长按连续发送
     * @param childLoadError 下一级资源错误；没有错误时为 null
     */
    public DeviceDefinition(int deviceId, String name, String indexAssetName,
                            String assetDirectory, boolean longPressEnabled,
                            String childLoadError) {
        this.deviceId = deviceId;
        this.name = name;
        this.indexAssetName = indexAssetName;
        this.assetDirectory = assetDirectory;
        this.longPressEnabled = longPressEnabled;
        this.childLoadError = childLoadError;
    }

    public int getDeviceId() { return deviceId; }
    public String getName() { return name; }
    public String getIndexAssetName() { return indexAssetName; }
    public String getAssetDirectory() { return assetDirectory; }
    public boolean isLongPressEnabled() { return longPressEnabled; }
    public String getChildLoadError() { return childLoadError; }
}
