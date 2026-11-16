## MODIFIED Requirements

### Requirement: bousen 渲染态字段语义

`RitualRenderState` SHALL 新增 kind `bousen`（忘川灯坛），其辅助字段解释如下，MUST NOT 复用既有 kind 的字段语义：

- `enabled` = 仪式启停态
- `tier` = 结构等级（1/2/3）；客户端据此取该阶 pattern 切片本地推导蜡烛坐标
- `movingMask` = **蜡烛点亮位掩码**，bit i = 规范序第 i 根蜡烛点亮。bit i 在服务端与客户端 MUST 指向同一根蜡烛
- `minY`/`maxY`/`period`/`inCount` = **不使用**
- `linkPos` = **不使用**

蜡烛总数为 16 / 32 / 64，恰为 1 / 2 / 3 个位掩码所需宽度；「产灵中」的坛面金光铺光判据 SHALL 为**点亮数 `lit > h`（蜡烛总数的一半）**，即 `Long.bitCount(movingMask) > 蜡烛总数/2`。客户端 SHALL 自行从 `movingMask` 判定，**MUST NOT** 为此另占渲染态标志位，也 MUST NOT 依赖服务端下发实时速率（服务端产灵速率按熄灭数连续变化，但金光只需布尔门）。满掩码计算 MUST 特判 64：Java 的 `1L << 64` 等于 `1L`（移位数按 64 取模），直接写 `(1L << n) - 1` 在 `n == 64` 时得 `0` 而非 `-1`。

蜡烛坐标 MUST NOT 经 `linkPos` 承载：`MAX_CHANNELS` 为 64，64 根恰好顶满、零余量，且既有 `forgeBurnMask` 类构造已占用 bit0 作标志位，将来增设第 65 根蜡烛会静默截断。坐标一律由客户端读**同一份**已同步的 pattern JSON 推导，沿用 `KIND_REIYOKU` 因 433 格占地超限而确立的既定路径。

#### Scenario: 产灵铺光由点亮过半判定

- **WHEN** 客户端收到 `bousen` 渲染态，`enabled` 为真且 `movingMask` 的置位数大于该阶蜡烛总数的一半
- **THEN** 客户端判定为产灵中并绘制坛面铺光，无须服务端额外下发标志位或速率

#### Scenario: 半阈值及以下不铺光

- **WHEN** 客户端收到 `bousen` 渲染态，`movingMask` 置位数小于或等于该阶蜡烛总数的一半
- **THEN** 客户端不绘制坛面产灵铺光（rate=0）

#### Scenario: 三阶满掩码不为零

- **WHEN** 客户端为 3 阶（64 根蜡烛）计算满掩码
- **THEN** 结果为全 1 的 `long`，MUST NOT 因移位取模而得到 0

#### Scenario: 坐标不进 linkPos

- **WHEN** 审查 `bousen` 渲染态的构造
- **THEN** `linkPos` 为空数组，64 根蜡烛坐标由客户端从已同步的 pattern 切片推导，未顶满 `MAX_CHANNELS`

#### Scenario: 位序跨端一致

- **WHEN** 服务端熄灭第 k 根蜡烛并推送新掩码
- **THEN** 客户端据此变为淡红的蜡烛，与服务端实际改写为熄灭的是同一根
