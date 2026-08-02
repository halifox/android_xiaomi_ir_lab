# 功放（device=8）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置与数量

```text
8_A_V receiver.json
8_A_V receiver/<brand>_<brandId>.json
8_A_V receiver/models/<modelId>.json
```

- 品牌：202；
- 型号：455；
- 型号全部来自 `others`；
- 字符串 key：9821；
- 反向 key：1196。

型号 ID 前缀：

- `kk_`：343；
- `mx_`：55；
- 纯数字开头：57。

## 代表性数据示例

这里选择 LG `kk_8_22_5061`。它包含功放常见的音量、输入源、音效和媒体控制：

```text
8_A_V receiver.json
  → 8_A_V receiver/LG_22.json
    → others[]._id 中的 kk_8_22_5061
      → 8_A_V receiver/models/kk_8_22_5061.json
```

```json
{
  "data": {
    "_id": "kk_8_22_5061",
    "version": 1002,
    "frequency": 38000,
    "key": {
      "power": "<Base64 密文省略>",
      "vol+": "<Base64 密文省略>",
      "vol-": "<Base64 密文省略>",
      "mute": "<Base64 密文省略>",
      "input": "<Base64 密文省略>",
      "Radio&Input": "<Base64 密文省略>",
      "soundeffect": "<Base64 密文省略>",
      "play": "<Base64 密文省略>"
    }
  }
}
```

`Radio&Input` 和 `soundeffect` 说明输入源与音效键名不能只靠一张通用按键表猜测，必须保留型号中的原始名称。

### `power` 功能的完整处理过程

`kk_8_22_5061.json` 的原始 `power` 字段：

```json
{
  "version": 1002,
  "_id": "kk_8_22_5061",
  "frequency": 38000,
  "key": {
    "power": "Y3pNIc9o78oQwCRuVoeMdpVlal0py6mOXbhwDX7qTfN9+qaMKYrNP3l++ciJ4kv+EhF6ve9r3+qXUZ94IvouWXlc+QLmM6naswekTiH15e4FhuBwxZzJ/nMQsKKpHsbh"
  }
}
```

1. `RemoteCatalog.loadRemote()` 读取 `frequency=38000`；`fixedCommands()` 把 `key.power` 转换为电源命令。
2. `FixedIrTransmitter.transmit()` 选择正向码，并调用 `FixedIrDecoder.decode()`。
3. Base64、AES、GZIP、Gson 处理后得到 68 个微秒时长，总时长 108000 µs。
4. 最终发送的是：

```java
manager.transmit(38000, new int[] {
    4487,4472,552,537,552,537,552,1665,552,1665,552,537,552,1665,552,537,
    552,537,552,537,552,537,552,1665,553,1665,552,537,552,1665,552,537,
    552,537,552,537,552,1665,552,1665,552,1665,552,1665,552,537,552,537,
    552,537,552,1665,552,537,552,537,552,537,552,537,551,1666,552,1666,
    552,1665,552,47847
});
```

## 按键结构

代表性按键：

```text
power, vol+, vol-, mute
up, down, left, right, ok
play, pause, FF, REW
input, source
```

功放的输入源和音量都是固定命令。JSON 不提供当前音量或当前输入源的回读状态。

## 载波

- 范围：35420～44950 Hz；
- 最常见：38000 Hz，共 356 个型号。

## 编码结论

全部型号走固定码路径。`mx_` 型号带有非空 `seceret_key`，当前样本值与公共 AES 密钥一致。
