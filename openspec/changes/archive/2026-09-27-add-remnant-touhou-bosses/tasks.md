## 0. 当前进度（切片 A + 鬼蛛）

已交付并通过 `gradlew build`（321 测试）/ `lang_audit` / `validate_ritual_pattern` / `openspec validate --strict`：

```
弹幕基类四种行为 曲射 / 分裂 / 悬停 / 溜め     ✅ 编译通过，行为可配置
轨道编排模型 TargetMode / Shape / Geometry / Beat / Track / SpellCard / TrackRunner  ✅
签名色盘 + TrackLint（R1/R2/R3 + 缺段约束 + 视觉独占）                                ✅ 15 条单测
AbstractTouhouBoss 阶段 / 伤害除数 / 距离带游走 / ≤5 目标 / 咒符条 / 掉落              ✅
大妖精 3 符卡正式化（散華 / 旋風 / 落華）                                             ✅
鬼蛛「堅牢」缺「破」3 符卡 × 2 轨（経糸 / 結界 / 硬化）                              ✅
effect 注册表 + 百鬼夜行召唤落地钩子                                                   ✅
两条 L1 召唤配方（大妖精 / 鬼蛛）                                                       ✅
野外大妖精自然刷新增删                                                                 ✅
```

**尚未实现**：狐火 / 傩神楽面的**实体**（符卡表已写并过 lint，缺 entity + 注册 + 配方）；
L2 祭坛与另外两条配方（卡在 `ritual_stone_2`）；数值秒带改造；`star_silver` 移除。

## 1. 弹幕基类：四种新行为（D2 / D3）

- [x] 1.1 同步字段：flag 字节 + 悬停 tick + 分裂 tick/发数 + 曲射轴（2×16 位打包）+ 角速度 + 溜め半径
- [x] 1.2 曲射：绕轴罗德里格旋转 `deltaMovement`，两端同规则
- [x] 1.3 悬停：到达 tick 速度归零；**定住后改走 AABB 接触判伤**（零向量下 moveVector 恒为 MISS）
- [x] 1.4 分裂：到达时刻散成 N 发并清除母弹；`SphereDanmaku.spawnSplitChildren` 生成环形子弹幕
- [x] 1.5 溜め：完全静止、待命 20 tick、玩家进入半径触发；踩到且声明了分裂则炸成一圈
- [x] 1.6 全部经 `configureXxx` 注入，无硬编码
- [ ] 1.7 双端运动学一致性单测（需实体环境，暂缺）
- [ ] 1.8 悬停弹定住后位置恒定单测（需实体环境，暂缺）
- [ ] 1.9 悬停弹仍能造成伤害单测（需实体环境，暂缺）

## 2. 轨道编排模型（D1）

- [x] 2.1 `TargetMode`（AIMED / SELF_AXIS / ARENA）+ 复制语义
- [x] 2.2 `Shape` 枚举（13 种三维合法母题）+ `Params` 不可变参数 + 链式构造
- [x] 2.3 `Geometry`：形状 → 发射指令的三维几何（局部基向量展开，缺口对齐玩家方位）
- [x] 2.4 `Track` / `Track.Beat` / `SpellCard` / builder
- [x] 2.5 `TrackRunner`：并发推进 + 多目标分发 + 缺口相位对齐
- [x] 2.6 单测：无世界无实体条件下构造并断言轨道结构
- [x] 2.7 单测：轨道数恒在 1~3

## 3. 可读性契约与视觉独占（D7 / R1-R3）

- [x] 3.1 `SignaturePalette`：容量断言，色即轨道身份证
- [x] 3.2 `TrackLint`：R1 前向威胁 / R1 随机受约束 / R2 解法全向（密度判据）/ R3 层限 / 参数合法性
- [x] 3.3 轨道视觉独占校验（同符卡内 identity 不得全同）
- [x] 3.4 单测：4 条 lint 规则的正反用例
- [x] 3.5 config：弹幕实体数上限（500）、R1 偏角、R2 封死阈值
- [x] 3.6 `DanmakuBudget`：进出世界计数 + 达上限停发（停发不删旧弹）+ 低频对账
- [x] 3.7 单测：R3 / 全向随机 / 色盘不足 / 轨道撞色 全部可检出

## 4. BOSS 基类（D4 / D5 / D6）

- [x] 4.1 `AbstractTouhouBoss` 实现 `TouhouBoss`（隔壁的 common 侧空接口）→ 咒符条白捡
- [x] 4.2 符卡阶段按血量阈值切换；换卡不重算生命
- [x] 4.3 生命钳 1024 + 伤害除数（`damageScale()`），血条比例随实际伤害同步
- [x] 4.4 距离带 + 自由游走：带外趋近/退开，带内游走；垂直分量独立；选点失败退化为横移不卡死
- [x] 4.5 距离带 / 移速 / 垂直偏好 / 游走间隔均可覆写
- [x] 4.6 目标选取：距 BOSS 最近 ≤5 名存活玩家
- [x] 4.7 按 `TargetMode` 分发：AIMED 每目标一份；SELF_AXIS / ARENA 只一份
- [x] 4.8 血条对全部目标可见
- [x] 4.9 单测：复制语义与档位

