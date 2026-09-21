# Design: add-balance-test-harness

## Context

- 两条数值曲线已落地：`balance-player-monster-stats`（玩家 ×10/阶、护壁指数、怪物预算）与
  `balance-danmaku-weapon-stats`（武器 3 带、词条池）。
- 实机验证痛点：手动进阶只能得到带 roll 的随机值、凑装备耗时，无法稳定复现"标准同阶战斗"，
  也无法在两端 roll 检验难度。
- 既有可复用资产：`balance/MonsterStatBudget`（玩家 DPS/EHP 曲线与怪物预算）、
  `grace/GraceService`（写阶级贡献）、`spirit/attr/PlayerAttributes`（写属性容器）、
  四类弹幕实体（`SphereDanmaku`/`KnifeDanmaku`/`TalismanDanmaku`/`LaserDanmaku`）、
  `FairyEntity`（飞行/悬停基座）、`DanmakuWhitelists`。

## Goals / Non-Goals

**Goals:**
- 一条命令把玩家配置到任意阶级的**标准数值**（可复现、无 roll 偏差），并可开无限灵力测持续 DPS。
- 每个阶级两个测试 BOSS：`min`（该阶区间最低）与 `max`（最高），同阶标准装备 TTK ≥ 2 分钟。
- 10 种东方美学弹幕模式，BOSS 每 3 秒随机切换，按 HP 分阶段。
- 纯测试设施：不影响正式玩法、不新增用户可见内容（命令权限 2）。

**Non-Goals:**
- 不产出正式美术（复用妖精模型占位）。
- 不定稿正式 BOSS 数值。
- 不做持久化/多人同步。

## Decisions

### D1 命令集 `/gs_test`（权限 2）

| 子命令 | 行为 |
|---|---|
| `player <tier:1..5>` | 把玩家配置到该阶**标准数值**：清空既有 grace 贡献与台账 → 按 **base 中点**（不 roll）应用 1..N 阶 → 阶级=N → 池满 → 重算原版属性桥 → 发一套**标准装备**（武器 + 该带等级核 + 球核 + 该带增幅核） |
| `player <tier> [infinite]` | 同上；`infinite` 额外开启"测试无限灵力"（每 tick 补满池） |
| `boss <tier:1..5> <min\|max>` | 在玩家上方生成对应测试 BOSS（复用妖精悬停位置），带 `DanmakuWhitelists` 免误伤同类 |
| `clear` | 移除本命令生成的全部测试 BOSS |
| `reset` | 玩家回到凡人（0 阶、空池、清 grace 贡献/台账、关无限灵力），回收标准装备可选 |

- "标准数值" = grace 表 **base 中点**（不含 roll）：灵力池/灵力强度走台账，其余键写 `sourceId="test"` 贡献组（与 `grace_tier_N` 隔离）。
- 标准装备按武器带：`tier 1-2 → Lv1/T1`，`3-4 → Lv2/T2`，`5 → Lv3/T3`。

### D2 测试无限灵力态

- 按玩家持久化的测试标记（或命令作用域内的临时字段）；开启时 `PlayerTick` 把池补满到有效上限。
- 仅测试用；`reset` 关闭。不参与正式死亡/同步语义（池满即可）。

### D3 测试 BOSS 数值标定（TTK ≥ 2 分钟）

标准玩家 DPS 取自 `MonsterStatBudget.referencePlayerDps(tier)`（属性基准、同带武器、无词条）：

```
min 变体：HP = DPS(tier) × testBossMinSeconds   (默认 120s = 2min)
          弹伤 = EHP(tier) / testBossMinHits    (默认 10，较弱)
max 变体：HP = DPS(tier) × testBossMaxSeconds   (默认 180s = 3min)
          弹伤 = EHP(tier) / testBossMaxHits    (默认 7，较强)
```

- HP 与弹伤再从该阶预算带上做 min/max 端点取值（"极限最低值 / 极限阶级最高值"）。
- 目的：给 BOSS 足够时间走完 10 种模式与阶段切换（原作换符卡节奏）；玩家需在生存前提下持续输出。
- 非弹幕伤害沿用东方怪 90% 减免（`TouhouMonster`），保证必须用弹幕测试。

### D4 阶段与模式选择

BOSS 每 **patternInterval**（默认 60 tick = 3s）随机选一种未在最近连续重复的模式发射；按 HP 分阶段：

