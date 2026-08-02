# DVD（device=4）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置

```text
4_DVD.json
4_DVD/<brand>_<brandId>.json
4_DVD/models/<modelId>.json
```

## 数据统计

- 品牌：247；
- 型号：839；
- `tree.keysetids` 引用：807；
- `others` 引用：32；
- 去重后引用：839；
- 字符串 key：28986；
- 反向 key：1787。

型号 ID 前缀：

- `xm_`：807；
- `kk_`：6；
- 纯数字开头：26。

## 代表性数据示例

这里选择飞利浦 `kk_4_67_8213`。它包含播放控制、菜单、音量，以及大量 `_r` 反向码，能够同时说明 DVD 固定码和正反码结构：

```text
4_DVD.json
  → 4_DVD/Philips_67.json
    → others[]._id 中的 kk_4_67_8213
      → 4_DVD/models/kk_4_67_8213.json
```

真实型号文件摘录如下，Base64 密文正文省略：

```json
{
  "data": {
    "_id": "kk_4_67_8213",
    "version": 1000,
    "frequency": 36000,
    "key": {
      "power": "<Base64 密文省略>",
      "power_r": "<Base64 密文省略>",
      "play": "<Base64 密文省略>",
      "play_r": "<Base64 密文省略>",
      "pause": "<Base64 密文省略>",
      "stop": "<Base64 密文省略>",
      "FF": "<Base64 密文省略>",
      "REW": "<Base64 密文省略>"
    }
  }
}
```

`power_r` 和 `play_r` 是相应按键的反向码，不是需要单独展示的功能；36000 Hz 也说明 DVD 不能统一按 38000 Hz 发送。

### `play` 功能的完整处理过程

下面是 `kk_4_67_8213.json` 中未经缩短的 `play` 固定码：

```json
{
  "version": 1000,
  "_id": "kk_4_67_8213",
  "frequency": 36000,
  "key": {
    "play": "4NbBUoT8TO4l4RzT2cuPgaeZWdHBDhPFMqvRySTge2cbH3ABJT5prH+rpwzdLHmXC+wWQL9yqRGC6/NbvUBhjB/8YLKcR024hzGCQ+MNn3Ugg6TKX2l2AzNeIyDHyX6OA3Ykjvhfyp1vHrCJSWrf3twAjpEjV3E9oNFWZiT2rjs="
  }
}
```

1. `RemoteCatalog.loadRemote()` 从 `frequency` 得到 36000 Hz，`fixedCommands()` 从 `key.play` 创建播放命令，并把 `key.play_r` 作为可选反向码关联到同一命令。
2. `FixedIrTransmitter.transmit()` 本次选择上面的正向 `play` 密文。
3. `FixedIrDecoder.decode()` 得到 76 个微秒时长，总时长 216000 µs。
4. 最终发送：

```java
manager.transmit(36000, new int[] {
    2686,887,464,869,464,440,464,440,464,886,909,441,463,442,463,442,
    463,442,463,442,939,871,463,443,462,442,464,442,939,871,939,441,
    463,871,464,442,463,84518,2684,886,464,869,464,440,464,440,464,886,
    909,441,463,442,463,443,463,443,464,442,940,871,464,442,463,442,
    464,442,940,871,940,441,464,870,463,442,463,84573
});
```

## 按键结构

所有型号都是字符串固定码，没有 `type=2` 状态规则。

代表性按键：

```text
power, play, pause, stop
up, down, left, right, ok
menu, next, FF, REW
```

`FF`、`REW` 等键名包含大写字母，解析时不能强制把原始 key 转成小写。

## 载波

- 范围：32520～59000 Hz；
- 最常见：37990 Hz。

DVD 的频率跨度较大，必须使用型号文件中的 `frequency`。

## 编码结论

全部型号走通用固定码解密路径。播放状态不会从设备回传，`play`、`pause`、`stop` 只表示发送对应动作。
