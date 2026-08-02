# 小米万能遥控红外码库解析与 Android 控制实验

本文使用的遥控器数据全部来自 [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database)。

这个项目读取小米万能遥控码库中的设备、品牌和型号 JSON，并把固定红外码或动态生成的空调波形交给 Android `ConsumerIrManager` 发送。

README 只记录所有设备共用的结构。每种设备的型号数量、按键、载波和特殊格式放在独立文档中；每篇设备文档还使用一段 assets 原始 JSON，逐步说明一个功能读取哪些字段、经过哪些方法、得到什么波形以及最终传给 `ConsumerIrManager` 的参数。

## 1. 设备文档

| device | 设备 | 品牌索引 | 型号文件 | 文档 |
|---:|---|---:|---:|---|
| 1 | 电视 | 503 条，457 个唯一品牌 | 2966 | [电视](docs/01-tv.md) |
| 2 | 电视机顶盒 | 0 | 0 | [电视机顶盒](docs/02-set-top-box.md) |
| 3 | 空调 | 280 | 1828 | [空调与格力配置](docs/03-air-conditioner.md) |
| 4 | DVD | 247 | 839 | [DVD](docs/04-dvd.md) |
| 6 | 风扇 | 133 | 422 | [风扇](docs/06-fan.md) |
| 8 | 功放 | 202 | 455 | [功放](docs/08-av-receiver.md) |
| 10 | 投影仪 | 118 | 370 | [投影仪](docs/10-projector.md) |
| 11 | 卫星电视 | 2 | 13 | [卫星电视](docs/11-cable-satellite-box.md) |
| 12 | 盒子 | 136 | 221 | [盒子](docs/12-box.md) |
| 13 | 单反 | 12 | 18 | [单反](docs/13-camera.md) |
| 14 | 灯泡 | 31 | 102 | [灯泡](docs/14-light.md) |
| 15 | 空气净化器 | 32 | 50 | [空气净化器](docs/15-air-cleaner.md) |
| 16 | 热水器 | 25 | 70 | [热水器](docs/16-water-heater.md) |

当前共有 13 种设备、1675 个品牌文件和 7354 个型号文件。

## 2. 通用索引关系

数据按下面的关系加载：

```text
devices.json
  → <device>_<name>.json
    → <device>_<name>/<brand>_<brandId>.json
      → tree.nodes[].keysetids[] / others[]._id
        → <device>_<name>/models/<modelId>.json
```

不能只扫描 `models` 目录。型号与设备、品牌的关系来自上级 JSON；没有被品牌文件引用的型号不应该自动加入结果。

### 2.1 `devices.json`

`devices.json.data[]` 保存设备类型：

- `deviceid`：设备编号；
- `language[]`：多语言名称；
- `long_pressed_match`：长按匹配标记；
- `providers[]`：数据提供方，例如 `kk`、`mx`、`xm`、`mi`、`yk`；
- `select_by_location`、`prunning_options`、`info`、`logo`：地区、匹配和展示信息。

设备索引和资源目录使用相同名称。例如：

```text
1_TV.json                 1_TV/
3_AC.json                 3_AC/
10_Projector.json         10_Projector/
```

### 2.2 品牌索引

设备索引的 `data[]` 通常包含：

- `deviceid`：设备编号；
- `brandid`：品牌编号；
- `name`：品牌名称；
- `priority`：排序优先级；
- `providers[]`：品牌的数据提供方。

电视索引存在重复 `brandid`，503 条索引记录对应 457 个唯一品牌。加载时应按品牌 ID 去重并保留第一次出现的顺序。

电视机顶盒的索引结构不同：

```json
{
  "data": {
    "count": 0,
    "data": []
  }
}
```

它当前没有品牌和型号数据。

### 2.3 品牌文件

品牌文件从两个位置引用型号：

```text
data.tree.nodes[].keysetids[]
data.others[]._id
```

同一个型号可能同时出现，加载时按 `_id` 去重。

`others[]` 还可能包含：

- `source`：数据来源；
- `order`：顺序；
- `key`、`frequency`、`type`：部分文件会内嵌完整型号内容。

### 2.4 型号文件

固定码型号通常是：

```json
{
  "status": 0,
  "encoding": "UTF-8",
  "language": "zh_CN",
  "data": {
    "_id": "131_5242",
    "frequency": 37640,
    "version": 1,
    "key": {
      "power": "Base64..."
    }
  }
}
```

常见字段：

- `_id`：型号 ID；
- `frequency`：载波频率，单位 Hz；
- `version`：数据版本；
- `key`：按键名到红外数据的映射；
- `seceret_key`：原数据中的拼写，部分 MX/XM 数据存在；
- `type`：当前主要出现在空调数据中。

不能只根据 `_id` 前缀决定算法。`kk`、`mx`、`xm` 和纯数字 ID 需要结合型号字段和 `key` 的值类型判断。

## 3. 通用固定码格式

除状态型空调外，当前有数据的设备型号中，`key` 的值都是字符串。

常见结构：

```json
{
  "power": "正向码",
  "power_r": "反向码"
}
```

- 普通键名保存正向码；
- `<key>_r` 保存同一按键的反向码；
- 不是所有按键都有反向码；
- `_r` 不能作为独立按键处理。

当前发送逻辑在正向码和反向码之间轮换；没有反向码时始终使用正向码。

### 3.1 解密流程

当前固定码使用下面的公共流程：

```text
Base64
  → AES-256-ECB/NoPadding
  → 删除明文尾部 ASCII 空格
  → GZIP 解压
  → Gson 解析 JSON int[]
  → Android 红外波形
```

公共 AES 密钥：

```text
fd7e915003168929c1a9b0ec32a60788
```

实现时需要注意：

- 密钥是 32 个 ASCII 字节，不是 16 字节十六进制数据；
- Cipher 是 `AES/ECB/NoPadding`；
- 明文尾部使用 `0x20` 补齐；
- 只能删除尾部空格，不能删除 `0x00`；
- GZIP 校验数据中可能包含零字节；
- 解压结果是 JSON 整数数组。

`seceret_key` 虽然拼写错误，但应该保留原字段名。当前样本中非空值与上面的公共密钥一致。

## 4. Android 红外发送

Android 使用：

```java
ConsumerIrManager.transmit(frequency, pattern);
```

- `frequency`：型号提供的载波频率，单位 Hz；
- `pattern`：载波开启和关闭的微秒时长数组；
- 数组从载波开启开始；
- 每个时长必须大于 0。

Manifest：

```xml
<uses-permission android:name="android.permission.TRANSMIT_IR" />
<uses-feature
    android:name="android.hardware.consumerir"
    android:required="false" />
```

发送前检查：

```java
manager != null && manager.hasIrEmitter()
```

部分 ROM 会拒绝第三方应用使用红外，并抛出：

```text
java.lang.SecurityException: Access denied, requires: android.permission.TRANSMIT_IR
```

这是系统或厂商权限问题，不是 JSON 或波形解码问题。
