## 1. 不可破坏与删除获取途径

- [x] 1.1 `ModBlocks.CRYSTAL` 属性改为 `.strength(-1.0F, 3600000.0F).noLootTable()`（保留 mapColor/sound/lightLevel/noOcclusion）
- [x] 1.2 删除 `data/gensokyou/loot_table/blocks/crystal.json`
- [x] 1.3 从 `data/gensokyou/ritual_recipes/zaohua_circle.json` 删除 `zaohua_crystal_storage` 条目
- [x] 1.4 确认 `CrystalBlock` 无依赖掉落表的逻辑，`saveToItem` 空实现保留（不再有破坏路径，但防御复制）

## 2. 双模式存储模型（A 类型制 / B 总量制）

- [x] 2.1 `GensokyouConfig`：保留 `STORAGE_TOTAL_CAPACITY`（B 模式），新增 `STORAGE_MAX_TYPES`（A 模式，默认 30，1..1024）与 `STORAGE_PER_TYPE_CAP`（A 模式，默认 `Integer.MAX_VALUE`，可到 `Long.MAX_VALUE`）
- [x] 2.2 `CrystalBlockEntity` 新增持久化 `mode` 字段：新块默认 A；旧档无字段读取为 B
- [x] 2.3 `capacity()`/`totalCount()` 语义整理：按模式暴露类型上限/每类上限或总量上限
- [x] 2.4 `canInsert`/`insert` 按模式分支（A：类型闸 + 每类饱和空间；B：总量饱和空间）；`MAX_ENTRIES` 按模式取值
- [x] 2.5 聚合/计数全程 long + 饱和加法，排查所有 `(int)` 转换点
- [x] 2.6 `setMode(Mode mode, boolean clearContents)` 原子方法（供调试命令与将来仪式共用）
- [x] 2.7 清理对旧单模式语义的假设与废弃引用

## 3. 界面与协议适配

- [x] 3.1 `CrystalStoragePagePayload` 新增 `mode` 字段；两个指标按模式填充或统一传「当前/上限」对，避免 int 溢出
- [x] 3.2 `CrystalStorageMenu.snapshot()` 填充新字段
- [x] 3.3 `CrystalStorageScreen` 底部计数行按模式显示（A「类型 X/30」/ B「件数 X/2000」）；lang 新增对应键（zh_cn/en_us）
- [x] 3.4 搜索/滚动/排序/手势逻辑保持不变，回归自测

## 4. 模式切换调试命令（测试期临时能力）

- [x] 4.1 `ritual/command/DebugCommands` 新增模式切换子命令（看向的晶块或坐标参数）
- [x] 4.2 命令调用 `setMode(mode, true)` 直接清空存储并重推可见页
- [x] 4.3 确认不提供任何 GUI/物品切换入口；在代码注释标注「正式版删除」

## 5. 隐藏状态预留

- [x] 5.1 `CrystalBlock` 新增 `CONCEALED` boolean property，默认 false
- [x] 5.2 `assets/gensokyou/blockstates/crystal.json` 补 concealed 两个变体
- [x] 5.3 `CrystalRenderer` 读取 concealed，true 时不绘制
- [x] 5.4 `CrystalBlock` 形状/交互在 concealed=true 时禁用（不可开界面）
- [x] 5.5 确认无任何逻辑主动置 true（纯预留）

## 6. 核心托管预留接口

- [x] 6.1 `CrystalBlockEntity`：把条目序列化抽为公共 `exportContents()` 与 `importContents(tag, merge)`，均携带 `mode`
- [x] 6.2 `insert`/导入路径支持同种归并
- [x] 6.3 `CrystalBlockEntity` 预留 `ownerDim`/`ownerPos`/`segment` 字段的 NBT 存取（只存不读）
- [x] 6.4 `RitualCoreBlockEntity`：预留 `MUJINZO_VAULT` tag 常量与空读写壳（按 cell 原样承载 mode + 条目），确认不影响成型/启停/失效
- [x] 6.5 在 design 中记录的 `onStructureLost` previous-match 方案（方案 B：核心自记旋转）留待仪式变更；本次不实现，仅确认字段位可容纳

## 7. 验证

- [x] 7.1 `gradlew compileJava` 通过
- [x] 7.2 `python tools/lang_audit.py` 退出码 0
- [x] 7.3 实机：放置藏晶→不可挖、爆炸无效、无掉落
- [x] 7.4 实机：A 模式单类超 2000 持续累加；第 31 类被拒；同种在 30 类满时仍可入
- [x] 7.5 实机：B 模式总量 2000 截断；可超 30 类；旧档回退 B 模式
- [x] 7.6 实机：调试命令切换模式→清空、计数归零；界面无切换控件
- [x] 7.7 实机：`/setblock` 置 concealed=true → 不渲染、不可交互；置回 false 恢复
- [x] 7.8 实机/探针：export→import 往返一致（含 mode）；import 归并正确；绑定字段与核心 vault 段存读保留
- [x] 7.9 回归：既有造化配方（造化石/符卡星）不受影响；`/reload` 后无配方丢失报错
