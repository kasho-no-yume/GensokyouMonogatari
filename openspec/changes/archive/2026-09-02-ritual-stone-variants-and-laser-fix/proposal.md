# Proposal: 仪式石形态变种 + 激光渲染层级修复

> 本提案合并两项体验优化：仪式石的建筑表达力扩充（任务1）与激光被水面错误遮盖的渲染缺陷（任务2）。
> 两项互不依赖，合并为一个变更是因为均为小体量、无交叉代码面的打磨项。

## Why

仪式石目前只有满方块一种形态，玩家搭建祭坛建筑时无法做出台阶、立柱、檐口等细节，6 个品阶的建筑表现力被浪费；同时激光弹幕作为本 mod 的核心战斗视觉，其加法混合发光层不写深度，被后渲染的半透明方块（水面）整体覆盖——站在水边向湖面开火时激光"消失"，雾之湖场景下战斗反馈严重劣化。

## What Changes

- **仪式石形态变种**：新增 3 族 × 6 品阶共 18 个方块——`ritual_stone_slab_0..5`（台阶）、`ritual_stone_stairs_0..5`（楼梯）、`ritual_stone_wall_0..5`（墙），复用现有 `ritual_stone_N` 贴图，全部为纯装饰方块（不参与仪式结构匹配）
- **零新方块类**：直接注册原版 `SlabBlock`/`StairBlock`/`WallBlock`（楼梯基态引用同品阶 `ritual_stone_N.defaultBlockState()`），不建子类、不改 `ModBlocks.tierOf()`——仪式结构匹配语义完全不动（`RitualMatcher` 的 maxTier 统计只作用于图案槽位内的方块，变种不参与谓词）
- **物品与创造标签**：18 个方块各配 `TieredBlockItem`（沿用品阶染名），收进 `gensokyou` 创造标签
- **数据文件**：blockstate / block+item model / loot table（含台阶半砖掉落逻辑）/ 合成配方（台阶3→6、楼梯6→4、墙6→6）+ 切石配方（1→1），由脚本批量生成（python json.dump，禁 PowerShell 拼 JSON）
- **激光渲染修复**：新增「深度写入 + 上传排序」的加法混合渲染类型（`additiveSolid`），激光的主体/亮核/端盖/延迟指示线全部换用，外发光层保持现状；弹幕投射物（球/刀/札）渲染类型不动

## Capabilities

### New Capabilities

- `ritual-stone-shapes`：仪式石形态变种族（台阶/楼梯/墙 × 品阶 0-5）的注册、获取（合成/切石/创造标签）、掉落与装饰定位

### Modified Capabilities

- `danmaku-laser`：新增「激光相对半透明方块的渲染层级」需求——激光主体在半透明方块（水）之后绘制且写入深度，保证"人→激光→水"视线序下激光可见

## Impact

- **Java**：`registry/ModBlocks.java`（循环注册三族变种，直接用原版方块类）、`registry/ModItems.java`、`registry/ModCreativeTabs.java`、`client/renderer/DanmakuRenderTypes.java`（新增渲染类型）、`client/renderer/LaserDanmakuRenderer.java`（层拆分换用）
- **数据/资产**：18 blockstate + 72 model + 18 loot_table + 36 recipe JSON（脚本生成）；`lang` 文件补 36 个条目（方块名）
- **无破坏性**：不改动既有方块的注册名、行为与仪式匹配语义；激光视觉结构（四层）与伤害逻辑不变
- **风险点**：immediate BufferSource 对 `sortOnUpload` 的排序生效性需 runClient 实机验证（见 design.md）

## Out of Scope

- 祭品台（ritual_pedestal）的形态变种
- 变种方块参与仪式结构匹配（保持 tierOf 对仪式匹配的既有语义）
- 弹幕投射物发光层的渲染策略调整
