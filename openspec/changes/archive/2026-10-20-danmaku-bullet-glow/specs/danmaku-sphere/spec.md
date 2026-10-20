## MODIFIED Requirements

### Requirement: Sphere visual glow effect
普通球状弹幕的本体与外辉光 SHALL 由**同一张贴图**承载，并以**单次绘制**（单 quad、单 pass、单 draw call）呈现，MUST NOT 为球弹额外增加渲染层、draw call 或顶点。

四边形几何的球弹 SHALL NOT 渲染独立的外发光层与核心亮核层：这三层结构使填率随层数线性增长（历史实测结论为"Double additive 未排序后单体负载仍不可控，只能靠减遍数"），故 MUST NOT 恢复。

模型类几何（五角星柱）不在本条约束内：其分层渲染 SHALL 沿用既有的「等比放大 + 沿外法向推离」实现，其推离量 MUST 远大于深度量化噪声又远小于一个屏幕像素。

外辉光 SHALL 随弹体尺寸等比缩放：辉光半径与本体半径之比 MUST 是贴图内的固定比例，MUST NOT 需要独立参数。

本体区 SHALL 保持**不透明**（alpha = 255）且足够饱满，MUST NOT 退化成一个小核。外辉光区与本体之间 MAY 存在分离（窄透明缝）——这是"球旁边有一圈"在单层里的唯一实现手段（顶点色只能相乘，贴图里做不出比本体更亮的环）；但分离 MUST NOT 使弹读作"环"而不是"球"。

外辉光的可见外缘 MUST 收窄，MUST NOT 铺满整个 quad——辉光铺满时弹读作"一团纯色球"，环完全不可见。

外辉光区 SHALL 与本体区一致地写深度。

#### Scenario: 球弹为单次绘制
- **WHEN** 渲染一枚四边形几何的球弹
- **THEN** 该弹只提交一个 quad，MUST NOT 出现外发光层或亮核层的额外绘制

#### Scenario: 辉光随尺寸等比
- **WHEN** 同一视觉档案的球弹分别以 0.3 格与 0.8 格尺寸渲染
- **THEN** 两者的本体半径与外辉光半径之比相同

#### Scenario: 环可见
- **WHEN** 检视球弹贴图
- **THEN** 实心核之外存在一圈**可辨认**的环，MUST NOT 只是一团连续渐变

#### Scenario: 本体够饱满
- **WHEN** 检视球弹贴图的实心核
- **THEN** 其宽度不低于贴图宽度的 45%，MUST NOT 退化成小核

#### Scenario: 辉光区写深度
- **WHEN** 一枚球弹位于水面之前
- **THEN** 其外辉光与本体一并遮挡身后的水面，MUST NOT 只遮挡本体

#### Scenario: 模型类几何不受单通约束
- **WHEN** 渲染一枚五角星柱
- **THEN** 其分层渲染沿用既有的分层实现，MUST NOT 因本条要求被压成单层

### Requirement: Sphere texture asset
普通球状弹幕 SHALL 使用一张**去色**（RGB 通道恒为白）的高分辨率径向贴图，其全部色相由顶点色提供，MUST NOT 烘焙色相。

该贴图 SHALL 在**同一张图**内呈现**「实心核 + 分离环 + 收窄外晕」**三段结构：实心核的宽度 SHALL 不低于贴图宽度的 45%；环 SHALL 以柔边表达；外辉光的可见外缘 MUST NOT 超过贴图半宽的 0.9。

环与核之间 MAY 存在窄透明缝，但 MUST NOT 出现「小核 + 硬边粗环」——那是现役 LOD 贴图读作"甜甜圈"的原因。

贴图 SHALL 至少 128×128。alpha MUST 沿径向**连续**变化，MUST NOT 用纯色块或硬边台阶表达。

球弹贴图 SHALL 以**线性过滤 + mipmap** 采样。最近邻且无 mipmap 的采样会把软晕切成方块并在缩小时持续闪烁。

资源包替换球弹贴图 MUST NOT 影响任何玩法判定。

#### Scenario: 贴图去色
- **WHEN** 检视球弹贴图的 RGB 通道
- **THEN** 全部像素为白，MUST NOT 存在烘焙色相

#### Scenario: 同图含核环与辉光
- **WHEN** 检视球弹贴图
- **THEN** 同图内既有不透明实心核、又有可辨认的环、又有向外衰减的外晕

#### Scenario: 实心核占比
- **WHEN** 测量球弹贴图的实心核宽度
- **THEN** 其不低于贴图宽度的 45%，MUST NOT 低于四成

#### Scenario: 环不是硬边台阶
- **WHEN** 沿球弹贴图的半径方向采样 alpha
- **THEN** 其值连续变化，MUST NOT 出现只有两三个离散台阶的阶梯

#### Scenario: 资源包替换不改变语义
- **WHEN** 资源包替换了球弹贴图
- **THEN** 渲染随之改变，MUST NOT 影响任何玩法判定
