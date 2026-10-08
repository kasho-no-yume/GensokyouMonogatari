## ADDED Requirements

### Requirement: tsukumogami 渲染态字段语义
`RitualRenderState` SHALL 新增 kind `tsukumogami`，字段语义如下，MUST NOT 复用既有 kind 的字段语义：

- `enabled` = 仪式启停态
- `tier` = 结构等级（0/1/2）
- `minY`/`maxY` = 结构垂直范围（烟雾上升高度的参考）
- `period`/`inCount` = **不使用**
- `linkPos` = **不携带台位坐标**（该槽数有上限且 mask 足够）：客户端据 `movingMask` 的位掩码自行推导台位，坐标一律由客户端读本地同步的 pattern JSON 推导
- `movingMask` = **燃烧位掩码**：bit0 = 存在燃烧批次（仪式一次只烧一批，整座统一呈烟，无台位分址）。`movingMask == 0` 时客户端判定为非燃烧态、零烟雾

#### Scenario: 燃烧位掩码驱动烟雾
- **WHEN** 客户端收到 `tsukumogami` 渲染态且 `movingMask` 的 bit0 置位
- **THEN** 整座仪式的各祭品台位置统一绘制烟雾

#### Scenario: 全位熄灭即零烟雾
- **WHEN** `movingMask` 为 0 且 enabled 为真
- **THEN** 客户端绘制零烟雾（停等/待机态），不逐帧留空
