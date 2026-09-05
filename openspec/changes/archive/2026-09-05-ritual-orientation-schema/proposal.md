# Proposal: ritual-orientation-schema

## Why

现有仪式图案 schema v3 只校验"格位是什么方块"，放置一律用 `defaultBlockState()`——有朝向的方块（楼梯、旗帜、原木等）无法参与仪式：匹配不区分朝向、搭建摆不出正确朝向。这是构建杖实地投影（后续变更 ritual-builder-preview）的前置：幽灵要渲染"放置完成后的正确样子"，必须先有"格位 → 目标 BlockState（含朝向）"的权威解析。

## What Changes

- **BREAKING（仅图案文件格式）**：方块条目从对象改为**位置式数组** `["key",x,y,z,o?]`，每条目一行紧凑书写；仓库内 7 个既有图案 JSON 机械迁移（脚本重排，语义零变化）。稀疏格式下多级仪式文件的朝向条目可达数百条，对象式书写的缩进与键名冗余在 AI 辅助编写场景是实打实的 token 成本。
- 条目新增**可选**第 5 位 `o`（schema v3 → v4）：朝向常量，缺省 = 完全维持现状行为。
- 朝向为**单一扁平枚举（26 常量）**，数字直存、字符串名双解析（`5` 与 `"north_top"` 等价）：
  - `1-4` NORTH/EAST/SOUTH/WEST → `HORIZONTAL_FACING`（无则 `FACING`）
  - `5-8` `*_TOP` → 上述 + `HALF=top`（楼梯上半）
  - `9-10` UP/DOWN → `FACING` 垂直分量
  - `11-26` R0-R15 → `HORIZONTAL_ROTATION`（罗盘约定 R0=北顺时针，vanilla=(r+8)%16）
  每个常量自带属性需求谓词，EXACT 加载期校验、TAG 运行期校验；墙类连接态方块不属于任何常量，声明即拒载。
- 四分之一展开时朝向随位置同步变换（全局仅 rot90/mirrorX 两张 26 元置换表，mirrorZ 复合即得）——"朝向满足中心对称"由构造保证。
- `RitualMatcher` 校验：条目带朝向时额外比对世界方块朝向属性（不支持该常量即不匹配）；4 旋转尝试时期望常量随 rot90^r 变换。
- `RitualBuilderPlacement` 搭建：抽出共用的"格位 → 目标 BlockState"解析（`resolveState`，种类 + 品阶 + 朝向一次到位），放置带朝向。
- `RitualCapture` 采集：按方块属性反推常量并在骨架中输出 `o` 位。

## Capabilities

### New Capabilities

（无——朝向是既有能力域的字段扩展）

### Modified Capabilities

- `ritual-pattern-system`: 声明式结构定义条目格式改为位置式数组并新增可选朝向常量；展开规则扩展为"位置 + 朝向"同步变换；加载期校验新增朝向相关拒载项；匹配器朝向校验；采集命令输出朝向。
- `ritual-builder-placement`: 标签/精确谓词实例化之外，新增"条目带朝向时放置须应用朝向属性"；无朝向条目行为不变。

## Impact

- **代码**：`ritual/RitualPattern.java`（BlockEntry 加 `@Nullable Integer orientation`）、`ritual/RitualPatternLoader.java`（数组解析/校验/展开）、`ritual/RitualMatcher.java`（verifyTier）、`ritual/RitualBuilderPlacement.java`（resolveState 抽取）、`ritual/RitualCapture.java`（输出）；新增 `ritual/Orientation.java`（26 常量 + 变换表 + apply/extract，纯逻辑可独立单测）。
- **数据**：`data/gensokyou/rituals/*.json` 7 文件机械迁移为数组格式（语义不变，迁移前后展开表逐字节对照验证）。
- **依赖变更**：无朝向的图案匹配/搭建/采集行为逐位不变（回归面 = 现有 7 仪式）；loader 不再接受对象式条目（外部图案尚不存在，无破约对象）。
- **下游**：ritual-builder-preview 变更消费本变更加抽出的 `resolveState` 抽象。
