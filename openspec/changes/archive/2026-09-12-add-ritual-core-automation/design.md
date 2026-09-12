# Design: add-ritual-core-automation

## Context

仪式核心（`RitualCoreBlockEntity`）无任何背包；物品只存在于结构内祭品台（`RitualPedestalBlockEntity.held`，单栈字段）上，唯一写入路径是玩家逐台右键（每次 1 件）。漏斗/管道等自动化完全无法与仪式交互。现有代码中唯一可能让台面出现 >1 栈的路径是 `tickPassiveRecipes` 的产物回写（merge 同物品堆），这与"一台一件"的原始设计意图相悖。`RitualOfferings` 的 `count` 字段允许单台需求 >1 件，但手动放料根本喂不满足，实为死配置。本 mod 尚无任何 capability 用例（全仓库零引用），本次是首个。

已对 21.1.248 sources jar 实测确认的 API：
- `Capabilities.ItemHandler.BLOCK` = `BlockCapability<IItemHandler, @Nullable Direction>`（`net.neoforged.neoforge.capabilities.Capabilities`）
- `RegisterCapabilitiesEvent.registerBlockEntity(cap, BlockEntityType<BE>, ICapabilityProvider<? super BE, C, T>)`，provider 为函数式接口 `T getCapability(O object, C context)`
- `ICapabilityProvider` javadoc 明确要求：已返回 capability 失效时必须调 `Level.invalidateCapabilities(pos)`（21.1 块级 cap 有缓存）

## Goals / Non-Goals

**Goals:**
- 核心对外表现为"容量 = 成型结构祭品台数、每格 1 件"的标准箱子，任何走 `IItemHandler` 的自动化（漏斗、管道、未来人形投料）可投料/取料
- 祭品台单件不变量成为代码级事实，而非约定
- passive 产物按原始设计掉地上（半径 3 圆盘随机落点）
- 消除核心 GUI 启停按钮首帧闪烁

**Non-Goals:**
- GUI 投料槽位、核心右键手动投料（明确不做，避免交互复杂度）
- 核心主动抽/输灵力以外的能力；物品过滤路由（requirement 感知的智能落位）
- "收集仪式"向指定箱子直输产物（未来独立立项）
- 祭品台方块自身挂 capability（只挂核心；管道想直连台位另说）

## Decisions

### D1 活代理 handler，核心零存储
`RitualCoreBlockEntity` 持有一个稳定的 handler 单例字段（NeoForge 按返回实例做缓存，实例必须稳定），handler 每次调用时**现场**从 `activeMatch` 解析台位列表并直接读写祭品台 BE——核心不复制背包、不加 NBT。台位识别不认 palette key（自定义仪式可能用别的字符），按"keyedPositions 全部坐标中 BE 为 `RitualPedestalBlockEntity` 者，按 (y,z,x) 规范序排序"推导；单次调用内台 BE 消失/换块则视为空槽 no-op。
备选（否决）：核心侧镜像背包 + 双向同步——双真相源，与启动扣减/配方采集/手动取放三处既有逻辑打架。

### D2 注册与失效
mod bus `@EventBusSubscriber` 内 `RegisterCapabilitiesEvent`：`registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.RITUAL_CORE, (be, side) -> be.createItemHandler(side))`，全方向返回同一 handler（side 仅作参数保留，不做方向差异）。在 `serverTick` 重扫处——`activeMatch` 出现（成型瞬间）与失效（置 null 分支）两处调 `level.invalidateCapabilities(pos)`，保证缓存的漏斗及时改判。未成型 handler 槽数恒 0，返回非 null 无副作用。

### D3 槽语义 = 单件箱子
`getSlotLimit=1`；`isItemValid(slot, stack)`：非空即 false（不校验物品类型，纯箱子语义——台面脏物由严格等值配方匹配自行惩罚，堵没堵是玩家自动化的事）；`insertItem` 模拟/真实：空台 → 落 `copyWithCount(1)`，返回余量交还给调用方（漏斗天然背压：台满则料留漏斗）；`extractItem`：台非空 → 取 min(k, held) 件并写回余量。单件上限在 `RitualPedestalBlockEntity.setHeld` 统一收敛：count>1 时截 1、余量服务端当场掉落实体（覆盖所有现存写入方，无需逐处防）。存档加载不做 clamp：历史超限栈视为"满槽"，被配方/取料逐次削减，无升级路径能再增大。

### D4 passive 产物：半径 3 圆盘随机掉落
删除 `tickPassiveRecipes` 整段"目标台选择 + merge + 容量不足整单放弃"逻辑（RitualCoreBlockEntity.java:348-378 的结果落位部分）；改为扣料成功后在水平圆盘内均匀随机落点生成 `ItemEntity`（携带完整 result 栈，count>1 合法——产物不再受台面容量约束）：`r = 3·√rand`、`θ = 2π·rand`、`x/z = 核心中心 ± r·(cos,sin)`、`y = 核心顶面 + 0.25`，`setDefaultPickUpDelay()` 防瞬间回吸。半径进 `GensokyouConfig.RITUAL_OUTPUT_DROP_RADIUS`（默认 3，COMMON），遵守"可调数值进 config"仓库铁律。落点压在结构方块上属正常（物品自行弹出）；漏斗摆在圆盘内可承接。

### D5 废弃 Offering.count
`RitualPattern.Offering` 移除 `count` 字段；`parseOffering` 遇显式 `count>1` 拒载并报因（字段缺省/不存在 = 1 件，不报错，向后兼容读取面收窄为"写了且>1 才炸"）。`RitualOfferings.check/upkeepTick/shrink` 按恒 1 件简化。连带清显示：`RitualInfoPayload.Entry` 移除 `count`（stream codec 同步），`RitualCoreScreen` 清单行不再画 `×N`，仅图标 + ✓/✗。多件需求的表达 = 多条 requirement 绑定不同 `slot`。配方 `Ingredient.count` 不动（跨台池化，与单件天然兼容）。

### D6 启停按钮闪烁：B 方案
`RitualCoreScreen.init()` 中 start/stop 创建后立即 `visible = false`，显隐唯一由 `containerTick()` 按 payload 收敛（与动作按钮既有模式对齐）。代价：toggleable 仪式打开后按钮最晚晚一 tick 出现，不可感知。

## Risks / Trade-offs

- [21.1 块级 cap 缓存导致成型后漏斗仍见 0 槽] → 成型/失效两态切换处强制 `invalidateCapabilities`；验证用例覆盖"先放漏斗后成型"顺序。
- [管道灌满台面后配方严格等值永不命中（脏台面死锁）] → 属既有匹配的固有语义；核心下方漏斗可抽，自动化自行清台即解；spec 场景显式覆盖"抽出"路径。
- [handler 现场解析台位列表在每次 cap 调用排序] → 台位 ≤ 几十个、漏斗每 8 tick 调一次，开销忽略；不做缓存以保持与结构变化零脱节。
- [setHeld 截断掉落在客户端预测路径重复掉] → setHeld 仅服务端调用（现有全部调用点均在 `!isClientSide` 分支），无此风险，实现时保持该纪律。
- [`Entry.count` 移除改 wire format] → 仅影响同版本 S2C 自研包，无跨版本兼容负担。

## Migration Plan

dev 阶段无存档兼容压力：无内容使用 `requirements`/`count>1`（全仓库仅 kagutsuchi 且无 requirements）。回滚 = revert 提交即可，无持久化结构变化（核心零新增 NBT）。

## Open Questions

无——半径、落点、过滤策略、schema 收紧幅度均已与用户拍板。
