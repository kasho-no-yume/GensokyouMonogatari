# Tasks: 灵力核心六阶化与 GUI 打磨

## 1. 六阶灵力核心（Java 侧）

- [x] 1.1 `SpiritCoreItem`：删除 `DEFAULT_CAPACITY/DEFAULT_FILL_RATE_PER_SECOND` 常量；tooltip 增加输入输出速率行（`TierPalette.textColor(tier)` 染色；tier 需可感知——构造入参加 tier 或按容量推断，取简单者）
- [x] 1.2 `ModItems`：删除 `spirit_core` 注册与 `SPIRIT_CORE` 字段引用清理；循环注册 `spirit_core_0..5`（容量 50000×12ⁿ / 速率 1000×8ⁿ），保留 `SPIRIT_CORES` 集合便于创造栏与引用
- [x] 1.3 `ModCreativeTabs`：按 0→5 阶排列收录六物品
- [x] 1.4 语言文件：`item.gensokyou.spirit_core_0..5` 中英（灵力核心 0~5 阶 / Spirit Core T0~T5）、tooltip 速率行键 `tooltip.gensokyou.spirit_core_rate`；删除旧 `spirit_core` 键
- [x] 1.5 资源清理：删除 `models/item/spirit_core.json`、`data/gensokyou/recipe/spirit_core.json`；新增六个 `models/item/spirit_core_N.json`
- [x] 1.6 全仓引用检查：`grep SPIRIT_CORE|spirit_core` 清理残留（KagutsuchiFlameBehavior 等消费 `SpiritCoreItem` 抽象处应零改动，仅注册引用受影响）

## 2. 仪式信息行通道（payload + behavior）

- [x] 2.1 新建 `InfoLine` record（textKey/args/iconItemId/color/progress/state）与 StreamCodec 手写读写
- [x] 2.2 `RitualInfoPayload` 增 `List<InfoLine> infoLines` 字段并接入编解码与 `snapshot()` 组装链
- [x] 2.3 `RitualBehavior` 增 `uiInfo(...)` 钩子（返回 `List<InfoLine>`）；基类默认实现产出祭品清单行（图标+✓✗）与配方清单行（✓✗+名+缺料摘要）
- [x] 2.4 `KagutsuchiFlameBehavior` 覆写 `uiInfo`：燃烧行（燃料图标+进度条+剩余秒）/停机提示行；删除数据通道中该仪式的特判依据
- [x] 2.5 各既有 behavior 检查：确认走基类默认实现即可，无需逐个改动

## 3. 仪式 GUI 重排（248 宽）

- [x] 3.1 `RitualCoreMenu`：`BATTERY_SLOT_X/Y` 挪至右栏、`INVENTORY_TOP_Y` 按新区块调整（信息区下缘 ≈150）
- [x] 3.2 `RitualCoreScreen`：布局重排——固定头（仪式名/阶级/状态/灵力）+ 右栏（槽/启停/操作按钮坐标）+ 信息区逐行渲染 InfoLine（文本/图标/进度条/✓✗）；删除 `KAGUTSUICHI` 特判与 `renderBurnRow`
- [x] 3.3 语言文件：固定头新键（若有）与三操作按钮既有键核对
- [x] 3.4 编译验证 + 开界面对照设计图自查（信息区/右栏/背包区三分不重叠）

## 4. 贴图生产（gen_tex.py）

- [x] 4.1 `tools/textures/spirit_cores.py`：六阶核心一文件多张（共享调色板，品阶主色+纹样强度递增，0 阶无彩）；预览给用户过目
- [x] 4.2 `tools/textures/danmaku_weapon.py`：弹幕铳造型（暖金机身+深紫描边+枪口高光）；预览给用户过目
- [x] 4.3 `tools/textures/ritual_core_gui.py`：248 宽仪式底图（固定头分隔线/信息区边框面板/右栏槽框/背包 4 行格线）
- [x] 4.4 `tools/textures/weapon_core_gui.py`：武器主题面板底图（三槽嵌半透明暗化弹幕铳图案+槽位框+背包格）
- [x] 4.5 用户确认后 `--write-assets` 全部写入；删除旧 `textures/item/spirit_core.png`

## 5. 武器 GUI 重绘接线

- [x] 5.1 `WeaponCoreScreen`：面板尺寸/底图路径按新底图核对；三槽上方槽位类型小标注（弹核/等级核/增幅核，语言键中英）
- [ ] 5.2 实机截图验证两个 GUI（仪式面板 + 武器面板），交用户验收；不达标回步骤 4 迭代

## 6. 收尾

- [x] 6.1 `gradlew compileJava` 通过；创造栏/JEI 中旧 spirit_core 无残留、六阶核心齐全
- [x] 6.2 中英语言文件 JSON 合法性核对（新增键双侧齐备）
