# Design: phase-c-gensokyo-gate

## Context

阶段 B 后灵力经济闭环可用。本阶段打开幻想乡：结界引爆仪式 → 隙间传送门 → 数据驱动维度（孤岛式固定群系布局）→ 学卡/技能槽/冷却 HUD。商人 NPC 与委托任务雏形顺延至下次迭代。

## Goals / Non-Goals

**Goals:** 隙间传送门完整生命周期；幻想乡维度（5 群系各出现一次+三途川外围）；学卡数据与技能槽 HUD（含冷却显示）可玩。
**Non-Goals:** 商人 NPC、委托任务、主线剧情、隙间门的正式美术、地形精调（山体超高等密度函数优化后置）。

## Decisions

### D1 结界引爆与隙间门
- 新 pattern `barrier_break_circle`（结构待定形，v1 复用外环+四正位祭品台变体以区分）；
  行为 `BarrierBreakBehavior`：右键核心尝试激活——从玩家+相邻电容扣 `barrierSpCost`，
  成功后在核心上方放置 `sukima_block`。
- **一次性开关**：激活标志持久化在核心 BE；此后结构有效即自动维持隙间方块（免费重建），
  结构破坏（activeMatch 失效）→ 移除隙间方块；重建结构 → 免费重新出现。
- 隙间方块：`entityInside` 触碰传送——玩家已在幻想乡则回主世界出生点，否则进幻想乡 (0,地表,0)；
  复用实体 portalCooldown(40t) 防连触。占位贴图黑曜石拷贝。方块不可摧毁（-1 耐久）。

### D2 幻想乡维度 = overworld 噪声 + 自定义群系源
用户明示"参考主世界生成"→ 直接复用 `minecraft:overworld` noise settings（海平面/表面规则全继承），
仅替换 biome_source 为自定义 **`GensokyoBiomeSource`**（代码注册 MapCodec 到 BuiltInRegistries.BIOME_SOURCE）：
- 区位函数（群系坐标 4×4 格）：半径 > islandRadius(48 格=192m) → 三途川；
  岛内按方位角扇区划分四地：守矢山 108°、迷雾竹林/雾之湖/魔法森林各 84°，
  每群系恰好一块 ✓
- 维度 JSON：dimension_type 对齐主世界参数；generator 引用 overworld 设置 + 本 source
- 已知妥协：湖体凹陷与高山拔起依赖噪声随机性，专用密度函数塑形列为后续任务

### D3 五群系 JSON
均含 spawners（妖精生态延续）与主题化 effects（雾色/水色）。植被特征引用 vanilla placed features：
竹林=minecraft:bamboo 高频；雾之湖=dark_oak_checked 密植（大湖体后置）；三途川=沙/海草；
守矢山=spruce 疏林+裸岩斑块；魔法森林=jungle 树+huge_red/brown_mushroom+小蘑菇地面被。

### D4 技能槽与学卡
- `LearnedCardsData` Attachment（List<String>，copyOnDeath）：已学卡 id 列表
- 施放：3 个客户端键位（默认 G/H/J）→ C2S `CastSkillPayload(slot)` →
  服务端校验 已学/冷却/灵力 → 执行 `SpellCardEffects.perform(cardId, level, player)`
- 效果抽取：符卡效果从物品类抽到静态注册表（musou_fuuin/icicle_fall 先入池，light_reflect 占位），
  物品与技能共用同一执行器
- 冷却与耗蓝走配置 skills 节；HUD 三槽位于热键栏右上，绘制对应物品图标 + 冷却遮罩倒计时数字 +
  未学习置灰
- 过渡手段：`/gs_learn <card>`（权限≥2）直接写入学习表，委托任务接入后移除该入口

## Risks / Trade-offs

- [复用 overworld 噪声导致湖/山不达标] → 记录为已知妥协，后续以自定义 density function 迭代
- [BiomeSource 注册时机] → 主类构造期直接 Registry.register 内置注册表（合法窗口）
- [键位冲突] → 默认 G/H/J 可在控制设置改键

## Open Questions

- 隙间门的正式视觉（裂缝粒子面片）与音效
