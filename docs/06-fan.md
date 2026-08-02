# 风扇（device=6）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置与数量

```text
6_Fan.json
6_Fan/<brand>_<brandId>.json
6_Fan/models/<modelId>.json
```

- 品牌：133；
- 型号：422；
- 型号全部来自 `others`；
- 字符串 key：4181；
- 反向 key：30。

型号 ID 前缀：

- `kk_`：386；
- `mx_`：34；
- 纯数字开头：2。

## 代表性数据示例

这里选择美的 `kk_6_110_11342`。它同时具有电源、风速、风类、摇头、定时、负离子和温度键，代表风扇常见的离散动作集合：

```text
6_Fan.json
  → 6_Fan/Midea_110.json
    → others[]._id 中的 kk_6_110_11342
      → 6_Fan/models/kk_6_110_11342.json
```

```json
{
  "data": {
    "_id": "kk_6_110_11342",
    "version": 1003,
    "frequency": 39160,
    "key": {
      "power": "<Base64 密文省略>",
      "wind_speed": "<Base64 密文省略>",
      "wind_type": "<Base64 密文省略>",
      "fanspeed+": "<Base64 密文省略>",
      "fanspeed-": "<Base64 密文省略>",
      "shake_wind": "<Base64 密文省略>",
      "timer": "<Base64 密文省略>",
      "anion": "<Base64 密文省略>"
    }
  }
}
```

每个 key 都是独立固定码。这个样本没有描述“当前是第几档”的状态字节，应用只能发送 `wind_speed` 或 `fanspeed+` 等动作。

### `power` 功能的完整处理过程

下面是 `kk_6_110_11342.json` 中的原始 `power` 数据：

```json
{
  "version": 1003,
  "_id": "kk_6_110_11342",
  "frequency": 39160,
  "key": {
    "power": "Xkpq/dLnM8L1KlDKeunPa+YrLjMz02+ZIRqWCOIMAn8IbvGOMZw0+1wqCX8hXYbqiEwgK9xzvuxkJRxtNhfMcLmHbr8lYaWYrkYyNqgh3mHPPzSlkom0TQ2852+b43f3Y88iXYGMDGv4LzHvUGY8h79qMQRh/gkETvSsghrJJBU="
  }
}
```

1. `RemoteCatalog.loadRemote()` 读取 39160 Hz 和 `key.power`；`fixedCommands()` 创建电源命令。
2. `FixedIrTransmitter.transmit()` 选择正向密文。此型号没有 `power_r`，以后每次也都使用同一个值。
3. `FixedIrDecoder.decode()` 得到 104 个微秒时长，总时长 209000 µs。这个步骤不计算风扇状态，只解密一条离散动作。
4. 最终发送：

```java
manager.transmit(39160, new int[] {
    8783,4308,585,1548,584,476,584,476,610,476,585,476,585,476,610,476,
    584,476,610,476,584,1549,610,1549,584,1549,584,1549,584,1549,585,1549,
    584,1549,610,1549,584,1549,584,476,584,476,610,476,584,476,584,476,
    610,476,584,476,584,476,610,1548,584,1548,584,1548,584,1548,584,1548,
    584,1549,610,1549,584,1549,584,477,584,476,610,476,584,476,610,476,
    611,450,584,476,610,476,610,1549,584,1549,584,1549,610,1549,610,1549,
    584,1549,610,13834,8809,2111,584,92939
});
```

## 按键结构

代表性按键：

```text
power, timer, shake_wind
wind_speed, wind_type
anion, Light, mode, sleep
1, 2, 3
```

虽然风扇有风速、模式、定时等状态，但当前 JSON 没有空调式的完整状态规则。每个功能仍是一条固定红外码。

`Light` 使用大写开头，必须保留原始键名。

## 载波

- 范围：36000～56800 Hz；
- 最常见：38000 Hz。

## 编码结论

422 个型号的 key 值都是字符串，走通用固定码路径。代码不能从这些 JSON 推导风扇真实档位，只能发送指定动作。
