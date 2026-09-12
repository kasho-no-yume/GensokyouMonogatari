# Design: kagutsuchi-flame-ritual

## Context

- 仪式框架现状：`RitualCoreBlockEntity.serverTick` 每 tick 重扫匹配（20t 周期）+ `enabled` 门控分发 `RitualBehavior.serverTick`；行为类为无状态单例，状态一律落核心 BE（先例：pendingLink/portalPos）。`RitualBehaviors` 按 patternId 注册。
- 加具土命图案已就绪：tiers [0,1,2,3]，`P` 在轴上展开为 4 座祭品台；**无 recipe、无 requirements，缺省 `toggleable: false`**（现被 `ritual-core-interface` spec 当作不可开关示例——需一并修正）。
- 祭品台为单件不变量（每台 1 物品），`PedestalItemHandler` 已对外暴露自动化代理；`setPedestalsActive` 启停时广播悬浮渲染。
- 灵力型制现状：核心 `storedSpiritPower` int、`SpiritStorageBlockEntity.stored` int、`RitualInfoPayload.stored/capacity` writeVarInt、玩家池 float。用户裁定：仪式侧全面 long，玩家池不动。
- GUI 通道现状：`RitualCoreMenu` 零槽位、纯按钮通道；`RitualInfoPayload` 仅开界面/点按钮时推送，无周期刷新——倒计时不能走这条链。

## Goals / Non-Goals

**Goals:**
- 加具土命之焰完整运行逻辑：点火即吞、批次状态机、速率/缓存/空烧/停等、无缝换批。
- 仪式侧灵力 long 化（BE、基类、payload），一次改到位供后续高级仪式直接享用。
- `spirit_core` 电池物品 + 仪式 GUI 输出槽，按"多档分化"预留结构但只出默认档。
- 倒计时经菜单 DataSlot 每 tick 自动同步；火焰粒子表现。

**Non-Goals:**
- 灵力核心分品阶、合成链平衡（配方占位）。
- 传输/存储仪式、隙间外任何灵力网格扩展。
- 玩家灵力池型制变更；`SpiritPowerHelper` float 通道重构。
- 祭品台交互改动（点火即吞模型使"烧中取回"问题不存在）。

## Decisions

### D1 点火即吞：批次不持有台位引用

`startBatch`：规范序（y,z,x，即 `match.keyedPositions` 展开序）扫描 `P` 台 → 首个"可燃且非黑名单"物品**当场销毁**（或替换为容器残留），核心 BE 记录批次态 `{fuelIcon: ItemStack, totalTicks: int, remainingTicks: int}`。燃烧全程与台位无关。
- 消灭"烧中物品还在台上"的取回/回滚/竞态一类全部问题（用户裁定"直接没了"）。
- 容器残留：`stack.getCraftingRemainingItem()` 非空（熔岩桶→铁桶）时**在点火瞬间**放回原台（台此时必空）。泛化兼容模组瓶装燃料。
- 备选（否决）：延迟销毁到批次结束——保留取回语义但引入状态耦合，复杂度不值。

### D2 批次状态机与无缝衔接

```
IDLE ──(can && pick成功)──▶ BURNING ──(remaining→0, pick成功)──▶ BURNING（同tick）
         ▲                    │ remaining→0, 缓存满或无可燃
         │ 台全空             ▼
         └──(台上有货&缓存<上限)── STALLED ──(缓存<上限)──▶ 立即同tick选批
BURNING 中缓存触顶：remaining 继续走、产出入账停（空烧），批末走上面分支
```

- 每 tick 顺序：入账（未满时 `cache += rate/tick`）→ `remaining--` → 归零则尝试下一批（尝试即含选批，选批内含缓存检查）——换批与点火同 tick 发生，且剩余 tick 溢出结转给下批（`overflow = -remaining`，新批 `remaining = total + overflow`），客户端永不见"停机帧"。
- 停机（enabled=false）：批次冻结存盘，重启续烧当前批；结构失效（`onStructureLost`）：批次作废、粒子停。
- 速率/缓存随等级：`rate = BASE_RATE × 4^level`（/tick 用整数累加器 `rateAccum += rate; cache += rateAccum/20; rateAccum %= 20`，BASE_RATE=20 时恒整），`capacity = BASE_CAPACITY × 10^level`。

### D3 可燃物判定 = 熔炉燃烧时长 + 配置黑名单

`stack.getBurnTime(null) > 0` 即合格（实测 1.21.1 环境原木可烧，判定天然覆盖木板/原木/煤/熔岩桶等；模组燃料注册即生效）。黑名单 `KAGUTSUICHI_FUEL_BLACKLIST`：物品 ID 字符串列表，`defineListAllowEmpty`，默认空表，照 `RUNE_AFFIX_POOL` 范式。时长语义直接取燃烧 tick 数（煤 1600t=80s → L0 产 1600 灵力）。
- 备选（否决）：自建 `#gensokyou:kagutsuchi_fuels` 标签白名单——把原版可燃直觉挡在门外，维护成本高。
- **实现前验证**：1.21.1 的 `getBurnTime` 签名/归属（ItemStack vs NeoForge 扩展）按 neoforge-1211-dev skill 工作流 javap 核实后再动笔。

### D4 仪式侧 long 化的边界

