# 投影仪（device=10）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置与数量

```text
10_Projector.json
10_Projector/<brand>_<brandId>.json
10_Projector/models/<modelId>.json
```

- 品牌：118；
- 型号：370；
- 型号全部来自 `others`；
- 字符串 key：8026；
- 反向 key：353。

型号 ID 前缀：

- `kk_`：141；
- `mx_`：56；
- 纯数字开头：173。

## 代表性数据示例

这里选择轰天炮 `kk_10_76_8156`。它同时包含电源、投影方向、画面翻转、宽高比、信号源和播放控制：

```text
10_Projector.json
  → 10_Projector/Hongtianpao_76.json
    → others[]._id 中的 kk_10_76_8156
      → 10_Projector/models/kk_10_76_8156.json
```

```json
{
  "data": {
    "_id": "kk_10_76_8156",
    "version": 1000,
    "frequency": 37950,
    "key": {
      "power": "<Base64 密文省略>",
      "input": "<Base64 密文省略>",
      "project": "<Base64 密文省略>",
      "upside down": "<Base64 密文省略>",
      "flip around": "<Base64 密文省略>",
      "4:3": "<Base64 密文省略>",
      "freeze": "<Base64 密文省略>",
      "menu": "<Base64 密文省略>"
    }
  }
}
```

这些键仍然都是固定码；`upside down` 和 `flip around` 是两个真实原始键名，显示层可以翻译，但数据层不能擅自合并。

### `power` 功能的完整处理过程

`kk_10_76_8156.json` 的原始 `power` 字段：

```json
{
  "version": 1000,
  "_id": "kk_10_76_8156",
  "frequency": 37950,
  "key": {
    "power": "PbrxjQUQS/w3d1QjWic7X4INUf4xEGgufSdvJqc7Kzvy0BSv6qGcRz1DgNhu8kFhpogMGahjCv7RAeKqMe2tj3Zs7hQeqc88UUQ5PS+S6LmfR1KxnosuanC/vf61HNF4DWqhm+l4hpVG5PDOFnVw/g=="
  }
}
```

1. `RemoteCatalog.loadRemote()` 从型号得到 37950 Hz，并由 `fixedCommands()` 创建电源命令。
2. `FixedIrTransmitter.transmit()` 将 `key.power` 交给 `FixedIrDecoder.decode()`。
3. 解密结果是 72 个微秒时长，总时长 216000 µs。
4. 最终发送：

```java
manager.transmit(37950, new int[] {
    9001,4491,569,564,569,1671,569,1671,569,564,569,564,569,564,569,564,
    569,1671,569,1671,569,1671,569,564,569,1671,569,565,569,1671,569,1671,
    569,565,569,564,569,1671,569,564,569,1671,569,565,568,565,569,565,
    569,564,569,1671,569,564,569,1671,569,564,569,1671,569,1671,569,1671,
    569,1672,569,39757,9003,2251,569,96386
});
```

## 按键结构

代表性按键：

```text
power, up, down, left, right, ok
vol+, vol-, mute
menu, back, input
```

不同投影仪可能把信号源切换保存为 `input`、`source` 或其他原始键名，不能只保留一套固定键表。

## 载波

- 范围：34800～57500 Hz；
- 最常见：37910 Hz。

## 编码结论

全部型号都是字符串固定码。开关机可能由同一个 `power` 键轮换，也可能由型号自己的独立键决定，必须按 JSON 原始按键处理。
