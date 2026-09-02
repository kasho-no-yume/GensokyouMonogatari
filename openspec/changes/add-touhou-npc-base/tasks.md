## 1. NPC 基类实体

- [ ] 1.1 `entity/TouhouNpcEntity.java`：`extends PathfinderMob`，无移动 AI（仅 LookAt/RandomLook）、`isPushable=false`、`removeWhenFarAway=false`、不可拴绳、CREATURE 类目属性构建（`createAttributes`）
- [ ] 1.2 免伤与死亡拦截：`hurt()` 按 design D1 三分支（玩家来源 / 虚空·generic_kill / 其余拒绝），`abnormalDeath()`：紫色粒子（WITCH + DustParticleOptions，数量走 config）→ `discard()` → 偏移安全点刷新复制体（`saveWithoutMetadata`→`load` 语义复制）
- [ ] 1.3 击杀计数附件：`NpcOffenseData(int count)` record + Codec，注册进 `ModAttachments`（`copyOnDeath`）
- [ ] 1.4 归因与三振：直接 Player / 弹射物 getOwner 归因；达 `GensokyouConfig.NPC_KICK_THRESHOLD`（默认 3）时在 `gensokyou:gensokyo` 维度传送主世界共享出生点 + 播报 + 清零，不在则仅清零；1/2 次发 actionbar 警告
- [ ] 1.5 `GensokyouConfig` 新增：NPC_KICK_THRESHOLD、粒子数量、复制偏移半径

## 2. 交易接入

- [ ] 2.1 基类暴露 `Merchant` 实现钩子（offers 供给 + `openTradingScreen` 调用 + `setTradingPlayer` 生命周期），`mobInteract` 分发：交易型 / 对话型 / 无

## 3. 对话系统

- [ ] 3.1 `dialogue/`：`DialogueGraph`/`DialogueNode`/`DialogueOption` record + Codec（`next` 空=结束；action 枚举 OPEN_TRADE/CLOSE）
- [ ] 3.2 服务端会话管理（per-player Session，开启/推进/失效）+ `network/DialogSyncPayload`(S2C) 与 `DialogActionPayload`(C2S) 注册进 `ModNetworking`，C2S 校验会话与索引合法性
- [ ] 3.3 `client/screen/DialogScreen.java`：文本换行分页 + 滚动、选项按钮、ESC 关闭
- [ ] 3.4 动作执行：OPEN_TRADE → 销会话并 `startTrading`；对话关闭/远离 NPC 会话失效

## 4. 占位角色：森近霖之助（rinnosuke）

- [ ] 4.1 `ModEntityTypes` 注册 `rinnosuke`（0.6×1.9），`GensokyouClient` 注册 `SkinMobRenderer` 换肤，皮肤占位贴图入 `textures/entity/`（记占位清单）
- [ ] 4.2 示例交易表（静态 offers）+ 示例对话图（覆盖长文本分页、选项分支、OPEN_TRADE 衔接各至少 1 处）
- [ ] 4.3 lang 条目（实体名/对话文本翻译键，zh_cn + en_us）

## 5. 验证

- [ ] 5.1 `gradlew.bat runServer`：latest.log `Done (`，无 `Errors in registry`（附件/payload/实体注册全过）
- [ ] 5.2 `runClient` 免伤回归：怪物/岩浆/摔落/骷髅箭对 NPC 全部无效；`/kill` 触发流程但不计数
- [ ] 5.3 `runClient` 死亡闭环：创造模式攻击 → 紫色粒子 + 瞬间复制体 + 无掉落；连续 3 次后玩家被传回主世界出生点、计数清零、有警告与播报
- [ ] 5.4 `runClient` 交互闭环：右键霖之助 → 对话（翻页/滚动/选项/伪造包拒绝）→ 对话内转交易 → 交易买卖正常；远离 NPC 会话失效
- [ ] 5.5 存档回归：NPC 区块卸载重载仍在原位；计数跨死亡/重登保持
