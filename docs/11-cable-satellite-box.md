# 卫星电视（device=11）JSON 与红外格式

[返回通用说明](../README.md)

数据来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

## 数据位置与数量

```text
11_Cable _ Satellite box.json
11_Cable _ Satellite box/<brand>_<brandId>.json
11_Cable _ Satellite box/models/<modelId>.json
```

- 品牌：2；
- 型号：13；
- `tree.keysetids` 引用：12；
- `others` 引用：1；
- 字符串 key：564；
- 反向 key：76。

型号 ID 前缀：

- `xm_`：12；
- `kk_`：1。

## 代表性数据示例

这里选择卫星电视品牌文件中的 `xm_11_21`。它由 `tree.nodes[].keysetids[]` 引用，包含频道、节目指南、信号信息和大量正反码：

```text
11_Cable _ Satellite box.json
  → 11_Cable _ Satellite box/China Satellites_2268.json
    → tree.nodes[].keysetids[] 中的 xm_11_21
      → 11_Cable _ Satellite box/models/xm_11_21.json
```

```json
{
  "data": {
    "_id": "xm_11_21",
    "version": 1000,
    "frequency": 37800,
    "seceret_key": null,
    "key": {
      "power": "<Base64 密文省略>",
      "power_r": "<Base64 密文省略>",
      "ch+": "<Base64 密文省略>",
      "ch+_r": "<Base64 密文省略>",
      "SIGNAL DISPLAY": "<Base64 密文省略>",
      "guide": "<Base64 密文省略>",
      "page_up": "<Base64 密文省略>",
      "sound_channel": "<Base64 密文省略>"
    }
  }
}
```

这个例子也能区分 `device=11` 与空的 `device=2`：二者名字都与电视接收设备有关，但索引和型号目录完全不同。

### `power` 功能的完整处理过程

`xm_11_21.json` 的原始 `power` 字段：

```json
{
  "version": 1000,
  "seceret_key": null,
  "frequency": 37800,
  "_id": "xm_11_21",
  "key": {
    "power": "vRjIdyCacNx0kltsRy01TUN74omO8UCBDyQqJsGASKzVqIgpLvF4VxQG62AXlUenJXdPNUiJtN2kjGrBJpl/O/ErA9KUWWj4xeerwu/cMzN4RZaj/XCV8SvgjST2UjFX"
  }
}
```

1. `RemoteCatalog.loadRemotes()` 从品牌树的 `keysetids[]` 找到型号，`loadRemote()` 再读取 37800 Hz 和 `key.power`。
2. `fixedCommands()` 同时识别 `power_r`，但 `FixedIrTransmitter.transmit()` 第一次发送选择上面的正向码。
3. `FixedIrDecoder.decode()` 得到 40 个微秒时长，总时长 217000 µs。
4. 最终发送：

```java
manager.transmit(37800, new int[] {
    845,845,1718,823,844,1667,1717,1653,844,845,845,845,844,845,844,845,
    1718,1653,1717,86551,844,845,1717,822,844,1667,1718,1652,844,845,844,846,
    844,846,843,846,1716,1653,1717,86539
});
```

## 按键结构

代表性按键：

```text
0～9, power
ch+, ch-, vol+, vol-, mute
up, down, left, right, ok
menu
```

## 载波

- 范围：37390～38820 Hz；
- 型号数量少，没有一个频率占绝对多数。

## 编码结论

13 个型号全部使用字符串固定码，结构与电视固定码相同，不包含状态型规则。
