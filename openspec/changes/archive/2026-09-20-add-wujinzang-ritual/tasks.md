# Tasks: add-wujinzang-ritual（批 1）

## 1. Pattern 与行为注册

- [x] 1.1 `data/gensokyou/rituals/wujinzang_circle.json`：`toggleable` 改为 `true`，跑 `tools/validate_ritual_pattern.py` 通过
- [x] 1.2 新建 `ritual/behavior/WujinzangBehavior.java`（实现 `RitualBehavior`），在 `ritual/RitualBehaviors.java` 加常量与 `register(WUJINZANG, ...)`
- [x] 1.3 `GensokyouConfig`：新增 `WUJINZANG_BASE_CAPACITY=10000`、`WUJINZANG_BASE_DRAIN=5`、`WUJINZANG_MULT=5`、`WUJINZANG_IN_RATE=1000000` 及 FX 参数项，全部 `defineInRange`

## 2. 核心生命周期钩子补全

- [x] 2.1 `RitualCoreBlockEntity.serverTick`：检测图案 A→B 直接切换，先调 A `onStructureLost` 再调 B `onFormed`
- [x] 2.2 核心级 `onFormed` 幂等与占位跳过语义就位（世界重载重扫不重复生成）
- [x] 2.3 在 `ritual/command/DebugCommands` 增 `wujinzang` 子命令，导出机读单行摘要

## 3. 内容托管与晶块生命周期

- [x] 3.1 `RitualCoreBlockEntity`：`MujinzoVault` 更名 `WujinzangVault`（含 getter/setter/tag 常量/存取）
- [x] 3.2 vault 结构落地：分区组表 `segment→{NONE,TYPED,TOTAL}` + 孤儿段 `segment→导出内容`，随 BE 持久化
- [x] 3.3 `WujinzangBehavior`：成型时对每个祭品台上方一格执行「空则放晶块 + `setBinding`；已是本核心晶块则保留；被占则记录」
- [x] 3.4 启停：启用置全部晶块 `concealed=false`；停机置 `true`
- [x] 3.5 启动校验：任一晶位非「本核心晶块或空气」时 `start()` 失败并回显被占原因
- [x] 3.6 不成型：逐晶块 `exportContents` 归入选区/孤儿段后 `removeBlock`
- [x] 3.7 再次成型：逐段 `importContents` 恢复内容
- [x] 3.8 `serverPassiveTick`：等级升降迁移（升级追加空晶块；降级撤块存孤儿段；升回恢复）
- [x] 3.9 `CrystalBlock.useWithoutItem`：有 owner 时直接 `PASS`
- [x] 3.10 `ModCapabilities`：`CRYSTAL` 的 handler 对「有 owner」的晶块返回 null
- [x] 3.11 强制加载：成型期对晶块所在区块 `setChunkForced(true)`（去重），失效/移除释放

## 4. 动态分区与 itemHandler

- [x] 4.1 静态函数：可堆叠判据（`maxStackSize>1` 且无非默认组件）
- [x] 4.2 静态函数：分区规划（同组优先/同种合并优先/空闲格式化/背压）
- [x] 4.3 `RitualCoreBlockEntity.itemHandler()` 按图案分派；新增 `WujinzangItemHandler`（跨晶合并箱、忽略槽号、余量背压）
- [x] 4.4 分区规划落到真实晶块：insert 合并/建条/格式化，extract 按条目定位并返回真实栈（`simulate` 零副作用）

## 5. 供能与灵力

- [x] 5.1 核心新增「电池核心→缓存」补料内核（世界无关纯函数 + 每秒 carry 落账），行为开关 `refillsCacheFromSocket()` 默认 false
- [x] 5.2 `WujinzangBehavior` 打开补料开关；`getCapacity()` 加 `wujinzangCapacity(level)=base×5^L` 分派
- [x] 5.3 `serverTick`：启用的无尽藏每秒从缓存扣 `drain(level)`，不足则 `setEnabled(false)` 自动停机并隐藏晶块
- [x] 5.4 端点声明：`spiritInRatePerSecond` 返回配置基项（默认 1,000,000），`spiritOutRatePerSecond` 返回 0
- [x] 5.5 确认无 `on_activate` 费、无激活配方（pattern 不写 requirements）

## 6. 终端 GUI（批 1）

- [x] 6.1 新增 `WujinzangTerminalMenu` + `ModMenus` 新 MenuType；`network` 扩展可见页/手势协议以携带 segment 定位（复用 `CrystalStorage*` 范式）
- [x] 6.2 服务端聚合视图：合并在线晶块条目、搜索/排序/翻页、仅推可见页、按 `revision` 重推
- [x] 6.3 新增 `WujinzangTerminalScreen`：左侧通用信息（信息行/启停/灵力核心槽/被占提示）+ 右侧 9×5 网格 + 搜索/排序/翻页/滚动条 + 玩家物品栏 + 原版 3×3 合成格
- [x] 6.4 存取手势与确定性 segment 定位（同种跨晶按规范序解析），存入先过拒收闸并回显
- [x] 6.5 离线晶块标记与提示
- [x] 6.6 核心右键按图案分派：无尽藏开终端，其余开通用面板（编辑杖/潜行例外不变）
- [x] 6.7 原版 3×3 合成格 + 结果格（`TransientCraftingContainer` 口径，退出归还/掉落，不自动抽料、不写回仓储）

## 7. 运行特效

- [x] 7.1 `RitualRenderState` 新增 `KIND_WUJINZANG`；`buildRenderState` 加分支（enabled/tier/底座锚点）
- [x] 7.2 `RitualCoreRenderer`：蓝色螺旋雾带（复用 `spirit_mist`，层数随 tier，包络淡入淡出）
- [x] 7.3 3 阶起：结构底座 8 个中心对称位置竖直信标激光（复用 `bolt_core/bolt_glow`）
- [x] 7.4 LOD：按距离/规模降级雾带段数与层数、跳过远处激光
- [x] 7.5 FX 参数（层数上限/颜色/激光高度宽度/半径比例/LOD 阈值）全部进 `GensokyouConfig`

## 8. lang 与数据

- [x] 8.1 `zh_cn`/`en_us` 补齐：仪式名/信息行（缓存、耗电、被占）/终端文案/「藏已满」「离线晶块」等键
- [x] 8.2 `python tools/lang_audit.py` 退出码 0

## 9. 验证

- [x] 9.1 `gradlew compileJava` 通过
- [x] 9.2 `gradlew runServer --console=plain`：`Done (`、无 registry 报错
- [x] 9.3 `/gs_debug wujinzang` 探针输出可读，断言分区/容量/耗电
- [x] 9.4 实机：成型（4/128 晶）、启停隐藏、经核心存取、动态分区、升降迁移、被占提示、终端聚合、独立晶块无影响
- [x] 9.5 性能：满配 128 晶 + 雾带 + 激光 + 8 区块强制加载下的帧率与 TPS 实测，据此定 LOD 阈值
- [x] 9.6 复查与既有 spec 的一致性（`ritual-core-automation` 祭品台代理未被破坏、`crystal-storage` 自动化接口对独立晶块不变）
