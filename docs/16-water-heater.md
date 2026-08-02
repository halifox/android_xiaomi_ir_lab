# 热水器（device=16）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置与数量

```text
16_Water Heater.json
16_Water Heater/<brand>_<brandId>.json
16_Water Heater/models/<modelId>.json
```

- 品牌：25；
- 型号：70；
- 型号全部来自 `others`；
- 字符串 key：436；
- 反向 key：14；
- 型号 ID 全部以 `kk_` 开头。

## 代表性数据示例

这里选择 A.O.史密斯 `kk_16_3184_8084`。它包含电源、温度加减、模式、菜单和一键加热，能够代表热水器的离散操作方式：

```text
16_Water Heater.json
  → 16_Water Heater/A.O.Smith_3184.json
    → others[]._id 中的 kk_16_3184_8084
      → 16_Water Heater/models/kk_16_3184_8084.json
```

```json
{
  "data": {
    "_id": "kk_16_3184_8084",
    "version": 1000,
    "frequency": 37940,
    "key": {
      "power": "<Base64 密文省略>",
      "temperature_up": "<Base64 密文省略>",
      "temperature_down": "<Base64 密文省略>",
      "mode": "<Base64 密文省略>",
      "menu": "<Base64 密文省略>",
      "ok": "<Base64 密文省略>",
      "One-click Heating": "<Base64 密文省略>",
      "TURBO": "<Base64 密文省略>"
    }
  }
}
```

温度加减也是两条独立固定码。这个文件没有目标温度字段，应用不能像处理状态型空调那样直接编码任意温度。

### `power` 功能的完整处理过程

`kk_16_3184_8084.json` 的原始 `power` 字段：

```json
{
  "version": 1000,
  "_id": "kk_16_3184_8084",
  "frequency": 37940,
  "key": {
    "power": "kRkKYgOp+qzyv702UTYOnuMx0uGWBjD/B6Jjl8bnwPjAcO3A4/IIwaKwbuyVym8g5gDv3UeLIQxwWkiNvDlqU9TxOk+3msCglB4djaQiGsZlssSl7jRiB2vtF450F+lpUIizjjy7EcL+hXlMBSwGAw=="
  }
}
```

1. `RemoteCatalog.loadRemote()` 取得 37940 Hz，`fixedCommands()` 从 `key.power` 创建电源命令。
2. `FixedIrTransmitter.transmit()` 将密文交给 `FixedIrDecoder.decode()`。
3. 解密得到 76 个微秒时长，总时长 322500 µs。结果已经包含协议要求的重复尾帧，不需要发送器自行拆分。
4. 最终发送：

```java
manager.transmit(37940, new int[] {
    8999,4478,565,543,565,543,565,543,565,1679,565,1679,565,543,564,1679,
    565,543,565,1679,565,1679,565,1679,565,543,565,543,565,1679,565,543,
    565,1679,565,543,565,543,565,543,565,543,565,543,565,543,565,543,
    565,543,565,1679,565,1679,565,1679,565,1679,565,1679,565,1679,565,1679,
    565,1679,565,39897,8998,2239,564,96113,8998,2239,565,95214
});
```

## 按键结构

代表性按键：

```text
power
temperature_up, temperature_down
reservation, Appointment
mode, set, clock, timer
Medium, Variation, Thermostat
```

同类功能使用了不同英文命名和大小写，不能只维护一套固定键名。

## 载波

- 范围：37640～38380 Hz；
- 最常见：38000 Hz，共 38 个型号。

## 编码结论

热水器温度和预约功能都是离散固定码。当前数据没有空调 `type=2` 那种完整状态规则。
