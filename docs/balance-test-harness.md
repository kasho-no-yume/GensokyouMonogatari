# 数值测试台（/gs_test）

> 开发/管理员设施（命令权限 2），用于实机验证 `balance-player-monster-stats` 与
> `balance-danmaku-weapon-stats` 的数值曲线。**不属于正式玩法**，测试实体无正式掉落、不入图鉴。
> 变更来源：`add-balance-test-harness`。

## 命令

| 命令 | 作用 |
|---|---|
| `/gs_test player <1..5>` | 一键把玩家配置为该阶**标准数值**（grace 表 base 中点、无 roll），并发放该武器带的标准装备 |
| `/gs_test player <1..5> infinite` | 同上，另开"测试无限灵力"（tick 补满），用于持续 DPS 观测 |
| `/gs_test boss <1..5> <min\|max>` | 在玩家上方生成该阶测试 BOSS（min=区间最低值，max=最高值） |
| `/gs_test clear` | 清除附近（160 格）的测试 BOSS |
| `/gs_test reset` | 玩家回凡人测试态，关闭无限灵力 |

## 标准数值与来源隔离

- "标准数值" = grace 表 **base 中点**（不做随机 roll），确定性、可复现。
- 非池键写入独立 sourceId `test_standard`，与 `grace_tier_N` / `command` 隔离；
  池两键（最大灵力/灵力强度）受单写规约约束，只能经阶级台账写入——命令会覆盖 1..N 阶台账份额。
- 标准装备按武器带发放：`阶1-2→Lv1/T1`、`阶3-4→Lv2/T2`、`阶5→Lv3/T3`（主武器 + 等级核 + 球核 + 增幅核）。

## 测试 BOSS 标定

```
min 变体：HP = 标准玩家DPS(tier) × testBossMinSeconds (默认 120 = 2min)
          弹伤 = 标准玩家EHP(tier) / testBossMinHits (默认 10)
max 变体：HP = 标准玩家DPS(tier) × testBossMaxSeconds (默认 180 = 3min)
          弹伤 = 标准玩家EHP(tier) / testBossMaxHits (默认 7)
```

- `标准玩家DPS/EHP` 取自 `balance/MonsterStatBudget`（属性基准 + 同带武器 + 护壁/擦弹）。
- 目标：**同阶标准装备下 min 变体 TTK ≥ 2 分钟**，给足换阶段/放符卡时间。
- 移动/悬停与小妖精一致；非弹幕伤害沿用东方怪 90% 减免；只用弹幕才能有效击杀。

## 十种弹幕模式与阶段

BOSS 每 `testPatternIntervalTicks`（默认 3 秒）随机切换一种模式（避免连续重复）：

| # | 模式 | # | 模式 |
|---|---|---|---|
| 1 | 环弹「八方圆舞」（16 向环） | 6 | 花弹「彼岸花开」（5×5 花瓣） |
| 2 | 螺旋「回旋涡」（双流反向） | 7 | 迟弹「缓速散华」（慢速大弹） |
| 3 | 狙弹「三途刺」（自机狙 3 向飞刀） | 8 | 激光「贯穿之矢」（自机狙激光） |
| 4 | 扇弹「孔雀开屏」（9 向扇形） | 9 | 追符「执念灵符」（追踪符） |
| 5 | 星芒「十字星辉」（8 向+旋次环） | 10 | 弹幕雨「无尽散华」（随机乱流） |

阶段（按生命阈值，config 可调）：P1 100-66% 用模式 1-5；P2 66-33% 用 4-9；P3 33-0% 用全部 + 更快间隔/弹速。

全部参数在 config `testHarness` 段；几何（角度/数量）在 `entity/TestBossPattern`。

## 建议测试流程

1. `/gs_test player <N> infinite` → 确认 HUD 属性与理论表一致（可用 `/gs_attributes show` 对照）。
2. `/gs_test boss <N> min` → 观察阶段与 10 模式覆盖、记录 TTK（应 ≥ 2 分钟）。
3. `/gs_test boss <N> max` → 检验难度上限。
4. `/gs_test clear` / `/gs_test reset` 善后。
