# Proposal: 仪式系统规范化

## Why

阶段 A/B 的多方块校验是硬编码偏移数组（`MultiblockMatcher`），无法表达复杂结构与多级建造，
且发电机/淬炼/召唤三处各自为政。趁内容尚少，建立数据驱动的标准仪式框架。

## What Changes

- **声明式结构定义**：`data/gensokyou/rituals/*.json`——字符画分层切片 + palette 谓词（精确方块/#标签/air/_ignore）+ 锚点键
- **多级结构**：单文件内 levels 数组，自顶向下增量匹配，返回最高可达等级
- **朝向无关**：匹配器自动尝试 4 旋转 × 镜像共 8 种变换
- **键位坐标输出**：匹配结果携带每个 palette 字符的世界坐标（如全部祭品台位置），供仪式逻辑消费
- **采集命令**（开发用）：`/gs_ritual_capture <名称> <半径> <高度>` 扫描现实搭建的结构自动生成 JSON 骨架写入日志
- **祭品台方块**：BE 存 ItemStack + BER 悬浮渲染（上下浮动+自转）；空手右键取回、持物右键放入
- **迁移**：召唤环改写为首份 `summon_circle.json`；发电机/淬炼/催化剂切换 `RitualMatcher`；删除 `MultiblockMatcher`

## Capabilities

### New Capabilities

- `ritual-pattern-system`: 结构定义格式、多级识别算法、朝向枚举、键位坐标输出、采集命令
- `ritual-pedestal`: 祭品台方块的存取交互与悬浮渲染

### Modified Capabilities

- `flandre-boss-low-tier`：召唤仪式校验由 MultiblockMatcher 改为 RitualPattern 匹配（行为不变）

## Impact

- 新包 `com.bitsson.gensokyou.ritual`（pattern/loader/matcher/command）
- 删除 `block/ritual/MultiblockMatcher`
- 三处调用点迁移 + 新增祭品台方块与其资产/语言条目
