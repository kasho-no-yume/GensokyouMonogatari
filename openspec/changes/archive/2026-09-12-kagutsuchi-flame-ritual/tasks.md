# Tasks: kagutsuchi-flame-ritual

## 1. API 先验（skill 验证工作流，先于一切编码）

- [x] 1.1 从 sources jar grep 确认 1.21.1 燃料时长入口：`stack.getBurnTime(null)`（`IItemStackExtension`，燃料表由 `FURNACE_FUELS` 数据映射驱动，含 `#logs`），单点封装于 `KagutsuchiFlameBehavior.burnTicksOf`
- [x] 1.2 确认容器残留入口 `stack.hasCraftingRemainingItem()`/`getCraftingRemainingItem()`（原版熔炉烧熔岩桶留铁桶即此路径，照抄语义）
- [x] 1.3 确认 `ParticleTypes.FLAME` + `sendParticles` 12 参重载（仓内三处同调用先例）；`addDataSlot(DataSlot)` protected 返回 DataSlot；`SlotItemHandler.set` 强转 `IItemHandlerModifiable`

## 2. 仪式侧灵力 long 化（BREAKING，纯拓宽签名）

- [x] 2.1 `RitualCoreBlockEntity`：`storedSpiritPower` → long，`receive/extract(long)`、`getCapacity()` 返回 long，NBT `putLong/getLong`（不做 int 旧档兼容）；加载不再按容量夹取（activeMatch 未就位会误截，超储走"生产为 0 + extract 自愈"）
- [x] 2.2 `RitualCoreBlockEntity.getCapacity()` 按 activeMatch 分派：CAPACITOR 读配置，KAGUTSUICHI 走 `BASE_CAPACITY × 10^level`，其余图案维持电容配置值
- [x] 2.3 `SpiritStorageBlockEntity` stored/capacity/receive/extract 同步 long 化
- [x] 2.4 `RitualInfoPayload` stored/capacity 改 `writeLong`/`readLong`（写读对称）
- [x] 2.5 编译驱动适配全部调用方（Capacitor/Generator/Relay 局部改 long，玩家池边界显式窄化 cast）；`gradlew.bat compileJava` 通过

## 3. 灵力核心物品（spirit-core-item）

- [x] 3.1 `ModDataComponents` 注册 `SPIRIT_CORE_POWER` 组件：record `SpiritCoreData(long stored)` + Codec（缺省视为空核）+ VAR_LONG 流编解码
- [x] 3.2 `spirit/SpiritCoreItem`：构造定值 `{capacity=30000L, fillRatePerSecond=100}`，maxStackSize=1，静态 long 存取原语 receive/extract/getStored（直读组件，供仪式与未来存储系统复用）
- [x] 3.3 `ModItems` 注册 + 创造栏收录 + zh/en 语言键 + tooltip 显示"已存/容量"
- [x] 3.4 占位合成配方 JSON（`data/gensokyou/recipe/spirit_core.json`：紫水晶+红石+荧石粉shapeless）+ item model + gen_tex 贴图

## 4. 核心 BE：电池槽与批次态

- [x] 4.1 `RitualCoreBlockEntity` 增 `batteryStack`：NBT 持久化 + `BatteryHandler`（实现 `IItemHandlerModifiable`，仅收 `SpiritCoreItem`）；`RitualCoreBlock.onRemove` 掉落电池（含组件）
- [x] 4.2 批次态字段：`burnFuelIcon`（copyWithCount(1) 序列化）、`burnTotalTicks`/`burnRemainingTicks`、`rateCarry`/`fillCarry`（千分定点进位）；NBT 持久化 + `beginBurnBatch/advanceBurnTick/clearBurnBatch`
- [x] 4.3 结构失效批次作废：`KagutsuchiFlameBehavior.onStructureLost` → `clearBurnBatch`（已吞燃料不返还）

## 5. KagutsuchiFlameBehavior 状态机

- [x] 5.1 行为骨架 + `RitualBehaviors` 注册（`KAGUTSUICHI` 常量）+ `kagutsuchi_flame_circle.json` 补 `"toggleable": true`
- [x] 5.2 `GensokyouConfig`：`KAGUTSUICHI_BASE_RATE_PER_SECOND=20`、`KAGUTSUICHI_BASE_CAPACITY=1000`、`KAGUTSUICHI_FUEL_BLACKLIST`（默认空，`defineListAllowEmpty`）
- [x] 5.3 燃料选取：规范序扫 `P` 台 → `burnTicksOf>0 && !黑名单` → 点火即吞（销毁/容器残留落回原台）；判定单点封装
- [x] 5.4 `serverTick` 主循环：批推进（归零当 tick 清批→选批→成功即无缝衔接）→ 每秒结算（产灵 `20×4^L/s` receive 截断=空烧作废；注灵 min(电池速率配额, 缓存, 电池空余)）→ 缓存满不选批=停等，回落即续
- [x] 5.5 停机=批次冻结（enabled 门控天然保留 BE 态）、重启续烧；点火与每秒结算向正打开本界面的玩家重推 payload（`level.players()` + containerMenu pos 匹配）
- [x] 5.6 火焰粒子：BURNING（含空烧）时 `interval = max(2, 6-level)`、count `2+level` 于核心上方 sendParticles；停等/待机零粒子

## 6. 菜单与界面扩展

- [x] 6.1 `RitualInfoPayload` 增 `fuelItem`（物品 ID 字符串，燃烧中非空）纳入 snapshot 组装；write/read 对称
- [x] 6.2 `RitualCoreMenu`：`BatterySlot`（extends `SlotItemHandler`，可切可见性，服务端接 BE handler/客户端占位 dummy，槽数两端对称）+ `addDataSlot`×2（remaining/total，持引用绕过私有 dataSlots）
- [x] 6.3 `RitualCoreScreen`：燃烧行（燃料图标 + 96px 进度条 + "剩余 N 秒"向上取整）；非燃烧时按 stored≥capacity 显示停等/待机文本；槽位标注文本；非加具土命仪式 `setShown(false)` 隐藏电池槽
- [x] 6.4 首帧验证路径：openOrHint 既推 snapshot（fuelItem 随带），DataSlot 初值随菜单打开同步；客户端槽位初始 hidden、payload 到达后 containerTick 收敛

## 7. 验证与回归

- [x] 7.1 `gradlew.bat compileJava` 零错；`runServer` 启动日志 `Done (1.442s)!` 且无 `Errors in registry`
- [ ] 7.2 runClient 手测清单：0 阶搭建→放原木/木板/煤块/熔岩桶混台→点火即吞与空桶残留→速率 20/s 与换批无断档（粒子连续）→无电池烧满缓存→空烧至批末→停等→插电池自动续火→倒计时逐 tick 递减与换批刷新→停机再启批次冻结续烧→拆核心掉电池→重启世界批次态保持
- [ ] 7.3 高阶回归：1-3 阶结构下速率 ×4/缓存 ×10 抽查（L2 起 100/s 默认电池节流生效、缓存触顶停等链路）
- [ ] 7.4 既有仪式回归：电容存取/发电机推送/中继/炼体/结界环 GUI 与 tick 行为不因 long 化回归（重点 payload 数值显示；其余仪式界面确认电池槽不可见）
