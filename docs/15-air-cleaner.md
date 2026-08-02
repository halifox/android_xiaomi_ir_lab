# 空气净化器（device=15）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置与数量

```text
15_Air Cleaner.json
15_Air Cleaner/<brand>_<brandId>.json
15_Air Cleaner/models/<modelId>.json
```

- 品牌：32；
- 型号：50；
- 型号全部来自 `others`；
- 字符串 key：405；
- 反向 key：18；
- 型号 ID 全部以 `kk_` 开头。

## 代表性数据示例

这里选择科瑞莱 `kk_15_4611_8485`。它同时包含自动、睡眠、风速、负离子、童锁和灯光功能：

```text
15_Air Cleaner.json
  → 15_Air Cleaner/Corile_4611.json
    → others[]._id 中的 kk_15_4611_8485
      → 15_Air Cleaner/models/kk_15_4611_8485.json
```

```json
{
  "data": {
    "_id": "kk_15_4611_8485",
    "version": 1001,
    "frequency": 38400,
    "key": {
      "power": "<Base64 密文省略>",
      "automatic": "<Base64 密文省略>",
      "sleep": "<Base64 密文省略>",
      "wind_speed": "<Base64 密文省略>",
      "anion": "<Base64 密文省略>",
      "child_lock": "<Base64 密文省略>",
      "Light": "<Base64 密文省略>"
    }
  }
}
```

这些功能看起来具有状态含义，但 JSON 中每个值仍是独立 Base64 固定码，没有可以自由组合的净化器状态模板。

### `power` 功能的完整处理过程

`kk_15_4611_8485.json` 的原始 `power` 字段：

```json
{
  "version": 1001,
  "_id": "kk_15_4611_8485",
  "frequency": 38400,
  "key": {
    "power": "ZbX6org8NHnlM0m/0YIue9OVzeGo6ClP1l6QF34Bz0xLyvnB7YMV0LJ8DYngwZfFCigibIqqHJTzKzXvrBKM3/wk3qJXA7WL1lBXnxaLizLIA8GjCDrgK6DTNQuT7fZxVZHhX1weLYuYk/Ac4JswuA=="
  }
}
```

1. `RemoteCatalog.loadRemote()` 读取 38400 Hz，`fixedCommands()` 从 `key.power` 创建电源命令。
2. `FixedIrTransmitter.transmit()` 选择正向码并调用 `FixedIrDecoder.decode()`。
3. 解密得到 72 个微秒时长，总时长 214000 µs。这个过程没有读取 `automatic`、`sleep` 或 `wind_speed`，因为每个功能都是彼此独立的固定码。
4. 最终发送：

```java
manager.transmit(38400, new int[] {
    8915,4462,566,554,565,554,566,553,565,554,565,554,565,554,565,554,
    566,554,565,1674,565,1674,566,1674,566,1674,566,1674,566,1674,565,1674,
    565,1674,566,1674,566,554,566,1674,566,555,565,554,565,554,566,1674,
    566,554,565,554,565,1674,566,554,566,1674,566,1673,565,1674,566,554,
    565,1674,566,39345,8916,2247,565,95240
});
```

## 按键结构

代表性按键：

```text
power, automatic, sleep, night
wind_speed, fanspeed+, fanspeed-
timer, anion, shake_wind
child_lock, Light
```

## 载波

- 范围：36280～38400 Hz；
- 最常见：38000 Hz，共 40 个型号。

## 编码结论

净化器的模式、风速和定时都使用离散固定码。JSON 不提供可以自由组合的完整净化器状态。
