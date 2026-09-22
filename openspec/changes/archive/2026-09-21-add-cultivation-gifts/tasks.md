# Implementation Tasks: add-cultivation-gifts

> 状态：注册表三键、配置、阶级表、三处消费点、语言与单测均已落地（`gradlew compileJava test` 通过）。
> 归档由本会话统一执行。

## 1. 注册表与配置

- [x] 1.1 `AttributeKey` 新增 `JUMP`（flat, cap 3.0）、`PHYS_RESIST`（percent, cap 0.85）、`MELEE_DAMAGE`（flat, cap 100），均 `transformRewritable=false`
- [x] 1.2 `GensokyouConfig.playerAttributes` 增基准/封顶：`baseJump`/`jumpCap`、`basePhysResist`/`physResistCap`、`baseMeleeDamage`/`meleeDamageCap`
- [x] 1.3 `grace` 表新增 3 键 × 5 阶行：jump 0.4（roll 0.5）；phys_resist 0.04/0.08/0.12/0.16/0.23（roll 0.35）；melee_damage 5/7/13/25/30（roll 0.25）——**T5 满 roll 分别为 3.0 / 0.85 / 100**
- [x] 1.4 lang：`attribute.gensokyou.jump/phys_resist/melee_damage`（zh_cn 跳跃/物抗/近战；en_us Jump/Phys Resist/Melee）

## 2. 消费点

- [x] 2.1 `AttributeBridgeIds.JUMP` + `PlayerAttributes.refreshBridged` 把 `jump` 桥到 `minecraft:jump_strength`（ADD_VALUE，幂等；块→强度用 `AttributeMath.jumpStrengthDelta`）
- [x] 2.2 新增 `event/CultivationGiftEvents`：玩家受非弹幕伤害 `× (1 − clamp01(phys_resist))`；豁免 `GENERIC_KILL`/`FELL_OUT_OF_WORLD`
- [x] 2.3 同处理器：玩家 `player_attack` 近战时 `amount += melee_damage`（先加附加、后乘物抗）
- [x] 2.4 弹幕伤害不进入物抗通道（`DamageEventHandler` 与护壁指数互不干扰）

## 3. 测试

- [x] 3.1 `AttributeKeyRegistryTest` 断言 15 → 18
- [x] 3.2 单测：跳跃块→jump_strength 换算单调、+3 格落在期望强度附近（`AttributeMathTest`）
- [ ] 3.3 单测：物抗钳制/近战加算——（依赖 ServerPlayer/事件，留实机；纯钳制由 `clampPercent`/`finalFrom` 既有覆盖）
- [ ] 3.4 实机：`/gs_test player 5` 观察跳跃更高、非弹幕受伤减少、近战增伤

## 4. 文档

- [x] 4.1 `docs/balance-test-harness.md` 与 proposal/design/spec 已登记三键语义与豁免规则

## 实现备注（偏差）

- **上限语义**：三键的硬上限等于"T5 满 roll 值"（3.0 / 0.85 / 100），中点低于上限（用户明确物抗"最大值 85% 而非中点"）。
- **旧档新增键**：已在新表入账的旧档其 `grace_tier_N` 不含三键，需洗练或 `/gs_test player` 重新入账才会出现；旧表档登录迁移会自动带上。
- **`phys_resist` 含摔落伤害**（与高跳对冲）；如不需要可后续加排除。
