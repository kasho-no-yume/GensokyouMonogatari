## 1. 方块与物品注册（Java）

- [x] 1.1 `ModBlocks` 循环注册三族变种：`ritual_stone_slab_N`（`SlabBlock`）、`ritual_stone_stairs_N`（`StairBlock`，基态取同品阶 `RITUAL_STONES.get(tier).get().defaultBlockState()`）、`ritual_stone_wall_N`（`WallBlock`，补 `forceSolidOn()`）；属性对齐 `.mapColor(MapColor.DIAMOND).strength(1.5F, 6F)`；`tierOf()` 不动
- [x] 1.2 `ModItems` 循环注册 18 个 `TieredBlockItem`（沿用染名机制）
- [x] 1.3 `ModCreativeTabs` 在仪式石之后按 台阶→楼梯→墙 分组收录 18 个物品

## 2. 资产生成脚本（tools/gen_ritual_stone_variants.py）

- [x] 2.1 编写脚本（python json.dump、幂等）：blockstates——slab（bottom/top/double，double 复用 `gensokyou:block/ritual_stone_N` 模型）、stairs（facing×half×shape 全变体 + uvlock）、wall（multipart post + 4 向 low/tall/none）
- [x] 2.2 block models 每级 9 个（slab/slab_top/stairs/inner_stairs/outer_stairs/wall_post/wall_side/wall_side_tall/wall_inventory，全部 parent 原版模型、textures 指向 `gensokyou:block/ritual_stone_N`）
- [x] 2.3 item models 每级 3 个
- [x] 2.4 loot_table 每级 3 个：台阶用 alternatives（half=double → set_count 2，否则 1）；楼梯/墙 survives_explosion 自掉
- [x] 2.5 recipe 36 个：合成（台阶 3→6、楼梯 6→4、墙 6→6）+ 切石（1→1），输入为同品阶 `gensokyou:ritual_stone_N`
- [x] 2.6 运行脚本产出 144 个 JSON，抽查 wall multipart 与 slab loot 各 1 份

## 3. 语言文件

- [x] 3.1 `zh_cn.json` 补 18 键（仪式石台阶/仪式石楼梯/仪式石墙 ×6 品阶共用文本），`en_us.json` 同步补齐

## 4. 激光渲染修复

- [x] 4.1 `DanmakuRenderTypes` 新增 `additiveSolid(texture)`：同 `additiveGlow` 但 `setWriteMaskState(COLOR_DEPTH_WRITE)` + `sortOnUpload(true)`，同 texture 缓存
- [x] 4.2 `LaserDanmakuRenderer` 拆层：覆写 `glowRenderType()` 返回 `additiveSolid(BEAM)`；外发光层显式改取 `additiveGlow(BEAM)`；端盖换 `additiveSolid(CAP)`；指示线随 `glowRenderType()` 自动走 additiveSolid

## 5. 验证

- [x] 5.1 `gradlew.bat runServer`：latest.log 出现 `Done (`，无 `Errors in registry`、无 missing model for variant、配方/战利品表零报错
- [x] 5.2 `runClient`：创造标签取全 18 物品；台阶半砖/双砖、楼梯转角、墙连接行为正确且贴图与同品阶仪式石一致
- [x] 5.3 `runClient` 水边实测：向湖面开火激光主体不被水覆盖；水下激光保持染色观感；球/刀/札弹幕与外发光观感无变化（重点观察 sortOnUpload 生效性，记录结论）
- [x] 5.4 仪式回归：既有仪式结构照常匹配成型，祭坛周围用变种装饰不影响仪式
