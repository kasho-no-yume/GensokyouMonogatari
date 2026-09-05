# Design: ritual-orientation-schema

## Context

- schema v3 现状：`RitualPattern.BlockEntry(key,x,y,z)` 只带位置，条目为 JSON 对象；`RitualPatternLoader.expandInto` 按中心对称把稀疏四分之一展开为全量（off-axis 四象限镜像；轴上四方含坐标互换成套）；`RitualMatcher.verifyTier` 逐格 `Predicate.test(state)` 只判方块种类；`RitualBuilderPlacement` 放置一律 `block.defaultBlockState()`。
- 规模修正：一个图案文件承载该仪式**所有等级**的结构，多级大型仪式的朝向条目可达数百（展开后上千格）——条目书写密度是真实成本（AI 辅助编写读文件的 token 开销），故 v4 同步压缩条目格式。
- 本仓库已有带朝向方块可当试验田：`ritual_stone_stairs_0..5`（`StairsBlock`，`HORIZONTAL_FACING`+`HALF`+`SHAPE`）、`ritual_stone_wall_0..5`（`WallBlock`，连接态布尔属性，**无**单一朝向属性）。
- 术语防撞车：既有 spec 里"朝向枚举"一词已被占用（指匹配器的 4 种旋转）。本设计的格位朝向统一称 **orientation（朝向常量）**，旋转仍称 rotation。
- 约束（用户拍板）：orientation 为**可选**位，缺省 = 现状行为逐位不变；**不分 kind，单一扁平枚举 + 数字编码**；条目数组化；`FACING` 垂直向（UP/DOWN）纳入第一版。

## Goals / Non-Goals

**Goals:**
- 仪式格位可声明期望朝向，匹配/搭建/采集三条链路全部认账。
- 朝向随四分之一展开同步变换，"朝向满足中心对称"由构造保证，不靠作者自觉。
- 条目格式压缩：位置式数组 + 单字母字段语义，密集图案文件的书写/阅读成本降到最低。
- 抽出 `resolveState(pattern, entry, tier) → BlockState` 共用解析，为下游投影变更（ritual-builder-preview）铺路。
- 既有 7 图案语义零变化（仅格式机械迁移）。

**Non-Goals:**
- 不做墙类（连接态）朝向——`WallBlock` 无单一朝向属性，声明即拒载；墙进仪式仍可用（不带 `o` 位）。
- 不控制楼梯 `SHAPE`（直梯/内外角）：匹配只查朝向常量覆盖的属性；`SHAPE` 由原版邻接更新自动派生。
- 不做 `ROTATION`（装饰 45° 旋转类，如活板门斜转）——常量表可扩展，留待未来。
- 不改网络包、不改菜单/tooltip（材料统计仍按方块种类聚合，朝向不拆行）。
- 不做对象式旧格式兼容解析（外部图案不存在，单格式一条活路）。

## Decisions

### D1 orientation 挂在 BlockEntry，不挂 Predicate
同一字符（如一圈楼梯）每格朝向可以不同（都朝中心）；Predicate 是"是什么"，orientation 是"怎么摆"，正交。`record BlockEntry(char key, int x, int y, int z, @Nullable Integer orientation)`，orientation 为 26 常量表内 id。备选：kind+value 两段式——否决，kind 由方块属性唯一决定，逐条重复书写是纯冗余，且扁平枚举顺带消灭了"TAG 格 kind 加载期验不了"的不对称。

### D2 条目数组化 + 扁平 26 常量 + 双解析
条目格式：`["key",x,y,z,o?]`，每条目一行紧凑 JSON。第 5 位 `o`：int 常量 id，或等价字符串名（loader 判 `isNumber()` 分流，名不区分大小写）。

| id | 名 | 应用规则（探测顺序固定） |
|---|---|---|
| 1-4 | NORTH/EAST/SOUTH/WEST | `HORIZONTAL_FACING` → `FACING` → `AXIS`（原木类双向轴，见 D4） |
| 5-8 | `*_TOP` | 上述 facing 分量 + `HALF=top` |
| 9-10 | UP/DOWN | `FACING` 或 `AXIS`（垂直分量） |
| 11-26 | R0-R15 | `ROTATION_16`，罗盘约定 R0=北、顺时针递增 |

每常量自带属性需求谓词（校验/apply/matches 的单一事实源）。**1.21.1 实测修正**（sources jar）：`RotationSegment` 常量 N=0/E=4/S=8/W=12——ROTATION_16 本身就是罗盘序，R 系与 vanilla 值**恒等映射**（旧版"0=南"是 1.20.5 前约定，`(r+8)%16` 作废）；`HorizontalDirectionalBlock.FACING` 是 `BlockStateProperties.HORIZONTAL_FACING` 的别名（同一实例），楼梯/墙旗按对象身份探测安全。

### D3 变换表：全局两张 26 元置换表
对称展开只需两个本原操作：`mirrorX`（位置 x→-x）与 `rot90`（位置 (x,z)→(-z,x)，即俯视顺时针，与 `RitualMatcher.orient` case 1 同定义）；`mirrorZ = rot90∘rot90∘mirrorX`、坐标互换 `(x,z)→(z,x) = rot90∘mirrorX`。常量表上的置换：
- rot90：1→2→3→4→1；5→6→7→8→5；9/10 不动；R: r→(r+4)%16。
- mirrorX：1/3 不动；2↔4；5-8 同理；9/10 不动；R: r→(16-r)%16。
`expandInto` 各分支的位置映射与其复合分解**必须**在单测里逐分支钉死（表驱动：26 常量 × 两本原操作全枚举 + "朝中心楼梯环展开后四向皆朝中心"性质测试）。备选：运行时由向量反推——否决，热路径查表最快且可测。

