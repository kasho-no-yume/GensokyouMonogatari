# Design: 仪式石形态变种 + 激光渲染层级修复

## Context

- 仪式石现状：`ModBlocks` 循环注册 `ritual_stone_0..5`（`TieredBlock`）与 `ritual_pedestal_0..5`，每级独立注册名、独立 blockstate/model；物品侧 `TieredBlockItem` 持有 tier 做染名；创造标签 `forEach` 收录。
- 仪式匹配链路：`RitualMatcher.verifyTier` 按图案 JSON 谓词逐槽位校验，**之后**才对槽位内方块调 `ModBlocks.tierOf` 统计 maxTier。谓词是数据侧定义的（只认满方块仪式石/祭品台），所以装饰性变种天然进不了图案槽位，`tierOf` 无需感知变种。
- 激光渲染链路（反编译 1.21.1 `LevelRenderer.renderLevel` 实证）：
  - 实体缓冲在 L1167~1180 全部冲刷（`endLastBatch` + 各 sheet + `endBatch()`）；
  - `RenderType.translucent()` 地形段（水）在 L1184/L1203 **之后**绘制；
  - 激光层走 `DanmakuRenderTypes.additiveGlow`：加法混合 + `COLOR_WRITE`（不写深度）。激光先画且不占深度 → 后画的水深度测试必然通过，alpha 混合覆盖激光 → "人→激光→水"视线序下水盖住激光。
  - 其他实体不中枪的原因：不透明/cutout 部分写深度，身后的水被深度剔除；激光是纯发光体，全层不写深度。

## Goals / Non-Goals

**Goals:**

- 6 品阶 × 台阶/楼梯/墙共 18 个装饰方块，贴图零新增，可合成/切石获得，进创造标签
- 激光在水前可见（主体正确遮挡身后的水），激光在水后/水下时保持今天的水面染色观感
- 弹幕投射物（球/刀/札）与外发光层的现有观感零变化

**Non-Goals:**

- 祭品台形态变种；变种参与仪式结构匹配；弹幕发光层策略调整
- fabulous graphics（`transparencyChain` 分支）下的逐像素正确合成（原版本就不完美，维持现状）

## Decisions

### D1 方块类：直接用原版方块类，不建子类/接口

`SlabBlock(Properties)` / `StairBlock(baseState, Properties)` / `WallBlock(Properties)` 直接注册；楼梯基态引用同品阶 `RITUAL_STONES.get(tier).get().defaultBlockState()`（方块注册先于物品，DeferredHolder.get() 安全）。tier 只存在于 `TieredBlockItem` 构造参数。
备选（否决）：`Tiered` 接口 + 三个变种子类——变种的运行时 tier 查询当前无任何消费方（YAGNI），且 `tierOf` 若按接口放宽会埋下仪式语义漂移的隐患。

### D2 注册属性与命名

- 注册名：`ritual_stone_slab_N` / `ritual_stone_stairs_N` / `ritual_stone_wall_N`（沿用后缀品阶惯例）
- 属性对齐仪式石本体：`.mapColor(MapColor.DIAMOND).strength(1.5F, 6F)`；墙按原版惯例补 `forceSolidOn()`
- lang：`block.gensokyou.ritual_stone_{slab,stairs,wall}_N` = 仪式石台阶/楼梯/墙（6 级共用文本，品阶色由 item 染名表达，与仪式石本体现状一致）

### D3 资产生成：python 脚本一次性产出

新增 `tools/gen_ritual_stone_variants.py`（python json.dump，幂等可重跑；禁止 PowerShell 拼 JSON——skill 已知陷阱）。产出清单（每级 15 文件 × 6 + 数据 54 = 144 JSON）：

```
blockstates/  slab(3 variant: bottom/top/double→复用 ritual_stone_N 满块模型)
              stairs(4×facing×half×shape)  wall(multipart: post + 4 向 low/tall/none)
models/block/ slab, slab_top, stairs, inner_stairs, outer_stairs,
              wall_post, wall_side, wall_side_tall, wall_inventory   ← 9 个，全 parent 原版模型
models/item/  slab/stairs/wall 各 1                                        ← 3 个
loot_table/blocks/  台阶: alternatives（half=double→set_count 2，否则 1）
                    楼梯/墙: survives_explosion 自掉                       ← 18 个
recipe/       合成: 台阶3→6 / 楼梯6→4 / 墙6→6；切石: 1→1                  ← 36 个
```

