# Design: ritual-system-normalization

## Context

现有校验为 `MultiblockMatcher.isValidSummonRitual`（核心+同层 8 仪式石硬编码）。仪式将向复杂/多级演进，需要标准结构格式与通用匹配器。

## Goals / Non-Goals

**Goals:** 数据驱动结构定义；多级识别；朝向无关；键位坐标输出；游戏内采集工具；祭品台。
**Non-Goals:** 仪式激活后的特效编排（后续变更）；结构渐变动画。

## Decisions

### D1 结构定义 JSON（data/gensokyou/rituals/*.json）

```json
{
  "id": "summon_circle",
  "anchorKey": "C",
  "palette": {
    "C": "gensokyou:ritual_core",
    "S": "#gensokyou:ritual_stones",
    "-": "minecraft:air",
    ".": "_ignore"
  },
  "levels": [
    { "level": 1, "slices": [["-----"], ["-SSS-"], ["-SCS-"], ["-SSS-"], ["-----"]] }
  ]
}
```

- slices[y] = String[]（每元素一行 z 行），字符= x 列；y 自低向高
- palette 谓词三种：`block:id` 精确 / `#ns:path` 标签 / `_ignore` 不检查 / `minecraft:air` 强制为空
- 锚点 = anchorKey 字符在切片中的首个位置；世界锚点即玩家交互的方块

### D2 匹配算法

```
matchAt(level, anchorPos):
  for pattern in all():                    ← 重载监听器持有的注册表
    for li = levels.size-1 .. 0:           ← 自顶向下
      for orient in [rot0,90,180,270]×[原,镜像X]:   ← 8 变换
        if verifySlices(li, orient): return Match(pattern.id, level, keyedPositions)
  empty
```

verifySlices：对每个非 _ignore 字符格，按变换映射到世界坐标并断言谓词；同时收集
`Map<Character, List<BlockPos>>`。v1 不做跨 tick 缓存（结构小、调用频率低）。

### D3 采集命令

`/gs_ritual_capture <name> <radius 1..16> <height 1..16>`（权限 ≥2）：
以玩家脚下为锚点扫描区域，真实方块→自动分配字符键（A,B,C…，air→`-`），
组装 JSON 打印至日志并回显提示。产出为骨架，人工把键抽象成标签后入库。
命令类挂 RegisterCommandsEvent（game bus）。

### D4 祭品台

- `RitualPedestalBlock extends Block implements EntityBlock`
- `RitualPedestalBlockEntity`：持 ItemStack 单槽（单个物品，非整组）+ NBT 持久化 + 客户端同步（getUpdateTag/getUpdatePacket）
- 交互：右键放入**单个**物品（生存扣 1，创造模式放复制体不扣）；空手或潜行右键取回；拆台时祭品一并掉落
- 注意：`useItemOn` 必须将真身 stack 引用传入处理逻辑（不得 copy），否则 split 不作用于玩家手上
- `client/RitualPedestalRenderer implements BlockEntityRenderer`：
  translate(0.5, 1.2+bob, 0.5) + Y 轴慢速自转 + `ItemRenderer.renderStatic(GROUND)`
  光照取 `LevelRenderer.getLightColor(level, pos)`
- 注册：`EntityRenderersEvent.RegisterRenderers.registerBlockRenderer`（NeoForge 扩展）

### D5 迁移

三处调用点改为 `RitualMatcher.matchAt(level,pos).isPresent()`；
`summon_circle.json` 与旧硬编码等价；删除 MultiblockMatcher。

## Risks / Trade-offs

- [采集输出含大量杂色方块需人工抽象] → 骨架仅是起点，palette 抽象化是设计环节本身
- [无缓存的高频匹配] → 仅交互触发时匹配，单次 ≤ 数百次方块查询，可接受；必要时再加短缓存
- [镜像导致左右手性仪式混淆] → v1 全枚举接受；未来可在 pattern 加 `"mirror": false` 开关

## Open Questions

- 多级仪式的具体内容（二级召唤环长什么样）待阶段 C 设计幻想乡内容时一并定
