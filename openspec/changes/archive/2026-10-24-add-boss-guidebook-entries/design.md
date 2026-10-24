## Context

指导书是 Patchouli 数据驱动（`book.json` + `en_us/entries` + `zh_cn.json`），现有「敌对生物列表」只有 `monsters_fairies` 一条。四只可召唤 BOSS 已经落地，但书里没有它们的条目；同时也没有任何规范把「补条目」和新 BOSS 开发绑在一起。

BOSS 侧已有的可复用事实：

| 实体 | `bossTier()` | 主题信物 | 召唤配方 |
|---|---|---|---|
| `big_fairy` | 1 | `fairy_nectar` | `gensokyou:hyakki_boss_big_fairy` |
| `yamame` | 1 | `earth_spider_silk` | `gensokyou:hyakki_boss_yamame` |
| `rumia` | 1 | `nightfall_core` | `gensokyou:hyakki_boss_rumia` |
| `yakumo_ran` | 2 | `sukima_fragment` | `gensokyou:hyakki_boss_yakumo_ran` |

Patchouli 版本 1.21.1-93 的三个相关事实（已读源码确认）：

1. `BookIcon.from()` 对以 `.png` 结尾的字符串走 `TextureIcon`（贴图图标），对物品 id 走 `StackIcon`——图标两种都支持。
2. 条目排序是「locked → 显示状态（UNREAD 最前）→ priority → sortnum」，`sortnum` 不是第一优先级。
3. `secret` 条目解锁后仍走 `0xAA000000 | textColor` 的淡化色，`entry_color` 对它无效。

## Goals / Non-Goals

**Goals:**

- 为四只现有 BOSS 补齐条目，并把「新 BOSS 必须补条目」写进规范与开发清单。
- 让排序、门槛、页型、图标都有可静态校验的形态，而不是只靠口头约定。
- 修正残魂典籍词条「只显示一条配方」的误导，同时不提前泄露 T2 信物。
- 让「没有召唤配方」的 BOSS（任务 / 结构遭遇）在规范与审计里都是**合法形态**。

**Non-Goals:**

- 不改任何 BOSS 数值、符卡、召唤配方或 Java 逻辑。
- 不新增章节：BOSS 条目仍在「敌对生物列表」内。
- 不引入「击败后解锁」的击杀型 advancement（当前门槛只按世界进度）。
- 不为芙兰朵露 / 无面 / 琪露诺建条目——它们当前没有正常遭遇通路。

## Decisions

### D1 门槛直接复用 `worldTier` 的语义

`GuideTierProgress.worldTier()` 已经把 1/2/3 定义为进下界/末地/幻想乡，4/5 为过渡门槛。条目门槛按 `bossTier()` 一一映射，不新增数据字段、不引入新的触发器。

**替代方案**：为每只 BOSS 单独写一个 advancement。否决——同一阶级的 BOSS 会重复维护，且与既有仪式条目的口径分叉。

### D2 排序契约：`sortnum = 100 × tier + n`

普通怪占 `0–99`，T1 占 `100–199`、T2 占 `200–299`，依此类推；同阶按推进顺序递增。四只现状：大妖精 101、黑谷山女 102、露米娅 103、八云蓝 201。

**接受**未读条目被置顶：这是 Patchouli 对全书一致的行为，规范里明确写「不算违规」。

**替代方案 A**：`read_by_default: true` 钉死顺序。否决——会让 BOSS 条目失去「新」标记，且与全书其它条目行为不一致。
**替代方案 B**：BOSS 单开子分类（`parent` 字段）。否决——用户要求条目留在同一列表里，且会多一次点击。

### D3 「区分」靠信物图标 + 顺序成块 + 分类描述

- 所有普通怪用物品图标；BOSS 用各自的主题信物图标，配合顺序成块仍能分辨。
- 分类描述补一句「末尾另录那些要摆开仪式、或要走到某处才见得到的妖异」。
- 不使用 `entry_color`：`secret` 条目解锁后固定走淡化色，配了也不生效（源码 `GuiButtonEntry#getColor`）。
- 不在名字上加「BOSS」前缀：条目名复用实体语言键，保证与血条/实体名一致。

### D4 三页骨架：模型单独一页，信物并入抵达页