| 阶段 | HP 区间 | 可用模式 | 间隔 | 全局弹速 |
|---|---|---|---|---|
| P1 | 100%–66% | 1–5 | 3.0s | ×1.0 |
| P2 | 66%–33% | 4–9 | 2.5s | ×1.1 |
| P3 | 33%–0% | 1–10 | 2.0s | ×1.25 |

- 切换阶段时给一次短暂停顿/自机狙提示（原作换阶段感），并在 HUD 播报阶段。
- 全部参数走 config `testHarness` 段。

### D5 十种弹幕模式（东方美学）

统一用现有四类弹幕实体 + 计算发射方向；每模式在**当前 3 秒槽内**按自身节奏发射（连发或单次爆发）。
伤害用 D3 的"单发基准 × 模式系数"。

| # | 名称 | 实体 | 几何/节奏 | 备注 |
|---|---|---|---|---|
| 1 | 环弹「八方圆舞」 | 球 | 16 向等角环，每 20 tick 一环（每槽 3 环），弹速 ×0.4 | 基础铺场 |
| 2 | 螺旋「回旋涡」 | 球 | 双流反向，每 3 tick 各 1 发，角度每发 +14° | 压迫走位 |
| 3 | 狙弹「三途刺」 | 飞刀 | 自机狙 3 向，夹角 8°，每 15 tick 一组 | 穿透，逼闪避 |
| 4 | 扇弹「孔雀开屏」 | 球 | 9 向扇形张角 60°，弹速 ×0.5，每 25 tick | 中距覆盖 |
| 5 | 星芒「十字星辉」 | 球 | 固定 8 向（45°）+ 次环旋转 22.5°，每 30 tick | 静止阵形 |
| 6 | 花弹「彼岸花开」 | 球 | 5 花瓣 vs 5 子弹（共 25），速度正弦调制，每 40 tick | 图案化 |
| 7 | 迟弹「缓速散华」 | 球 | 12 向慢速弹，出生后每 10 tick 加速（上限 ×1.5） | 变速弹 |
| 8 | 激光「贯穿之矢」 | 激光 | 自机狙，延迟 0.7s + 持续 1.2s，微弱旋转扫射 | 站桩惩罚 |
| 9 | 追符「执念灵符」 | 灵符 | 6 张追踪符，拾取玩家，转向 90°/s | 追踪压制 |
| 10 | 弹幕雨「无尽散华」 | 球 | 槽内 2 发/tick，随机角度/速度（0.3–0.8）×40 发 | 随机乱流 |

- 发射方向计算在 `TestBossPatternGoal` 内完成（旋转/正弦/随机/自机狙），实体复用既有弹幕。
- 灵符/激光沿用既有延迟与追踪参数；组合成"图案"而非新增实体类型。
- 视觉：球/灵符/激光沿用辉光；飞刀不发光。颜色按阶段使用品阶色（`TierPalette`）。

### D6 生成/渲染/清理

- 实体注册 `balance_test_boss`；渲染复用妖精 GeckoLib 模型 + 占位贴图（`docs/asset-placeholder-list.md` 登记），按 test tier 染色以便辨识。
- 生成时写 NBT：`test_tier`、`test_variant`（min/max）、预算出的 HP/弹伤、当前阶段/模式。
- `clear` 通过 ServerLevel 按实体类型移除；BOSS 死亡自动掉落无（测试实体不产出）。

## Risks / Trade-offs

- [无限灵力测出"非正常" DPS] → 仅测试态；`reset`/关服即消；文档标注。
- [2 分钟 TTK 需持续灵力] → 无限灵力开关即为此设计；否则低阶池不足以支撑 2 分钟。
- [10 模式实现量大] → 全部复用既有实体与方向计算，无新实体类型；模式参数进 config，可逐条校准。
- [测试 BOSS 被误当正式内容] → 命令权限 2 + 实体名/贴图标注"测试"；不入正式掉落与图鉴。
- [与正式 BOSS 混淆] → spec/命名显式标注 `balance-test-harness` 为测试设施。

## Open Questions

- 标准装备是否要在 `player` 命令里默认发放（还是另设 `gear` 子命令）？
- min/max 变体的 TTK 目标（2min/3min）与"极限 roll 端点"的取舍：是否 min 也保底 2min？
- 阶段数（当前 3）与每阶段模式池划分是否够"原作感"。
