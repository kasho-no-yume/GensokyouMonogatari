## 1. 数值与配置

- [x] 1.1 GensokyouConfig 新增 `TSUKUMOGAMI_BASE_RATE_PER_SECOND`(50)、`TSUKUMOGAMI_BASE_OUT_RATE_PER_SECOND`(50)、`TSUKUMOGAMI_BASE_CAPACITY`(400000)、`TSUKUMOGAMI_LEVEL_MULT`(5)
- [x] 1.2 燃料表「itemId→SP」配置列表：20 陶片点位 7000~20000、16 唱片点位 15000~45000，剔除模板/decorated_pot
- [x] 1.3 燃料校验走燃料表本身（不需要独立标签）

## 2. 行为内核

- [x] 2.1 新建 `TsukumogamiState implements RitualBehaviorState`：每槽 remainingTicks / fuelIcon / totalTicks
- [x] 2.2 新建 `TsukumogamiBehavior`：并行点火即吞、50×5^L/s 入账、空烧/停等、槽核注灵、`refillsCacheFromSocket=false`、`spiritOutRatePerSecond` 独立声明
- [x] 2.3 `RitualBehaviors` 注册、`RitualCoreBlockEntity` per-core state（框架已支持）、`onStructureLost` 作废不返还
- [x] 2.4 GUI `uiInfo`：并行多槽计时行、缓存上限、等级、燃料图标

## 3. 渲染态与客户端 FX

- [x] 3.1 `RitualRenderState` 新增 `KIND_TSUKUMOGAMI` + 燃烧位掩码字段；客户端据 pattern 本地推导台位
- [x] 3.2 `RitualCoreRenderer` 新增烟柱分支：暗色 large_smoke billboard 烟片 + alpha、随阶级加密/加高，启停淡入淡出
- [x] 3.3 服务端不引入任何粒子包调用（核对 1Hz 快照字段完整）

## 4. 工具链

- [x] 4.1 `/gs_debug` 探针输出 level/燃烧槽数/缓存/出率摘要
- [x] 4.2 JEI 燃料表页签（`TsukumogamiFuelCategory`，按 watatsumi 先例/三张 0/1/2）+ 指导书条目（`gen_ritual_book_entries.py`）
- [x] 4.3 lang 键全套（启停/空烧/停等/无料/槽位/等级）

## 5. 验证

- [x] 5.1 compileJava 通过
- [x] 5.2 runGameTestServer 单元测并行烧/空烧/停等/多槽入账
- [x] 5.3 实机：阶级 0/1/2 烟柱密度、无服务端粒子包、GUI 计时逐 tick
