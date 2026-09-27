## 1. 仪式注册与配置

- [x] 1.1 核对并纳入 `houjouno_teihou_circle.json` 的 0–2 阶、4/8/12 祭品台与 `toggleable:true` 结构，确认无 requirements 和通用 passive/activation 配方依赖
- [x] 1.2 在 COMMON 配置中增加独立的缓存基值/倍率、受灵基值/倍率、单台成本基值/倍率、单台采样基值/倍率和周期 tick 配置，默认值分别为 40000/12、40000/12、4000/4、1/4、1200
- [x] 1.3 在 `RitualBehaviors` 注册 `gensokyou:houjouno_teihou_circle` 的专用行为实例
- [x] 1.4 在核心缓存容量分派中加入丰穰神容量公式，并让行为按当前 `RitualMatch.level()` 声明受灵速率、保持供灵速率为 0
- [x] 1.5 提取可复用的饱和乘法与阶数公式，确保容量、受灵、成本、采样数在极值配置下不溢出

## 2. 作物收获适配层

- [x] 2.1 定义收获上下文、单次采样 provider 和输出收集器接口，约束 provider 不得修改世界、祭品台或其他持久状态
- [x] 2.2 实现运行时 provider 注册表，支持按物品或资源标识注册、显式覆盖标准自动识别、注销/重载安全和按台异常隔离
- [x] 2.3 实现标准 `BlockItem + CropBlock + 克隆身份` 自动识别、成熟状态构造和空工具 `BlockState#getDrops` 采样
- [x] 2.4 为西瓜和南瓜注册整果适配器，每次采样分别固定产出一个 `minecraft:melon` 与一个 `minecraft:pumpkin`
- [x] 2.5 为瓶子草、下界疣、甜浆果和可可注册成熟状态型原版适配器
- [x] 2.6 为甘蔗和仙人掌注册固定单件适配器，每次采样各产出一个 `minecraft:sugar_cane` 或 `minecraft:cactus`
- [x] 2.7 明确排除树苗、树木、蘑菇、装饰植物、成熟成果方块和无生长阶段的幻想乡植物，避免仅凭 `BlockItem` 或 `AGE` 误判
- [x] 2.8 添加纯逻辑测试覆盖标准作物识别、成熟作物方块拒绝、显式 provider 优先、全部原版特例和 provider 异常隔离

## 3. 周期结算与灵力

- [x] 3.1 实现结构内祭品台扫描，将每个正确种子或农业繁殖材料台位解析为独立有效单元，并保持原输入不变
- [x] 3.2 实现每台 `1 × 4^level` 次独立采样，将每次完整 `ItemStack` 输出收入临时批次，并隔离不支持或异常台位
- [x] 3.3 实现 `canCover` 后单次 `payCost` 的三段式原子支付，总成本按最终有效台位数计算；失败时丢弃整批并保持 enabled 与 cooldown
- [x] 3.4 在成功支付并提交产物后设置配置周期，冷却期间跳过扫描；欠费、缺料和空转不得设置成功冷却
- [x] 3.5 实现按物品与完整数据组件等价聚合、饱和累计、最大堆叠拆分和 `RitualOutputs` 空投
- [x] 3.6 在 `serverTick` 接入生产结算，在 `serverPassiveTick` 接入不受 enabled 门控的槽内电池到缓存补料
- [x] 3.7 验证手动停止、结构失效、升级和降阶路径：仅前两者关闭 enabled，升级降阶不清 cooldown，下一次结算读取当前等级
- [x] 3.8 添加结算测试覆盖四个同种种子按四份计费、混合有效/无效台位、0/1/2 阶 1/4/16 次采样、192 次满台上限和随机空 loot 仍计成本

## 4. GUI 与调试

- [x] 4.1 实现丰穰神 `uiInfo`，显示运行/手动停止、冷却剩余、占用台位、有效/无效台位、单位成本、总成本与采样次数
- [x] 4.2 按 GUI 宽度规范使用短标签、紧凑大数与 tooltip 细节，确保缺灵待机仍显示为 enabled 状态而非未启动
- [x] 4.3 在 GUI 打开期间通过现有快照心跳同步外部充能、祭品台变化和冷却，不新增客户端 patternId 特判
- [x] 4.4 增加 `/gs_debug` 丰穰神子命令，输出 level、enabled、cooldown、台位统计、单位/总成本、采样数、stored/capacity 和适配失败数的单行摘要
- [x] 4.5 添加结算成功、缺料、欠费、适配失败、冷却和手动停止状态的 GUI/调试快照测试

## 5. 语言与指导书

- [x] 5.1 审计并补齐丰穰神 GUI 状态、tooltip、调试与指导书所需的 `zh_cn` 语言键，英文缺失不得造成中文裸键
- [x] 5.2 将 `houjouno_teihou_circle` 加入指导书条目生成器，生成 0–2 阶结构页、排序、图标和最低阶 0 的常驻入口
- [x] 5.3 扩展 `RitualTierComponent` 的参数分支，从同一配置公式显示容量、受灵、单位成本、总成本、采样数和冷却
- [x] 5.4 增加简短故事/引言及农业兼容说明，列出标准自动作物、八项原版特例和非标准 mod 适配要求，不创建独立 JEI 配方页

## 6. 验证与回归

- [x] 6.1 为成本、容量、受灵、采样和饱和运算添加配置边界测试
- [x] 6.2 为完整组件聚合、不同组件隔离、超最大堆叠拆分和空输出提交添加测试
- [ ] 6.3 使用 GameTest 或调试 harness 验证启动首产、成功后 1200 tick 冷却、停止继续受灵、欠费保持 enabled、恢复后自动结算和结构失效停机
- [x] 6.4 用标准 `CropBlock` 测试夹具与一个显式 provider 测试夹具验证 mod 兼容接口；确认未注册非标准作物保持无效且不抛错
- [x] 6.5 对 2 阶 12 台执行 192 次小麦采样压测，记录批次耗时、聚合后栈数和最坏组件差异场景
- [x] 6.6 运行 `python tools/validate_ritual_pattern.py --test-out run/world/datapacks/gs_ritual_test` 并确认 0 ERROR、0 WARN
- [x] 6.7 运行 `python tools/lang_audit.py`、相关 JUnit/GameTest 与 `gradlew compileJava`，确认全部通过
- [x] 6.8 运行 `openspec validate add-houjouno-teihou-ritual --strict`
- [ ] 6.9 由用户运行 `powershell -ExecutionPolicy Bypass -File tools\_run_ritual_test.ps1`，通过 `/gs_debug` 验证 0/1/2 阶实机产出、停机受灵与一分钟周期
