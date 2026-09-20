# Proposal: 无尽藏晶存储重构与仪式托管预留

## Why

无尽藏晶（`gensokyou:crystal`）当前是「可破坏 + 全局总量 2000 件预算 + 造化配方获取」的独立存储方块。新定位把它改为**无尽藏仪式的产物与载体**——方块随仪式成型出现、由仪式批量托管，不成型或不运行时隐藏或移除。现有容量模型、获取途径与「破坏即全灭」语义都与该定位冲突，需先重构存储模型并预留托管接口，为后续独立的仪式变更铺路。

## What Changes

- **BREAKING** 存储模型：容量语义由单一「全局总件数上限 2000」改为**双模式并存**：
  - **A 模式（类型制）**：类型数上限 30 + 每类 long 计数（默认上限可 config 调整）；
  - **B 模式（总量制）**：保留现有全局总件数上限 2000、种类不限。
  - 同种恒合并、逐件保留 NBT/组件、拒收闸（容器类 / 单件 NBT 超限）两模式一致。
- 双模式的默认与切换：新放置/仪式生成的晶块默认 **A 模式**；旧存档无 mode 字段时回退 **B 模式**。测试期提供调试命令手动切换，**切换时直接清空存储内容**（避免混模式脏数据）；正式版 MUST NOT 提供手动切换，模式由仪式决定。
- **BREAKING** 不可破坏：`strength(-1.0F, 3600000.0F)` + `noLootTable`；移除方块掉落表，玩家无法挖掘回收。
- **BREAKING** 删除获取途径：移除 `zaohua_circle.json` 的 `zaohua_crystal_storage` 配方。藏晶不再由合成获得，将随无尽藏仪式成型出现。
- 新增 `concealed` 方块状态（默认 false）：供仪式「停机但结构仍在」时隐藏方块。**本次仅预留，不接线、无行为变化**。
- 新增托管预留：`CrystalBlockEntity` 暴露内容导出/导入 API 与 owner/segment 绑定字段；`RitualCoreBlockEntity` 预留 vault 存储段。**均为空实现或只存不读**，不改变现有行为。
- 保留 `IItemHandler` 自动化接口、自绘网格界面与「仅同步可见页」协议。
- 明确本次**不实现**无尽藏仪式（pattern 与行为另开变更）。

## Capabilities

### New Capabilities

（无。本次不引入新的用户可见能力；托管契约待仪式变更落地时再成为正式 requirement。）

### Modified Capabilities

- `crystal-storage`: 容量语义由单一「全局总件数上限」改为 A/B 双模式并存（A 类型制 30×long / B 总量制 2000 不限种类），含默认与（测试期）手动切换清空；破坏语义由「可破坏且内容全灭」改为「不可破坏、无掉落」；删除「获取配方」；新增 `concealed` 隐藏状态；新增为仪式托管预留的导出/导入与绑定接口（预留，不改现状行为）。

## Impact

- **代码**：
  - `ModBlocks.CRYSTAL` 属性改为不可破坏 + 无掉落。
  - `GensokyouConfig`：并存三项——`STORAGE_TOTAL_CAPACITY`（B 模式，沿用）、`STORAGE_MAX_TYPES` 与 `STORAGE_PER_TYPE_CAP`（A 模式）。
  - `CrystalBlockEntity`：新增持久化 `mode` 字段（A/B），容量与插入逻辑按模式分支；新增 `exportContents`/`importContents`、owner/segment 预留字段。
  - `ritual/command/DebugCommands`：新增测试期模式切换子命令（切换即清空）。
  - `CrystalBlock`：新增 `CONCEALED` 属性；`CrystalRenderer` 读取该属性（预留）。
  - `CrystalStorageMenu` / `CrystalStoragePagePayload` / `CrystalStorageScreen`：底部计数行按模式显示（A「类型 X/30」、B「件数 X/2000」）；payload 携带 mode 并按模式传双指标。
  - `RitualCoreBlockEntity`：预留 vault 存储段（空壳）。
  - `RitualBehavior`：评估 `onStructureLost` 是否需要携带 previous match（预留决策）。
- **数据**：`data/gensokyou/ritual_recipes/zaohua_circle.json` 删除条目；`data/gensokyou/loot_table/blocks/crystal.json` 删除；`assets/gensokyou/blockstates/crystal.json` 增补 concealed 变体；lang `zh_cn`/`en_us` 增补隐藏态与模式相关键（如需要）。
- **存档**：条目计数仍为 long；新增 `mode` 字段，旧档缺失时回退 B 模式，无迁移脚本。旧档在 B 模式下可能超 30 类，A 模式新增异种才受类型上限约束。
- **风险**：不可破坏方块的误放无法回收；双模式切换清空的不可逆；托管预留若与后续仪式设计不匹配需回改。详见 design。