`RitualCoreBlockEntity`：`long storedSpiritPower`、`receive/extract(long)`、`getCapacity()` 按 activeMatch 分派——CAPACITOR 读配置，KAGUTSUICHI 走 D2 公式（其余图案返回电容配置值，行为不变）。`SpiritStorageBlockEntity` 同步 long 化。`RitualInfoPayload` stored/capacity 改 `writeLong`。调用方（Capacitor/Generator/Relay/Tempering）随签名自然适配；玩家池接口处显式窄化。`buffer` 持久化 `putLong/getLong`，开发期不做 int 旧档兼容读。
- 缓存复用 `storedSpiritPower` 而非新字段：payload 快照、receive/extract、GUI 已有展示位零改动即通。

### D5 输出通道：GUI 电池槽，缓存做缓冲池

生产管线：`产出→缓存(上限) →(每tick节流推)→ 电池`。规则 3 的"满"因此实际只在电池满或未插时触发——缓存语义即"电池拔插缓冲池"，自洽。
- 节流：`fillRatePerSec`（默认档 100/s）同 D2 累加器法逐 tick 折算，推入量 = min(缓存, 电池空余, 本tick配额)。副作用可接受：L2(320/s) 起注灵速率低于产率，高级仪式逼玩家换高档电池——正是"分三六九等"的成长钩子。
- 电池槽为核心 BE 新字段 `batteryStack`（单槽，NBT 持久化）；`RitualCoreMenu` 挂真 `Slot`（校验 `SpiritCoreItem`），内容经原版菜单协议自动同步，BE 无需 getUpdateTag 扩展。拆核心掉落电池（`RitualCoreBlock` 破坏钩子）。自动化不暴露电池槽（本期不给漏斗灌电池）。

### D6 倒计时同步 = DataSlot，燃料身份 = 事件 payload

`RitualCoreMenu.addDataSlot` ×2（remainingTicks / totalTicks，int 域足够——最长批 20000t 熔岩桶），原版每 tick 同步，Screen 画进度条 + "剩余 N 秒"。当前燃料图标与名称无法进 DataSlot（栈），扩展 `RitualInfoPayload` 增 `fuelItem` 字段；燃料在批切换时**本就发生状态变更**，由行为在选批/批末/STALLED 进出时向"正在看本核心界面的玩家"（`serverLevel.getEntities...` 或菜单反查）重推快照——低频事件推送，无周期网络开销。

### D7 `spirit_core` 定值范式

仿 `BulletCoreItem`：构造入参 `{capacity=30000L, fillRatePerSecond=100}`（默认档常量，分档=新物品新参数，结构已就位）。存储走数据组件 `ModDataComponents.SPIRIT_CORE_POWER`（record `SpiritCoreData(long stored)`，Codec）；tooltip 显示 `stored/capacity`；耐久条复用：`damage = (1 - stored/cap) 映射` 给满电一眼可辨（可选，低优先）。创造栏收录、lang 中英双份、占位合成。
- 备选（否决）：走 NeoForge `IEnergyStorage` capability——灵力体系是私有的（float 玩家池+long 仪式池自定义语义），FE 桥接留待真有interop需求。

### D8 粒子

行为 `serverTick` 内 BURNING（含空烧期）时：每 4 tick 于核心上方 `level.sendParticles(Particles.FLAME, core中心±0.4, y 0.9~1.4, 2~3 粒, 微小速度, 0~1 额外度)`；等级越高频率/密度递增（`interval = max(2, 6 - level)`）。服务端发粒子即广播附近客户端，零新代码路径。STALLED/IDLE 无粒子——视觉即状态语言。

### D9 toggleable 与 spec 联动

`kagutsuchi_flame_circle.json` 加 `"toggleable": true`。`ritual-core-interface` delta：不可开关示例场景**去具体化**——其余 7 个图案 JSON 已在 0156db2 删除（源树唯一数据图案即加具土命），措辞改为"未声明 toggleable（缺省 false）的仪式"，不再点名。

## Risks / Trade-offs

- **[1.21.1 燃料 API 形态不确定]** `getBurnTime` 归属签名若与预期不符 → 实现首日按 skill 工作流 javap 验证，判定逻辑封装单点（`KagutsuchiFuel.isBurnable(stack)`），换 API 只动一处。
- **[long 化波及既有行为]** receive/extract 签名改动牵动 Capacitor/Generator/Relay/Tempering → 编译器全量兜底；改动纯拓宽（int 调用可隐式转 long），风险集中在 payload writeVarInt→writeLong 的读写对称（同一 codec，必成对）。
- **[空烧白烧争议]** 缓存满时燃料持续损耗是有意设计（熔炉空烧直觉 + 催促接电池）→ spec 场景固化语义，反馈期可只调配置不返工。
- **[自动化竞态]** 漏斗在选批前抽走台面燃料——合法行为（物品本就可自由流通），批次态不引用台位故无一致性破坏。
- **[DataSlot 每 tick 同步成本]** 仅菜单打开时同步（原版协议自带该门控），2 int/玩家/仪式，可忽略。
- **[重推 payload 找玩家]** "正在看本核心界面的玩家"枚举无现成工具 → 走 `player.containerMenu instanceof RitualCoreMenu && pos 匹配` 遍历 `serverLevel.players()`，核心 tick 已有大量同类遍历先例，量级相同。

## Migration Plan

开发期变更：无生产存档。int→long 字段不做旧档读兼容（存档直接损坏可接受，重开世界）。回滚 = revert 整个 change，灵力核心物品无人引用自然消失。

## Open Questions

- 无阻塞项。默认注灵速率 100/s 为手感占位（用户授权"随意"），验收期调参。
