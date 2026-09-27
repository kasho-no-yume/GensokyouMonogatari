## REMOVED Requirements

### Requirement: 召唤仪式多方块
**Reason**: 该需求定义的激活路径是"手持召唤催化剂右键核心"，所依赖的 `summon_circle` pattern 从未存在（实际 pattern 为 `hyakki_yagyo_circle`），因此 `SummonCatalystItem` 一直是无法匹配的死代码，两个催化剂物品也从未真正可用。召唤仪式已由 `hyakki-yagyo-summon` 能力以「祭品台配方 + UI 按钮 + 无门票充能」重写，催化剂链路整体退役。

**Migration**: 玩家侧无需迁移——催化剂物品本就无法使用。召唤仪式的激活方式改为在祭品台上摆齐配方原料后点击核心 GUI 的「召唤」按钮；新仪式由 `hyakki-yagyo-summon` 完整定义。`ppoint` / `bpoint` 保留（妖精/琪露诺/铃奈鹤产出，且被仪式核心/祭品台/仪式石配方消耗）。

## MODIFIED Requirements

### Requirement: 芙兰朵露低阶形态
BOSS 实体（registry name `flandre`）SHALL 为敌对 Monster：最大生命/攻击/护甲/移速/经验从配置读取（移速默认 0.3）；被追踪渲染期间 SHALL 显示血条且百分比实时等于生命占比。血条的**呈现造型**由 `touhou-boss-bar` 能力规定（东方 BOSS 统一使用咒符条），本能力只规定"存在一条血条且读数正确"，MUST NOT 再指定其为原版蓝色分段样式。

#### Scenario: 属性与血条
- **WHEN** 玩家接近被召唤的芙兰朵露并攻击
- **THEN** 屏幕上方出现其血条（按 `touhou-boss-bar` 呈现为咒符条）并随伤害同步下降，属性值等于配置值