## 5. 大妖精 + 鬼蛛（D11 / D12）

- [x] 5.1 鬼蛛 `KuzumonoEntity` 注册 + 属性 supplier
- [x] 5.2 `RemnantBossRenderer` 注册点：按实体 id 解析渲染器，实体类内不持渲染实现
- [x] 5.3 鬼蛛缺「破」→ 全表无 `AIMED` 轨道（`TrackLint.hasNoAimedTrack` 断言）
- [x] 5.4 鬼蛛三张符卡：経糸（贴地滑行带 ‖ 悬停网）/ 結界（收缩壳 ‖ 溜め散布）/ 硬化（交差笼 ‖ 缺口补位分裂）
- [x] 5.5 鬼蛛签名色盘 4 色
- [x] 5.6 大妖精改造：脱离 `FairyEntity` 继承链，保留自有贴图/渲染，接 3 张符卡
- [x] 5.7 琪露诺回退继承 `FairyEntity`（她仍是占位精英，不挂进正式 BOSS 继承链）
- [x] 5.8 教学档约束：全大慢弹、生成点在玩家前向、无背向生成
- [ ] 5.9 残影实体包围盒放大（避免视锥剔除）—— 随正式模型一并做
- [ ] 5.10 狐火实体 + 傩神楽面实体（符卡表已写并过 lint）

## 6. 数值（D8）

- [x] 6.1 每只 BOSS 的秒数与挨弹数配置项（大妖精 60/10、鬼蛛 75/8）
- [x] 6.2 `MonsterStatBudget` 接线：生命 = DPS × 秒、弹伤 = EHP ÷ 挨弹
- [x] 6.3 spawn roll 接入 `MONSTER_SPAWN_ROLL`（±25%，随 NBT 持久化）
- [ ] 6.4 秒带表 T1~T5 落地（`bossSeconds` 倍带 → 秒带）
- [ ] 6.5 `referencePlayerEhp` 应用 `ATTR_HEALTH_BONUS_CAP`（修 T4/T5 高估）
- [ ] 6.6 狐火 / 傩神楽面 秒数与挨弹配置

## 7. 召唤挂钩（D9）

- [x] 7.1 `effect` id → 实体工厂注册表，未注册 id 明确报错
- [x] 7.2 声明 `hyakki:big_fairy` / `hyakki:kuzumono`
- [x] 7.3 生成位置：仪式核心正上方，与降临光柱落点一致；在 BURST→PILLAR 转相那一帧调用
- [x] 7.4 野生语义：祭坛关闭/拆除不触碰已降临实体
- [x] 7.5 掉落：四只全部碎符卡星；鬼蛛额外保底掉隙间碎片
- [x] 7.6 **未改动** `HyakkiYagyoBehavior` 的会话/充能/容量/演出
- [ ] 7.7 单测：未注册 effect id 抛错

## 8. 配方与存量数据

- [x] 8.1 `hyakki_yagyo_circle.json` 换成两条真配方（大妖精 / 鬼蛛），移除全死的 T2 探针
- [x] 8.2 野外大妖精移出 `biome_modifier`（正式化为召唤 BOSS）
- [x] 8.3 `bosses.json` 登记鬼蛛
- [x] 8.4 lang：`entity.gensokyou.kuzumono` + 4 个 JEI 配方/效果键，`lang_audit` 退出码 0
- [ ] 8.5 从 `zaohua_danmaku_weapon_frame` 移除 `star_silver`
- [ ] 8.6 入口级武器框配方 MUST NOT 含 T2 及以上材料（防回归测试）
- [ ] 8.7 狐火 / 傩神楽面 两条 L2 配方（卡在 `ritual_stone_2` 可达性）

## 9. 构建与验证

- [x] 9.1 `gradlew build` 通过（321 测试）
- [x] 9.2 `openspec validate add-remnant-touhou-bosses --strict` 通过
- [x] 9.3 `lang_audit.py` 退出码 0
- [x] 9.4 `validate_ritual_pattern.py --test-out` 全部 pattern 通过
- [ ] 9.5 存档重载：阶段 / 游走状态 / HP 除数保持
- [ ] 9.6 实机：百鬼夜行召出大妖精与鬼蛛，咒符条血条、自由游走、符卡切换
- [ ] 9.7 实机：鬼蛛全程不主动攻击（缺「破」的行为验证）
- [ ] 9.8 实机：悬停弹定住后仍能被打中；溜め踩到会炸成环

## 10. 对接项（本变更不做）

- [ ] 10.1 占位值 `hyakki:balance_test_boss` 已随探针配方一并移除，无需再改
- [ ] 10.2 玩家神恩门槛：`minPlayerTier` 从 `YaoyorozuGraceService` 私有校验提升为通用闸门
- [ ] 10.3 符卡名在血条上的显示：需改隔壁 `TouhouBossBarRenderer`（它当前不绘制原版名文本）
- [ ] 10.4 祭坛阶数可达性与 `ritual_stone_2/3` 配方（`add-ritual-stone-higher-tier-recipes`）
