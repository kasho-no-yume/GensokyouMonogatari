# Danmaku program core baseline manifest

基线来源：当前 `BossCards`、`Track`、`SpellCard` 与 `TrackLint`。本文件记录迁移前的内容形状；它不是新程序后端的运行结果。

## 内容清单

| BOSS | 卡数 | 卡循环 | 旧轨道/行为特征 |
|---|---:|---|---|
| 大妖精 | 3 | 240 / 240 / 200 | 环、花海、雨与地柱；锁轴/编队/无害窗口/激光 |
| 黑谷山女 | 4 | 200 / 260 / 260 / 260 | 蛛丝尾珠、螺旋、呼吸、随机散射 |
| 八云蓝残魂 | 8 | 120 / 140 / 160 / 160 / 200 / 140 / 160 / 200 | 目标复制、笼、激光、地柱、段式需求 |
| 露米娅/傩面 | 5 | 旧轨道持续重复 | 环、壳、地雷、落下弹、径向段 |

合计 20 张正式符卡。底噪由 BOSS 运行时另行维护，不属于这 20 张卡的卡内轨道。

逐拍原始声明见 `legacy-cards.json`，包含每张卡全部轨道/拍、Shape.Params、Behaviour、Projectile、寿命、无害窗、锚点、颜色与倍率。导出入口 `LegacyManifestTest.exportTwentyLegacyCards`，输出 `build/reports/danmaku-core/legacy-cards.json`，测试验证20张卡并回读JSON。

## 已核实的底噪

| 来源 | 几何与默认参数 | 时间/归属 |
|---|---|---|
| BigFairyEntity.tickDefaultAttack | GRID_FACING，25发，10度步距，速度.3，直径.7，前偏3 | config间隔60；实际冷却61tick；公共一份，主目标方向；默认实体寿命1200 |
| YamameEntity.tickDefaultAttack | FAN，3发，55度，速度.35，直径.55 | config间隔80；实际冷却81tick；公共一份；默认实体寿命1200 |
| YakumoRanEntity.tickDefaultAttack | FAN，3发，30度，速度.34，直径.55，色盘at(1) | config间隔40，实际41tick；旧路径开启公共底噪，新内容关闭；默认实体寿命1200 |

以上是源码和配置默认值核对，实际会话的配置覆写必须随性能基线另存。三者先把cooldown设为interval，之后每tick递减并return，直到下一tick才再次发射，因此不是严格Every(interval)。

Excel三页的逐单元格源快照及文件SHA-256保存在 `sheet-snapshot.json`，由openpyxl以data_only读取。其内容意图与旧代码声明分开保存，旧蓝8卡不冒充表格中的新蓝8卡。

## 差异登记口径

`legacyObserved` 在本阶段指源码/离线声明证据，实机观察另标；`newObserved` 全部尚未迁移，不能填为已验证。

| intended | legacyObserved | newObserved | reason |
|---|---|---|---|
| 山女周期60内0/20/40全部执行 | BossCards声明三拍，旧runner存在轨级与拍级双重due过滤风险 | core偏移调度测试通过，正式卡未迁移 | 周期子拍不能被相位0过滤 |
| FAN phase改变方向、CAGE gap影响缺口 | Geometry.java FAN分支只使用spread计算offset，不读phase；CAGE分支每面无条件生成n发，不读gapDeg（3n发） | 未迁移 | operations阶段真实几何验证 |
| 蓝8按具体构成直径.40 | 表格共通.70大玉与具体构成冲突；迁移采用.40 | 未迁移 | 内容决策已在content-mapping记录 |
| 尾珠age4..96，共24次 | tickDanmaku寿命判断为age>life，绝对寿命同样为now-birth>life；尾珠判断age>0且age%4==0，故未提前消亡时age100仍可派生，即25次 | 未迁移 | 父life[0,100)半开区间改变旧端点 |
| 底噪Every60/80 | 旧cooldown实际61/81 | 未迁移 | 旧冷却端点差异 |
| 蓝无独立底噪 | 旧YakumoRanEntity开启 | 未迁移 | 蓝八卡替换内容明确关闭 |

## 已登记差异

- 旧 `TrackRunner` 以轨道和实体弹为调度单位；新 core 以类型化程序节点和显式时间域为单位。
- 旧 `Track.Beat` 将几何、行为、寿命、无害窗口、锚点和编排字段放在同一记录；这些字段在新程序中由不同操作/通道承载。
- 旧轨道的 `repeatEvery=0` 既表示显式一次性时间线，也可能被作者误解为持续播放；新 core 必须显式区分 `Once`、`Loop`、`Stream`。
- 旧实现已有周期内偏移拍的回归测试，但新 core 将 `Every(period, phase)` 作为组合器语义，不能由外层 `phase=0` 过滤。
- 大妖精花海保留了密度豁免；其估值理由仍待新后端实测复核。
- 旧实体后端的编队帧会在发射时复制到每枚弹；新设计要求共享句柄与闭包，具体运行时迁移属于后续阶段。

## 端点口径

- 区间基线：`[start, end)`。
- 旧弹幕默认 BOSS 寿命：400 tick；显式寿命以 `Beat.lifetimeTicks` 为准。
- 旧默认实体后端仍负责伤害、擦弹、方块命中和实体生命周期；本阶段只保存其依赖清单。
