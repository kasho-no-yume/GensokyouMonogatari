# Tasks: ritual-orientation-schema

## 1. 朝向常量核心逻辑（纯函数，先行可单测）

- [x] 1.1 新增 `ritual/Orientation.java`：26 常量表（D2）+ 名↔id 双解析 + 属性需求谓词 + `apply(state,id)` / `extract(state)` / `supports(state,id)` + `rot90(id)` / `mirrorX(id)` 两张置换表；R 系罗盘↔vanilla 映射（实测 1.21.1 恒等：北0 东4 南8 西12）
- [x] 1.2 表驱动自检 `gs_ritual_selftest`（RitualCommands.OrientationSelfTest，无 JUnit 基建走命令路径）：26 常量 × 两本原操作封闭性/对合性；R 系公式；"朝中心楼梯环"展开性质；真实方块 apply/matches/detect 往返；rotateBy 与 matches 自洽（26×4）。实测曾抓到 rot90 TOP 段恒等 bug，修复后 0 failures

## 2. schema v4：数组条目 + Loader

- [x] 2.1 `RitualPattern.BlockEntry` 加 `@Nullable Integer orientation`；全仓构造点跟改（编译期收敛）
- [x] 2.2 `RitualPatternLoader` 条目解析改位置式数组 `["key",x,y,z,o?]`（形状/类型/越界拒载，D6）；`expandInto` 各分支按位置映射复合分解同步应用置换表（D3）
- [x] 2.3 加载期朝向校验（D6）：锚点/AIR/IGNORE 带 `o` 拒载；EXACT 块不满足常量属性需求拒载；TAG 不校验
- [x] 2.4 迁移脚本重排 7 个 rituals JSON 为数组格式（8914 行 → 1509 行）；展开表对照验证通过（无朝向条目展开为纯函数，元组集逐字节相等即充分）
- [x] 2.5 回归：`runServer` 确认 7 图案照常加载（`Loaded 7 ritual patterns` 无 warn）；临时楼梯环 + 3 个拒载用例实测全部按预期（`Rejected ritual pattern` ×3 报明原因，有效用例加载为第 8 图案），验证后已移除

## 3. 匹配器

- [x] 3.1 `RitualMatcher.verifyTier`：entry 带朝向时 `supports` + `extract == rot90^r(id)` 校验（D4，实现为 `Orientation.matches(state, rotateBy(id, r))`）
- [x] 3.2 回归（游戏内）：无朝向图案四旋转搭建仍全匹配；楼梯环结构四向摆放匹配、单格朝向打反不匹配——公式自洽 selftest 覆盖 + 用户 runClient 目检确认

## 4. 搭建

- [x] 4.1 `RitualBuilderPlacement` 抽出 `resolveState(pattern, entry, tier)`（种类+品阶+朝向），`build` 改用；TAG 运行期不支持常量 → 跳过计入"无法放置"
- [x] 4.2 冲突预检接入含朝向谓词：朝向不符的已放方块 → 冲突红框中止
- [x] 4.3 回归（游戏内）：既有图案搭建结果逐格与变更前一致（创造模式摆一个召唤环对照）——用户 runClient 目检确认

## 5. 采集

- [x] 5.1 `RitualCapture`：`detect(state)` 反推常量（D7 探测顺序，AXIS 规范化）；对称归并键纳入朝向（null 不扩展，零回归）；骨架条目输出第 5 位 int + 数组格式
- [x] 5.2 实测（游戏内）：手摆朝中心楼梯环 → `/gs_ritual_capture` 输出带 `o` 且可被 loader 加载、可被构建杖搭建复建——用户 runClient 实测（期间发现两处可用性问题已修：扫描区自脚下-1 起、冲突报错带 expected/got 常量名）

## 6. 文档与收尾

- [x] 6.1 常量表 + 数组格式图例进 spec delta（已备）；ROTATION_16 罗盘序、sources jar 仅含补丁类、runServer 僵尸进程/环境噪声三坑已沉淀 `neoforge-1211-dev` skill
- [x] 6.2 `gradlew build` 全绿 + 专用服务端启动无新告警（`Loaded 7 ritual patterns`、`Done`、无 Rejected/Errors）
