# 盒子（device=12）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置与数量

```text
12_Box.json
12_Box/<brand>_<brandId>.json
12_Box/models/<modelId>.json
```

- 品牌：136；
- 型号：221；
- 型号全部来自 `others`；
- 字符串 key：6684；
- 反向 key：62。

型号 ID 前缀：

- `kk_`：159；
- 纯数字开头：62。

## 代表性数据示例

这里选择海美迪 `206_5907`。它包含盒子常见的首页、方向、媒体、鼠标、显示模式和音量控制：

```text
12_Box.json
  → 12_Box/HiMedia_206.json
    → others[]._id 中的 206_5907
      → 12_Box/models/206_5907.json
```

```json
{
  "data": {
    "_id": "206_5907",
    "version": 1002,
    "frequency": 37990,
    "key": {
      "power": "<Base64 密文省略>",
      "home": "<Base64 密文省略>",
      "back": "<Base64 密文省略>",
      "mouse": "<Base64 密文省略>",
      "tvmode": "<Base64 密文省略>",
      "movie": "<Base64 密文省略>",
      "vol+": "<Base64 密文省略>",
      "menu": "<Base64 密文省略>"
    }
  }
}
```

型号 ID 虽然没有 `kk_` 前缀，仍然使用同一套字符串固定码流程，所以不能只根据 ID 前缀选择解码器。

### `power` 功能的完整处理过程

`206_5907.json` 的原始 `power` 字段：

```json
{
  "version": 1002,
  "_id": "206_5907",
  "frequency": 37990,
  "key": {
    "power": "XpK2lGZxx7Ofc1/qfhJgEsFCnJ/50tVKig48jq+xwmvIXx802X+s0l2q+Fs6wZMCsxMa8HJ+QqSYY8fc7umO3uGIc8TgORI4xjAsE3842+v8uYzrphmSW0MYdNljx8hR"
  }
}
```

1. `RemoteCatalog.loadRemotes()` 从 `HiMedia_206.json` 的 `others[]` 取得 `206_5907`，`loadRemote()` 读取 37990 Hz。
2. `fixedCommands()` 从 `key.power` 创建命令，`FixedIrTransmitter.transmit()` 选择该固定码。
3. `FixedIrDecoder.decode()` 得到 72 个微秒时长，总时长 216000 µs。
4. 最终发送：

```java
manager.transmit(37990, new int[] {
    8906,4475,561,561,562,561,562,561,562,561,562,1663,562,561,561,561,
    562,561,562,1663,561,1663,562,1663,562,1663,562,561,562,1663,561,1663,
    562,1663,562,1663,562,561,562,1663,562,1663,562,1663,562,561,562,1663,
    562,561,562,561,562,1663,562,561,562,561,561,561,562,1664,561,561,
    562,1663,562,39882,8906,2238,562,96906
});
```

## 按键结构

代表性按键：

```text
power, home, menu, back
up, down, left, right, ok
vol+, vol-, mute
```

盒子和电视机顶盒是两个不同的 `device`。当前 `device=12` 有完整数据，`device=2` 是空索引，不能把两者目录混用。

## 载波

- 范围：36000～38580 Hz；
- 最常见：38000 Hz。

## 编码结论

全部型号走字符串固定码路径，没有动态状态编码。
