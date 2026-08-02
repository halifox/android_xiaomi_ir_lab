# 电视机顶盒（device=2）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置

```text
2_Set-top box.json
```

当前没有对应的品牌目录和型号目录。

## 代表性数据示例

这个设备没有可选择的型号，因此能够代表它的真实样本就是 `2_Set-top box.json` 本身。它和其他设备索引不同，不是直接的品牌数组：

```json
{
  "data": {
    "count": 0,
    "data": []
  }
}
```

`count=0` 与空 `data[]` 相互一致。这里不能虚构一个型号示例，也不能回退读取 `device=12` 的盒子数据。

### 一个功能为什么无法发送

这个设备没有可以举例的 `power` 功能，处理过程会在索引阶段结束：

1. `RemoteCatalog.loadDevices()` 能从 `devices.json` 创建 `device=2`。
2. `RemoteCatalog.loadBrands()` 读取上面的原始 JSON，发现 `data` 不是品牌数组，于是返回空列表。
3. 没有 `BrandDefinition`，因此不会调用 `loadRemotes()` 和 `loadRemote()`，也得不到 `_id`、`frequency` 或 `key.power`。
4. 不会创建 `RemoteCommand`，不会调用 `FixedIrDecoder.decode()`。
5. 最终没有任何 `ConsumerIrManager.transmit()` 调用。这是数据为空的正常结果，而不是一个可以用默认 38000 Hz 绕过的错误。

因此当前数据中：

- 品牌数：0；
- 型号数：0；
- 红外按键数：0。

## 编码结论

目前没有型号数据，无法分析按键、载波或编码格式，也没有可发送的电视机顶盒红外码。

不能把这个空索引当成解析错误。它表示数据源当前没有为 `device=2` 提供资源。
