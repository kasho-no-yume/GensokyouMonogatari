# Tasks: add-sair-energy-ritual

## 1. Pattern 与离线校验

- [x] 1.1 新建 `data/gensokyou/rituals/sair_energy_circle.json`：`id=gensokyou:sair_energy_circle`、`anchorKey=C`、`tiers=[0]`、palette 含 `C=gensokyou:ritual_core` 与 `B=minecraft:bedrock`；单 level 0，adds = `["C",0,0,0]`、`["B",0,0,1]`、`["B",1,0,1]`（不写 `toggleable`、不写 `requirements`）
- [x] 1.2 运行 `python tools/validate_ritual_pattern.py --test-out run/world/datapacks/gs_ritual_test`：本 pattern 0 ERROR、0 WARN（重点核劫持/锚点/重复展开/品阶下限）

## 2. 行为与注册

- [x] 2.1 新建 `ritual/behavior/SairEnergyBehavior.java`（`implements RitualBehavior`）：覆写 `spiritOutRatePerSecond` 返回 `GensokyouConfig.SAIR_ENERGY_OUT_RATE_PER_SECOND`；覆写 `serverPassiveTick` 每 20 tick 用普通 `receive` 补满缓存（`gap = cap - core.getStored()`）
- [x] 2.2 覆写 `uiInfo`：状态行（stored / cap / out，短标签 + `InfoLine.tipped` 明细 / `InfoLine.compact`）+ 由来诗 lore 行；不改 `usesCoreSocket`（保持默认）
- [x] 2.3 `ritual/RitualBehaviors.java`：新增常量 `SAIR_ENERGY = Gensokyou.id("sair_energy_circle")` 并在静态块 `register(SAIR_ENERGY, new SairEnergyBehavior())`

## 3. 核心缓存分派与配置

- [x] 3.1 `RitualCoreBlockEntity.getCapacity()`：新增 `activeMatch.patternId().equals(RitualBehaviors.SAIR_ENERGY)` 分支，返回 `GensokyouConfig.SAIR_ENERGY_BASE_CAPACITY.get()`
- [x] 3.2 `GensokyouConfig`：声明并 `defineInRange` 两项——`SAIR_ENERGY_OUT_RATE_PER_SECOND`（默认 `1_000_000_000`）与 `SAIR_ENERGY_BASE_CAPACITY`（默认 `10_000_000_000L`，**`LongValue`**），置于文件合适分组

## 4. 本地化与调试

- [x] 4.1 `zh_cn.json`/`en_us.json`：补 `jei.gensokyou.ritual.sair_energy_circle`（赛尔能源 / Sair Energy）、状态行键与 `gui.gensokyou.ritual.sair_energy.lore_1..5`
- [x] 4.2 `python tools/lang_audit.py` 退出码 0
- [x] 4.3 （可选）`ritual/command/DebugCommands.java`：新增 `sair` 子命令，输出机读单行（stored/capacity/outRate/hit）

## 5. 验证

- [x] 5.1 `gradlew compileJava` 通过
- [ ] 5.2 `gradlew runServer --console=plain`：出现 `Done (`、无 `Errors in registry`（agent 不启动服务器，由用户执行）
- [ ] 5.3 实机：铺核心 + 八邻基岩成型；界面显示缓存恒 100 亿、供灵 10 亿/s；拆任一基岩即失效（用户实机）
- [ ] 5.4 路由联调：与万象共鸣建链，确认该仪式出现在源候选、抽取后缓存下周期恢复满额、实际传输受汇端截断（用户实机）
- [x] 5.5 复核长整型无溢出：`getStored`/`getCapacity`/`receive` 均为 long；`1e10` 与 `1e9` 远低于 `Long.MAX_VALUE`（9.2e18），端点账本 `速率×周期/20` 亦为 long
