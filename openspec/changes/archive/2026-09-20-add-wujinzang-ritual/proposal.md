# Proposal: 无尽藏之仪（批 1 — 托管仓储与宽屏终端）

## Why

无尽藏晶的「双模式存储（类型制 30×long / 总量制 2000）+ 托管预留 API + `concealed` 隐藏态 + 核心 `MujinzoVault` 空壳」已由 `crystal-storage` 铺垫完成，`wujinzang_circle` pattern 也已存在（128 祭品台、0..5 阶）但未注册行为。它是唯一能把这 128 个晶块收束成**一个统一仓储**的仪式：成型即生成晶块阵列、由核心统一读写、按物品特性智能分区、并以专属宽屏终端管理。本次交付批 1（仓储面板），AE 式自动合成终端留待批 2。

## What Changes

- **注册仪式行为**：`RitualBehaviors` 注册 `wujinzang_circle → WujinzangBehavior`；pattern `toggleable` 由 `false` 改为 `true`（需要启停）。
- **晶块生命周期**：成型时在全部祭品台上方放置无尽藏晶（数量随阶层 4/8/16/32/64/128）；停机（enabled=false）时全部置 `concealed=true` 隐藏；不成型时先导出内容、再移除晶块。核心被拆=内容随 BE 消失（既有口径）。
- **内容真源 = 晶块**：物品实体住在晶块 BE；核心仅作接口与托管者，持久化分区元数据（每 segment 的组）与孤儿段（降级撤下、未分配的内容）。
- **动态分区**：物品按判据分类（`maxStackSize>1` 且无特殊组件 = 可堆叠 → 类型制；否则 → 总量制）；插入时优先找已格式化且同组可接收的晶块，否则抓一个零条目空闲晶块格式化后使用；非空晶块永不改模式。
- **等级升降**：升级追加空晶块；降级撤下多余晶块，内容先并入剩余晶块、否则存入核心孤儿段，升回再恢复。
- **核心 itemHandler 重写**：`wujinzang` 生效时，核心对外 `IItemHandler` 由「祭品台代理箱」改为「跨晶块合并箱」（插入走分类分区，抽取按条目定位）。
- **专属宽屏终端**：新增 `wujinzang` 核心 GUI（左侧通用仪式信息/启停/灵力核心槽，右侧聚合全部晶块的仓储网格 + 搜索/排序/翻页 + 玩家物品栏 + 原版 3×3 合成格）。合成格为纯原版合成，批 1 不含自动从中抽料合成。
- **供能机制（通则）**：核心新增「电池核心 → 仪式缓存」补料；非产灵仪式**只消耗缓存**。无尽藏 0 阶缓存 10000、运行耗 5 灵力/s，容量与耗电均随阶层 ×5；受灵速率固定 1,000,000/s，无输出速率；缓存扣空自动停机。无激活配方、无激活费。
- **交互锁定**：被仪式绑定（有 owner）的晶块禁止玩家右键与自动化 capability 直连；世界独立摆放的晶块保留原交互。
- **区块加载**：运行期强制加载晶块所在的 8 个区块，失效/移除时释放。
- **运行特效**：蓝色螺旋雾带（复用紫雾贴图与几何），3 阶起底座 8 个中心对称点发射信标激光；支持 LOD。
- **BREAKING 调试能力**：无尽藏晶的存储模式不再由玩家/调试命令决定，改由仪式动态决定（调试切换命令暂留，后续版本移除）。

## Capabilities

### New Capabilities

- `wujinzang-ritual`: 无尽藏仪式行为——晶块生成/隐藏/移除的生命周期、内容真源与托管、动态分类分区、等级升降迁移、孤儿段、区块强制加载、交互锁定、灵力消耗与端点属性、信息行、运行特效驱动。
- `wujinzang-storage-terminal`: 无尽藏核心的专属宽屏终端——聚合全部晶块的仓储网格、搜索/排序/翻页、存取手势、玩家物品栏，与左侧通用仪式信息区共存的布局契约。

### Modified Capabilities

- `crystal-storage`: 新增「被仪式绑定的晶块禁止交互」；明确内容真源与核心接口/托管的分工；新增动态分区组（类型制组/总量制组）语义与空闲晶块格式化；导出/导入新增孤儿段往返。
- `ritual-core-automation`: 核心 `IItemHandler` 由固定祭品台代理改为按图案分派——`wujinzang` 时为跨晶块合并箱（忽略槽号的类型合并 + 分区背压）。
- `ritual-power-attributes`: 新增非产灵仪式「电池核心→缓存」补料与「只消耗缓存」的耗能语义；新增无尽藏端点速率声明（in=1e6/s，out=0）。
- `ritual-runtime-fx`: 新增 `wujinzang` 渲染态 kind——蓝色螺旋雾带按阶层加层、3 阶起底座 8 点信标激光；参数配置化、贴图复用。
- `ritual-core-interface`: 核心右键界面允许行为指向专属屏幕（无尽藏用宽屏终端而非通用面板），启停/信息行协议不变。
- `ritual-lifecycle`: 补齐「图案直接切换（A→B）」的清理与「成型替换」调用，使 `onStructureLost`/`onFormed` 在图案变更时也被触发，并保证 `onFormed` 幂等（世界重载不重复生成）。

## Impact

- **代码**：
  - `ritual/behavior/WujinzangBehavior.java`（新）、`ritual/RitualBehaviors.java`（注册）。
  - `block/entity/RitualCoreBlockEntity.java`：分区元数据/孤儿段持久化、`getCapacity` 分派、`itemHandler` 按图案分派、电池→缓存补料、图案变更钩子、区块强制加载管理、渲染态分支。
  - `block/entity/CrystalBlockEntity.java`：分区组读写、空闲判定、绑定态交互锁的支撑（复用既有 mode/export/import/binding）。
  - `spirit/SpiritPowerHelper.java` 或核心侧：电池→缓存补料内核（可测纯函数）。
  - `menu/RitualCoreMenu` / `client/screen`：新增无尽藏终端 Menu/Screen（或按查看者分派的变体）；`network` 复用可见页快照 + 手势协议并扩展 segment 定位。
  - `client/renderer/RitualCoreRenderer.java` + `ritual/RitualRenderState.java`：新 kind 与几何。
  - `config/GensokyouConfig.java`：缓存/耗电/倍率/端点速率/FX 参数。
- **数据/资源**：`wujinzang_circle.json` 改 `toggleable`；lang（zh_cn/en_us）信息行与终端文案；复用 fx 贴图（不新增）。
- **兼容**：`MujinzoVault` 更名 `WujinzangVault`（当前空壳，无存档负担）；旧独立晶块行为不变。
- **风险**：8 区块常加载的性能开销；128 晶块渲染的 FPS 压力（靠 LOD）；聚合视图跨多 BE 的一致性；批 2 合成终端未交付。

## Out of Scope

- AE 式自动合成（从仓储自动抽料、合成、回填）终端（批 2，另开 `add-wujinzang-craft-terminal`）；批 1 的原版 3×3 合成格不属于此项。
- 世界级共享金库 / 跨核心跨维度共享。
- 晶块升级扩容道具、独立晶块的额外玩法。
