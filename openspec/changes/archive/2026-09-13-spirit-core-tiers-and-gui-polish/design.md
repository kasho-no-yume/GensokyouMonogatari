# Design: 灵力核心六阶化与 GUI 打磨

## Context

- `SpiritCoreItem` 已是定值构造范式（容量/速率入参），仪式侧只消费抽象与 `fillRatePerSecond()`（KagutsuchiFlameBehavior），六阶化对仪式/传输系统零改动。
- 品阶色单一色源 `TierPalette`（0灰/1绿/2蓝/3金/4红/5紫）已存在，ritual_stone 系列已用 `_N` 后缀 + 按阶贴图（非 tint）惯例。
- `RitualCoreScreen` 当前 200 宽：信息 8 类内容挤 150px，加具土命燃烧行靠 `patternId` 字符串特判，电池槽在 (174,44)，背包区底图无格子。
- `WeaponCoreScreen` 176 宽原版灰底图；三核槽 (62/98/134, 28)；`danmaku_weapon.png` 现状为不可辨识的糊状物。
- 贴图工具链 `tools/gen_tex.py`：ASCII 数据文件 + 调色板，一文件多张批量出图，预览→`--write-assets`。

## Goals / Non-Goals

**Goals:**

- 六阶灵力核心作为独立物品兑现"档位即物品"规格
- 仪式 GUI 信息渲染改为行为数据驱动（InfoLine），删特判
- 两个 GUI 底图重绘 + 武器图标重画，达到项目像素美感基线

**Non-Goals:**

- 高阶核心合成配方（后续立项）
- 信息区滚动/分页（保持现有截断策略）
- 槽背景武器图案随武器类型动态渲染（静态嵌底图）
- 旧 `spirit_core` 存档迁移（datafix）

## Decisions

### D1 六阶定值与注册方式

数值表（容量 ×12 / 速率 ×8）：

| 阶 | id | 容量 | 速率/s |
|---|---|---|---|
| 0 | `spirit_core_0` | 50,000 | 1,000 |
| 1 | `spirit_core_1` | 600,000 | 8,000 |
| 2 | `spirit_core_2` | 7,200,000 | 64,000 |
| 3 | `spirit_core_3` | 86,400,000 | 512,000 |
| 4 | `spirit_core_4` | 1,036,800,000 | 4,096,000 |
| 5 | `spirit_core_5` | 12,441,600,000 | 32,768,000 |

`ModItems` 删旧注册，循环注册 6 阶（仿 RITUAL_STONE 系），`SpiritCoreItem.DEFAULT_*` 常量改为表驱动或直接删（构造入参即真相）。创造栏按阶排列。tooltip：`灵力 stored/capacity`（AQUA 保留）+ 新增 `输入输出速率 X/s`（`TierPalette.textColor(tier)` 染色）。
- 备选（否决）：单物品+数据组件分档——违背"档位即物品"既有规格；tint 染色单贴图——与 ritual_stone 全贴图惯例不一致且灰阶基图染品阶色难以保证观感。

### D2 InfoLine 数据驱动信息区

`RitualInfoPayload` 增字段 `List<InfoLine> infoLines`。`InfoLine` record 描述性行：`{String textKey 或 literal, Object[] args, String iconItemId, int color, float progress /*-1=无*/, Boolean state /*null=无, true=✓, false=✗*/}`，StreamCodec 手写读写（与现文件风格一致）。

`RitualBehavior` 增钩子 `uiInfo(context) → List<InfoLine>`；提供基类默认实现：祭品清单行（图标+✓✗）与配方清单行（✓✗+名+缺料摘要）。加具土命 behavior 覆写：燃烧行（燃料图标+进度条+剩余秒）。`RitualCoreScreen.renderLabels` 删除 `KAGUTSUICHI` 特判，只画固定头 + 遍历 InfoLine（文本/图标/进度条/✓✗ 四种元素）。
- 备选（否决）：Screen 端按 patternId 分发表——每加仪式改客户端，违背数据驱动方向；纯文本行——丢失图标/进度条表达力。

### D3 仪式 GUI 布局（248 宽）

```
┌─ 248 ────────────────────────────────────┐
│ 仪式名                        [运行●]     │ 固定头 y=10..34
│ 阶级 N              灵力 X / Y            │
├──────────────────────┬───────────────────┤
│ 信息显示区            │ 电池槽(196,44)     │ 信息区 y≈40..150
│ InfoLine 逐行渲染     │ [操作?×3 竖排]     │ 右栏: 槽+按钮
│ (基类/行为产出)       │ [启动] [停止]      │
├──────────────────────┴───────────────────┤
│ ▭×9 主背包 3 行（格画进底图）              │ y=158 起
│ ▭×9 快捷栏                                │
└──────────────────────────────────────────┘
```

- 面板 248×250 级别；`RitualCoreMenu.BATTERY_SLOT_X/Y` 移到右栏，`INVENTORY_TOP_Y` 随信息区下缘调整，Screen/Menu 常量同步。
- 背包格画进底图（标准 18px 格）；信息区画带边框空面板。
- 按钮沿用现有 widget，坐标挪至右栏；动作按钮保持右栏竖排。

### D4 武器图标 = 弹幕铳

`danmaku_weapon.png` 重画为手枪轮廓（16×16）：枪管朝右上、握把左下，暖金机身+深紫描边+白色枪口高光，与 core_*.png 弹核系列同族。ASCII 数据文件先预览给用户过目再 `--write-assets`。

### D5 武器 GUI 底图 = 核心嵌武器

`weapon_core.png` 重画：保留原版灰面板语言（与背包格衔接自然），三核槽位 (62/98/134, 28) 背景各嵌一次半透明暗化的弹幕铳图案（同一图案灰化后按槽位平移复用），装核后核贴图叠于其上形成"核心装入武器"视觉。纯底图实现，Java 零改动。槽上方加"弹核 / 等级核 / 增幅核"小标注（语言键）。

### D6 贴图生产

一个数据文件 `tools/textures/spirit_cores.py` 出 6 张（共享调色板、按阶主色+阶数符文点强度递增、0 阶无彩）；`weapon.py` 出弹幕铳；两个 GUI 底图各一数据文件（248 宽大图 ASCII）。全部走 gen_tex.py 预览→确认→写 assets。

## Risks / Trade-offs

- [payload 编码变更致新旧不互通] → 单机 mod 无实际影响；实施时确保双端同 commit。
- [248 宽 GUI 低分辨率下偏大] → 与原版创造/合成簿大面板同级，可接受；截图确认。
- [InfoLine 描述力不足的边缘需求] → 预留 color/progress/state/icon 四元素已覆盖现有全部用例；后续新仪式有超纲需求时再扩展字段（record 加字段为破坏性变更，需同步 codec）。
- [删旧物品致创造栏/配方书残留引用] → 同 commit 清理 lang/模型/配方 JSON 与 JEI 缓存刷新。
- [大底图 ASCII 工作量] → GUI 底图允许程序化辅助（纯色域+规则格线），仅装饰细节用 ASCII 精画；gen_tex.py 支持混合。

## Migration Plan

单 commit 序列：注册/物品 → payload/behavior → GUI → 贴图 → lang 清理。回滚 = revert 单一 change。存档中旧 `spirit_core` 实例失效（已确认接受）。

## Open Questions

- 无（品阶数值/无配方/方案B/加宽/静态嵌图均已与用户确认）。
