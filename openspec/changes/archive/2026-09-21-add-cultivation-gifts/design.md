# Design: add-cultivation-gifts

## Context

- 玩家属性套件（`player-attribute-suite`）现 15 键，`final = base + Σ贡献`（加区，百分比键受 cap 封顶）。
- `PlayerAttributes.refreshBridged` 已把 `health_bonus`/`move_speed_bonus` 桥到原版属性（幂等重算）。
- 受弹结算分两条：`DamageEventHandler`（玩家 danmaku）与 `TouhouCombatEvents`（东方怪非弹幕减免 90%）。
- 玩家侧**非弹幕**受伤目前无任何套件减伤；玩家近战无套件加成。

## Goals / Non-Goals

**Goals:** 新增 3 键，短命名；跳跃最多 +3 格；物抗最多 85% 且为独立乘区；近战最多 +100；数值走 config 与阶级 roll。

**Non-Goals:** 二段跳/空中控制；近战特效；变身改写域；与东方怪抗性的结算顺序强约束。

## Decisions

### D1 命名

| 键 id | 显示名(zh/en) | 语义 | flat | cap |
|---|---|---|---|---|
| `jump` | 跳跃 / Jump | 额外跳跃高度（格） | 是 | 3.0 |
| `phys_resist` | 物抗 / Phys Resist | 非弹幕伤害减免（比例） | 否 | 0.85 |
| `melee_damage` | 近战 / Melee | 近战附加物理伤害 | 是 | 100 |

（原"跳跃提高/非弹幕抗性提升/近战物理伤害提升"过长，统一缩短。）

### D2 跳跃：桥接原版 `jump_strength`

- 玩家跳跃初速 = `minecraft:jump_strength` 属性值（默认 v0 = 0.42），高度 `h(v) = v² / (2g)`，g = 0.08/tick，
  基准 h0 = 0.42²/0.16 ≈ 1.10 格。
- 桥接 modifier（ADD_VALUE）：`delta = sqrt(2g · (h0 + blocks)) − v0`（blocks 为 `jump` 最终值，钳 ≥0）。
  - +1 格 ≈ +0.19 强度；**+3 格 ≈ +0.39 强度**。
- 走既有幂等重算模式（`AttributeBridgeIds.JUMP`），与生命/移速桥一致。
- 副作用（高跳 → 摔伤）由 `phys_resist` 对冲，符合"馈赠"设定。

### D3 物抗：独立于护甲的乘区 + 豁免

- 新增事件处理器（玩家为非弹幕受害者时）：`amount ×= (1 − clamp01(phys_resist))`。
- "独立乘区"= 相对原版护甲/附魔独立乘算（护甲在 `actuallyHurt` 内单独结算），非套件容器结构变更。
- **豁免**：`GENERIC_KILL`（`/kill`）与 `FELL_OUT_OF_WORLD`（虚空）不减（同东方怪约定）；弹幕伤害不走此通道（走护壁指数）。
- 含摔落伤害（D2 的对冲）。

### D4 近战：附加物理伤害

- 同一处理器内：当 `source.getEntity() instanceof ServerPlayer` 且 `source.is(DamageTypes.PLAYER_ATTACK)` 时
  `amount += finalValue(MELEE_DAMAGE)`。
- 顺序：**先加近战附加、后乘物抗**（受害者的物抗会削减这部分，语义为"物理伤害被抗性减少"）。
- 与 `TouhouCombatEvents`（东方怪 90%）的先后由事件监听序决定，不做强约束（测试实体不敏感）。

### D5 逐阶数值（增量；上限 = T5 满 roll）

| 键 | T1 | T2 | T3 | T4 | T5 | roll | cap | 累计中点 |
|---|---|---|---|---|---|---|---|---|
| `jump` | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 | 0.5 | 3.0 | 0.4 / 0.8 / 1.2 / 1.6 / 2.0（满 roll 3.0） |
| `phys_resist` | 0.04 | 0.08 | 0.12 | 0.16 | 0.23 | 0.35 | 0.85 | 4% / 12% / 24% / 40% / 63%（满 roll 85%） |
| `melee_damage` | 5 | 7 | 13 | 25 | 30 | 0.25 | 100 | 5 / 12 / 25 / 50 / 80（满 roll 100） |

- **上限语义**：用户给定的是"最大值"——故设计使 **T5 满 roll = 给定上限**（3 格 / 85% / 100），中点低于上限；硬上限兼作来源叠加的安全闸。
- 各行 roll 取值使满 roll 恰为上限：`jump` roll 0.5（2.0×1.5=3.0）、`phys_resist` roll 0.35（0.63×1.35≈0.85）、`melee_damage` roll 0.25（80×1.25=100）。
- 全部写入 grace 表（`tier,key,base,roll`）；洗练整组重掷。

### D6 注册表扩展

`AttributeKey` 由 15 → 18 键；三键均 `transformRewritable=false`（不进降神改写域）。
`AttributeKeyRegistryTest` 断言数改 18。

## Risks / Trade-offs

- [原版 jump_strength 公式近似] → 用 `h=v²/2g` 近似，误差以 config 系数可调；若实机偏高/偏低再修。
- [物抗叠加护甲过强] → cap 0.85 + 与护甲独立乘算，实机校准；豁免斩杀/虚空。
- [近战 +100 对高护甲目标收益骤减] → 走预护甲加算，属"物理伤害"语义；如需真伤另议。
- [与东方怪 90% 减免顺序不定] → 仅影响近战对东方怪的边际，测试无感；文档标注。
- [旧档缺附件] → 三键基准 0，向后兼容。

## Open Questions

- 显示名是否用更短的"跳跃/物抗/近战"，还是"跳跃/物抗/近战"之外的主题词（如"身法/护体/怪力"）？
- 物抗是否也减免摔落（当前是）——若不想要，可加排除。
- 近战附加是否应对玩家 PvP 生效（当前 `PLAYER_ATTACK` 一律生效）。