### D4 匹配：orientation 校验独立于 Predicate.test
`verifyTier` 内 entry 带 orientation 时：`Orientation.matches(state, rot90^r(id))`（r = 本轮旋转尝试）。`matches` = 属性存在 + 值满足：射线属性（HORIZONTAL_FACING/FACING）精确比对；**轴属性（AXIS）双向等价**——NORTH/SOUTH 均被 AXIS=Z 满足（原木"朝北"实为"沿南北轴"，对称展开下这是唯一不自相矛盾的语义）；带 `HALF` 属性的块，id 1-4 要求 BOTTOM、5-8 要求 TOP（与 apply 的确定性写入对偶）。方块不支持该常量 → 该格不匹配。`Predicate.test` 签名不动（它拿不到 entry 上下文）。推论：全量图案（位置+朝向）仍 4 旋转不变，r=0 必中，旋转循环保留但带朝向变换后 r≠0 不再假阴性。

### D5 搭建：抽出 `resolveState`，放置带朝向
`RitualBuilderPlacement` 新增 `static @Nullable BlockState resolveState(RitualPattern, BlockEntry, int tier)`：种类解析（EXACT/TAG→品阶实例化，现状逻辑）+ `orientation != null` 时 `Orientation.apply(state, id)`。`build` 的 `setBlockAndUpdate` 改用它。`requirements()` 不变（仍按方块种类聚合，朝向不拆行）。TAG 格带朝向：运行期 `apply` 前 `supports` 防御，不支持则跳过该格（与"无法实例化"同路径）。

### D6 加载期校验（新增拒载项）
- 条目非数组/长度 <4 或 >5/类型错 → 拒载（格式迁移后这是唯一合法形状）。
- 锚点格带 `o` → 拒载（核心无朝向）。
- AIR/IGNORE 谓词格带 `o` → 拒载。
- EXACT 块不满足常量的属性需求（含墙类、UP/DOWN 对纯水平属性块）→ 拒载，日志指明格位与常量。
- `o` 值非法（id ∉ 1-26 / 未知名）→ 拒载。
- TAG 块：不校验（见 D5 运行期防御）。

### D7 采集：常量由方块属性反推，`o` 进对称归并键
`RitualCapture` 扫描时对每格 `detect(state)`（探测优先级 ROTATION_16 → facing 系 → AXIS 规范化 X→EAST/Z→NORTH/Y→UP → 无朝向=0）。对称类归并键从"字符聚类"扩为"字符 + 朝向"：以规范四分之一槽位为代表格，其余成员按所在展开分支的逆变换求期望常量，用 D4 的 `matches` 判定（轴双向等价天然兼容），不满足即报对称违规（与现有冲突回显同机制）。输出骨架条目带第 5 位（int，代表格 detect 值，0 则省略）。

### D8 迁移：脚本重排 7 文件，展开表对照验证
一次性脚本把 `{"key":..,"x":..}` 对象重排为 `["K",x,y,z]` 数组（无朝向条目即 4 元素）。验收硬标准：迁移前后 loader 展开表（排序后的 BlockEntry 全量列表）逐字节相等——格式变更与语义变更彻底解耦，回归面归零。

## Risks / Trade-offs

- [位置式数组未来加字段只能追加位次] → 接受：v4 已把可选位放末位，追加式扩展（第 6 位起）仍是合法演进；人肉可读性靠 spec 图例 + 双解析字符串名兜底。
- [R 系镜像/旋转公式手推易错] → 表驱动单测全 26 常量 × 两本原操作 + 性质测试；公式错在测试期即暴露。
- [旋转对称（风车形）朝向环不可表达] → 接受且必须讲清：位置展开群是 {id, mirrorX, mirrorZ, rot180}（双轴镜像，v2 起既有约定），朝向跟随同一群作用，故四角格位的合法对称形是"镜像形"（如东侧皆朝东/西侧皆朝西），风车形（rot90 不变但镜像不不变）不在语言内。采集报错信息带 expected/got 常量名以便玩家改摆。
- [楼梯 SHAPE 不校验 → 内/外角楼梯混摆也判匹配] → 接受：SHAPE 是邻接派生态非玩家意图态；builder 放置后原版邻接更新会自行纠正；记录于此备查。
- [TAG+orientation 运行期才暴露不支持] → 该格跳过并计入"无法放置"提示（现有路径），不崩溃；数据作者可用 `/gs_ritual_capture` 实测验证。
- [Capture 归并键扩展可能误报既有无朝向结构] → 无 `o` 的格归并键不变（null 不扩展），零回归。
- [BlockEntry 加字段影响 record 相等/构造点] → 全仓 grep 构造点（loader/matcher/placement/capture 均经此一处定义），编译期即收敛。

## Migration / Rollback

D8 脚本迁移 + 展开表逐字节对照。回滚 = 还原 Java + 还原 7 JSON（git 层面一次 revert）；风险窗口仅格式，无世界数据、无存档字段。

## Open Questions

（无——扁平枚举 + 数字编码 + 数组化均已由用户拍板。）