```
P1 patchouli:text        引言
P2 patchouli:entity      3D 模型（旋转展示，不配文字）
P3 gensokyou:boss_arrival  抵达 + 信物（同一页，见 D5/D6）
```

用户明确不要描述长相与信物，故 P2 只放模型；P3 只放配方卡与信物图标，两者都不配描述文字。

**坑**：`PageEntity.name` 是**不过 i18n 的字面量**，填了会直接显示键名；留空则自动使用实体的本地化名。

**风险与回落**：GeckoLib 实体在书里渲染尚未实机验证。若不成立，P2 改为 `patchouli:image`（预渲染 PNG），其余不变。

### D5 抵达页三种合法形态，校验只查「不说谎」

```
仪式召唤 → gensokyou:boss_arrival（配方卡 + 信物）
任务授予 → boss_arrival_text（文字 + 信物）或 patchouli:text + 信物另页
结构遭遇 → 同上
```

仪式型 SHALL 用 `"recipe": "gensokyou:hyakki_boss_big_fairy"` 按 **id** 绑定；禁用 `recipe_index`——配方数组重排会让 index 静默指错人。配方卡照常显示物品、灵力消耗与结构阶（用户确认灵力是配方的一部分）。

非仪式型不写配方，审计 MUST NOT 要求配方存在。抵达页统一挂 `"anchor": "arrival"`，让审计有稳定的检查点，也方便其它页面深链。

### D6 信物并入抵达页，靠模板组合实现，不加 Java

Patchouli 的模板可以同时挂多个组件，且模板组件字段支持 `#variable#` 透传：

```json
// templates/boss_arrival.json
{
  "processor": "com.bitsson.gensokyou.client.book.RitualPageProcessor",
  "components": [
    { "type": "patchouli:custom",
      "class": "com.bitsson.gensokyou.client.book.RitualPageComponent",
      "x": 0, "y": 0 },
    { "type": "patchouli:item", "item": "#token#", "x": 50, "y": 128, "framed": true }
  ]
}
```

条目页只需：

```json
{ "type": "gensokyou:boss_arrival",
  "ritual": "gensokyou:hyakki_yagyo_circle",
  "recipe": "gensokyou:hyakki_boss_big_fairy",
  "token": "gensokyou:fairy_nectar",
  "anchor": "arrival" }
```

`patchouli:item` 是内置模板组件（`ComponentItemStack`），`item` 是 `IVariable`，因此 `#token#` 会经 `RitualPageProcessor` 透传解析。**不需要改 Java**。

无配方型若需要「文字 + 信物」同页，再补一个 `boss_arrival_text` 模板（`patchouli:text` + `patchouli:item`），同样零 Java。当前四只都走仪式型，先只交付 `boss_arrival`。

**取舍**：信物以带框的物品槽呈现、无标签——悬停即显名称，与配方格里的物品一致。若实机觉得语义不明，再加一个两字标签（属于文案，不是描述）。

反向链接（残魂典籍 → BOSS 条目）由 D8 承担；BOSS 条目不链镇魂，避免提前揭示回收链。

### D7 图标：直接用主题信物物品 id

```
entry.icon = "gensokyou:fairy_nectar"    // 花精之滴
             "gensokyou:earth_spider_silk"
             "gensokyou:nightfall_core"
             "gensokyou:sukima_fragment"
```

**为什么不用模型头像**：先做过一版从 `geo` 的 `head.north` 面裁 16×16 的管线，实看后放弃——模型本身仍是占位水平，缩到 16×16 只剩一张脸，观感比物品图标差得多；八云蓝的 32×32 脸缩一半后更糊。信物图标既认得出来，又与抵达页展示的信物一致。

**无信物时**：用该 BOSS 的代表物品作图标（例如主题符卡道具）。

审计据此断言 `entry.icon == boss.signatureItem()`。

### D8 残魂典籍词条：一信物一页

```
P1 spotlight  残魂典籍 + 通用说明（保留）
P2 crafting   花精之滴   title=item.gensokyou.fairy_nectar        advancement=nether
P3 crafting   土蜘蛛之丝  title=item.gensokyou.earth_spider_silk    advancement=nether
P4 crafting   宵暗之核   title=item.gensokyou.nightfall_core      advancement=nether
P5 crafting   隙间碎片   title=item.gensokyou.sukima_fragment      advancement=end
```

