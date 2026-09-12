# Tasks: add-ritual-core-automation

## 1. GUI 首帧闪烁 bugfix

- [x] 1.1 `RitualCoreScreen.init()`：start/stop 按钮 `addRenderableWidget` 后立即 `visible = false`，显隐唯一由 `containerTick()` 按 payload 收敛（B 方案，与动作按钮既有模式对齐）

## 2. Offering 单件化（废弃 count）

- [x] 2.1 `RitualPattern.Offering` 移除 `count` 字段，构造点全部改调用方
- [x] 2.2 `RitualPatternLoader.parseOffering`：显式 `count > 1` 拒载并报因，字段缺省或 =1 正常加载
- [x] 2.3 `RitualOfferings`：`check`/`upkeepTick`/`shrink` 按"单台恒 1 件"简化（满足 = 台面持有匹配物品；扣减 = 置空台面）
- [x] 2.4 `RitualInfoPayload.Entry` 移除 `count`（write/read codec 同步），`snapshot()` 与 `RitualCoreScreen` 清单行去 `×N` 绘制
- [x] 2.5 校验三处同步：编辑器侧 `RitualPatternValidator` 增加 count>1 拒载规则；`RitualPatternValidatorRulesTest`/`ParityTest` 补用例；核对 `tools/validate_ritual_pattern.py` 是否处理 requirements 字段并同步

## 3. 祭品台单件不变量

- [x] 3.1 `RitualPedestalBlockEntity.setHeld`：入参 count>1 时台面截留 1 件、余量在服务端于台面位置 `addFreshEntity` 掉落；确认全部调用点处于服务端分支
- [x] 3.2 加载路径不 clamp（历史超限栈视为满槽，自然回落），在字段 javadoc 注明该容错语义

## 4. passive 产物掉落

- [x] 4.1 `GensokyouConfig` 新增 COMMON 项 `RITUAL_OUTPUT_DROP_RADIUS`（int，默认 3）
- [x] 4.2 `RitualCoreBlockEntity.tickPassiveRecipes`：删除"目标台选择 + merge 写回 + 容量不足整单放弃"整段；扣料成功后以核心为中心均匀随机落点（`r = R·√rand`、`θ = 2π·rand`、y = 核心顶面 +0.25）生成完整结果栈 `ItemEntity` 并 `setDefaultPickUpDelay()`

## 5. 核心 IItemHandler 代理箱

- [x] 5.1 `RitualCoreBlockEntity` 内实现活代理 `IItemHandler`（稳定单例字段）：台位每次调用现查 `activeMatch.keyedPositions()` 中 BE 为祭品台者按 (y,z,x) 排序；`getSlotLimit=1`；`insertItem` 空台落 1 件返余量、`extractItem` 取 ≤1 件写回、`isItemValid` 仅判空、simulate 零副作用；台 BE 消失按空槽 no-op
- [x] 5.2 新增 `@EventBusSubscriber` mod bus 监听 `RegisterCapabilitiesEvent`：`registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.RITUAL_CORE, (be, side) -> be.itemHandler())`，全方向同一实例
- [x] 5.3 `serverTick` 重扫处：`activeMatch` null→非null（成型）与非null→null（失效）两分支各调 `serverLevel.invalidateCapabilities(pos)`

## 6. 验证

- [x] 6.1 `gradlew compileJava test` 通过（重点：Validator 双侧 parity 用例）
- [x] 6.2 `runServer --console=plain` 启动日志无 registry/loader 错误（Forgematica 蜂巢噪声除外）
- [x] 6.3 游戏内验证矩阵（kagutsuchi 圈 + 临时 passive 配方 datapack，验后清理）：漏斗成型前 0 槽 / 成型后逐台铺 1 件 / 台满背压 / 下方漏斗抽料 / 产物圆盘随机掉落不回台 / 手动右键逐台放料回归不变
- [ ] 6.4 GUI 回归：加具土命之焰打开界面无按钮闪现；toggleable 仪式（dev 世界杖建或既有存档）按钮正常出现可用

## 7. 文档沉淀

- [x] 7.1 `.opencode/skills/ritual-design/SKILL.md` §4 硬性不变量补录：一台一件（台面容量 1、单条 requirement 恒 1 件、多件=多台）、passive 产物核心半径 3 圆盘随机掉落不登台
- [x] 7.2 同 skill 的 requirements schema 说明处删除 `count` 字段描述
