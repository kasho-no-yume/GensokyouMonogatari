## 1. 数据定义

- [x] 1.1 从 `sheet/achieve.xlsx` 导出 14 条 advancement JSON，放入 `src/main/resources/data/gensokyou/advancement/achievement/`
- [x] 1.2 为每条成就补齐 `display`（icon/frame/toast）与 `criteria`
- [x] 1.3 里程碑 3 条（君权神授、妖怪退治、梦想崩坏）使用 `frame: "challenge"`
- [x] 1.4 补充 `assets/gensokyou/lang` 中的成就名/描述，中英双语

## 2. 触发实现

- [x] 2.1 物品获得类：首次获得残页/仪式核心/灵铁/弹幕铳走 `minecraft:inventory_changed`
- [x] 2.2 进入幻想乡：直接复用 `changed_dimension` trigger
- [x] 2.3 击败大妖精：`minecraft:player_killed_entity` 判定 `gensokyou:big_fairy`
- [x] 2.4 神恩提升至 1 阶：在 `GraceService.advance` 结算处 award
- [x] 2.5 众生余录：`CodexOfBeingsItem` 收满后 hook + tick 扫描兜底
- [x] 2.6 药水：`SunakoBehavior` 3 阶成功产出 award
- [x] 2.7 仪式类：无尽藏、万象共鸣、八方归元在 `RitualCoreBlockEntity.start` 成功后 award；梦渡之座、附魔仪式在各自结算处 award

## 3. 校验与调试

- [x] 3.1 新增 `gs_ach give/has` 调试命令
- [x] 3.2 编译通过
- [x] 3.3 challenge frame 使用原版 `display.frame = "challenge"`
