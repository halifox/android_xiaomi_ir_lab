# 灯泡（device=14）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置与数量

```text
14_Light.json
14_Light/<brand>_<brandId>.json
14_Light/models/<modelId>.json
```

- 品牌：31；
- 型号：102；
- 型号全部来自 `others`；
- 字符串 key：877；
- 反向 key：68；
- 型号 ID 全部以 `kk_` 开头。

## 代表性数据示例

这里选择欧普 `kk_14_2267_8259`。它覆盖电源、亮度、色温、夜灯和辅助光源，是灯具固定动作结构的代表：

```text
14_Light.json
  → 14_Light/Opple_2267.json
    → others[]._id 中的 kk_14_2267_8259
      → 14_Light/models/kk_14_2267_8259.json
```

```json
{
  "data": {
    "_id": "kk_14_2267_8259",
    "version": 1000,
    "frequency": 38180,
    "key": {
      "power": "<Base64 密文省略>",
      "Bright": "<Base64 密文省略>",
      "clr temp+": "<Base64 密文省略>",
      "clr temp-": "<Base64 密文省略>",
      "Color temperature segment": "<Base64 密文省略>",
      "Night light": "<Base64 密文省略>",
      "Auxiliary light source": "<Base64 密文省略>"
    }
  }
}
```

这里的 `Bright`、`Night light` 等大小写和空格都是原始数据的一部分。它们可以映射成中文名称，但查码时必须使用原 key。

### `power` 功能的完整处理过程

`kk_14_2267_8259.json` 的原始 `power` 字段：

```json
{
  "version": 1000,
  "_id": "kk_14_2267_8259",
  "frequency": 38180,
  "key": {
    "power": "Bn9wGObpf9l55hX4VlGZAnEdAAVYw5+9ID6mQpz7DpOLboqLR44vdzXn8j14OtyCuKEszNEUHv6gjLPYn2jdN1gcmn8J1/60BB0DFHceSPUFhuBwxZzJ/nMQsKKpHsbh"
  }
}
```

1. `RemoteCatalog.loadRemote()` 读取 `frequency=38180`；`fixedCommands()` 从 `key.power` 创建灯具电源命令。
2. `FixedIrTransmitter.transmit()` 选择固定码后调用 `FixedIrDecoder.decode()`。
3. 解密得到 72 个微秒时长，总时长 213000 µs。码库没有单独给出开和关状态，因此这条命令表示遥控器上的电源动作。
4. 最终发送：

```java
manager.transmit(38180, new int[] {
    8992,4471,558,542,558,542,558,542,558,542,558,542,558,542,558,542,
    558,1642,559,1642,559,1642,558,1642,558,1642,558,1642,558,1642,558,1642,
    558,542,558,1642,558,542,558,542,558,542,558,542,558,542,558,542,
    558,542,558,542,558,1642,558,1642,558,1642,558,1642,558,1642,558,1642,
    558,1642,558,39647,8992,2245,558,94735
});
```

## 按键结构

代表性按键：

```text
power
brightness+, brightness-
Lights, Lighting, All
Warm, Cold, Night
favorites, LAMP3
```

键名大小写和命名方式差异明显，`Night` 在不同型号中还可能重复出现于不同键集合。解析时保存原始 key，显示层再单独映射名称。

## 载波

- 范围：33000～39900 Hz；
- 最常见：38000 Hz，共 78 个型号。

## 编码结论

灯泡虽然有亮度、色温和场景，但当前数据仍是每个动作一条固定码，不是可组合的状态协议。