贴图统一引用现有 `gensokyou:block/ritual_stone_N`；slab 的 double 档直接 parent 到现有 `gensokyou:block/ritual_stone_N` 模型，不新增满块模型。

### D4 激光修复：深度写入版渲染类型（拆层）

`DanmakuRenderTypes` 新增 `additiveSolid(texture)`：与 `additiveGlow` 唯一差异为 `setWriteMaskState(COLOR_DEPTH_WRITE)` + `sortOnUpload(true)`，其余不变（NEW_ENTITY 格式、加法透明、NO_CULL、自发光 shader、lightmap）。

渲染器拆层：

```
外发光 (α70,  1.9×半径) ──▶ additiveGlow(BEAM)   不写深度（保持现状）
主体   (α235, 1.0×半径) ──▶ additiveSolid(BEAM)  写深度 + 上传排序
亮核   (α255, 0.45×半径)──▶ additiveSolid(BEAM)  写深度
端盖   (α235)           ──▶ additiveSolid(CAP)   写深度
指示线 (α60~190)        ──▶ additiveSolid(BEAM)  写深度
```

正确性论证：
- **激光在水前**：主体/亮核写深度 → 后画的水深度剔除 → 光束正确遮挡水面（水面在光束后被挡住是物理正确的，非 artifact）
- **激光在水下/水后**：主体先画（实体期），水面后画深度通过 → 照常染色，与今天一致
- **自遮挡**：6 个 cross 平面经 `sortOnUpload` 远→近排序后逐个通过 LEQUAL 并写深度，加法混合与顺序无关 → 视觉与今天一致；亮核与主体共面等深，LEQUAL 放行
- **vs 地形**：深度测试不变（LEQUAL 对地形），穿墙遮蔽照旧

备选（否决）：
- `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS` 延迟重绘（已确认 NeoForge 21.1.248 存在该 Stage 且 dispatch 点接在 L1203 之后）——能修水前遮挡，但半透明地形写深度，水下激光会被水面深度整体剔除直接消失（雾之湖弹幕战倒退），且需手动迭代实体 + 防双渲染，架构成本高
- 全层（含外发光）写深度——会在水上凿出 1.9× 的光晕形大洞，过激

### D5 外发光拆出独立 buffer

现状 `renderActiveBeam` 把外发光/主体/亮核写进同一个 `glowRenderType()` consumer；拆层后外发光单独取 `additiveGlow` buffer，主体+亮核取 `additiveSolid` buffer，端盖/指示线换 `additiveSolid`。`AbstractDanmakuRenderer.glowRenderType()` 基类不改，激光渲染器覆写返回 `additiveSolid`，外发光处显式取 `additiveGlow`。

## Risks / Trade-offs

- [immediate BufferSource 对 sortOnUpload 的四边形排序生效性存疑（原版主要在 section 固定缓冲用）] → runClient 实机验证；最坏情况 cross 平面部分自剔除（光束某些角度变细），fallback 为接受或降 BEAM_PLANES
- [外发光层在水前仍被水轻微盖色（α70 极淡）] → 可接受的折中，换取不在水面凿光晕形大洞
- [fabulous 模式下行为与今天一致（未修）] → 极少使用，记录为已知限制
- [144 个生成 JSON 的正确性] → 脚本幂等 + runServer 日志检查 "missing model for variant"/配方加载错误；blockstate 生成用 python 规避引号陷阱（skill 已知项）

## Migration Plan

纯增量：新方块注册名全新、渲染改动客户端本地。回滚 = revert。无存档/网络协议迁移。

## Open Questions

- lang 显示名：默认「仪式石台阶/楼梯/墙」与本体前缀一致；若想要「祭坛台阶」等叫法改 3 个键值即可（不影响实现）