- `PageDoubleRecipe` 的 `title` 走 i18n，用信物名替代默认的产物名，四页才分得清。
- 每页 `text` 链回所属 BOSS 条目（`$(l:gensokyou:boss_big_fairy)`）。
- 页面级 `advancement` 是本书既有用法（`material_earth` / `material_ores` 都这么分页），无需 Java。
- 这样既修掉「只能用花精之滴」的暗示，又不提前暴露 T2 信物。

### D9 静态守卫：只认「当前可遭遇集合」

`tools/audit_boss_book_entries.py`：

- **发现源**：`SummonBossEffects` 的 `register("hyakki:<id>", ModEntityTypes.<CONST>)` → 经 `ModEntityTypes` 解析出实体 id。
- **阶级**：从实体类的 `bossTier()` 正则读取，不建平行表；解析失败就报错，逼作者写常量。
- **断言**：条目存在；`advancement` 与阶级映射一致；`sortnum` 落在 `[100×tier, 100×tier+99]`；`icon` 资源存在；存在 `anchor: "arrival"` 页；若为 `ritual_page` 则 `recipe` 存在且 `effect` 指向本实体；若为 `text` 则不要求配方。
- **绝不做的事**：要求每个 BOSS 都有召唤配方。

未来 BOSS 改由幻想乡任务/结构承载时，只需在「发现源」里加一处（例如结构刷新表），断言不用改。

### D10 文案：从官方设定集改编

- 只维护 `zh_cn.json`（既有规范）。
- 引言以故事开篇，事实骨架取自官方设定集，不与官方冲突。
- **大妖精没有官方个人条目**（红魔乡二面中 Boss），只使用《求闻史纪》「妖精」一族的通则，不编个人经历。
- 我们的实体是「八云蓝残影」，官方资料写的是本体：官方那些九尾/式神/算学/油豆腐全部放在**本体传闻**位置，残影用「不记事只记术」承接。

**文案基线（引言 p1）：**

- 大妖精：「妖精是自然的具现……」（自然的具现、不会真死、不爱战斗、这一只没有散）
- 黑谷山女：「黑暗的风穴里垂着不该垂在那里的线……」（土蜘蛛、一瞬结网、操纵传染病、被厌恶却在地底有人气、擅长土木、夜里接活）
- 露米娅：「露米娅总是把自己裹在一团黑暗里……」（魔法之暗、她自己看不见、撞树、妖怪中的弱者、红缎带是符纸、新月露本体、会吃人）
- 八云蓝残影：「妖兽的尾巴越多，魔力越高……」（九尾、紫的式神、代巡境界、算学、残影只记术、本体会到人里买油豆腐）

### D11 配方卡原料折行

`RitualPageComponent` 原来把原料画在一行、最多 9 个；百鬼夜行 L2 有 8 个祭品，第 8 个横向出框。

改为按页宽折行：`perRow = 116 / 16 = 7`，第二行起下移 18px，卡片高度按行数增长。只影响排版，不改配方数据、同步与签名。

## Risks / Trade-offs

- **GeckoLib 在书里渲染不成立** → 实机打开一次验证；失败则外观页降级为预渲染 `patchouli:image`，条目不重排。
- **32×32 脸缩到 16×16 辨识度下降（八云蓝）** → 已放弃头像方案，改用信物图标，该风险消失。
- **审计正则绑在 `ModEntityTypes` 的写法上** → 解析失败时输出明确报错（指出期望的写法），而不是静默跳过。
- **未读置顶会让刚解锁的 BOSS 条目前置** → 已确认为预期行为并写进规范；不额外处理。
- **百鬼夜行停止承载新 BOSS 后发现源失效** → 审计发现源按时代扩展；BOSS 条目本身与配方解耦，不受影响。
- **删除条目会留下死链**（残魂典籍页、页面内链） → 审计检查链接目标存在。
- **抵达页漏传 `token`** 会让 `#token#` 解析失败、整页组件报错 → 审计把「抵达页含信物」列为硬断言。

## Migration Plan

无运行期迁移：只改资源与文档。回滚 = 还原文件。已解锁玩家的 advancement 不变。

## Open Questions

- `patchouli:entity` 页在 GeckoLib 实体上的实机表现（首轮验收确认）。
- 四只条目的引言是否还需要按官方语气再收紧一版（文案基线已落在 D10）。
