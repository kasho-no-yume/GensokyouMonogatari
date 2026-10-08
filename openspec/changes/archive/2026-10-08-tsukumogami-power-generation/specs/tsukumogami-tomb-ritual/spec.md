## ADDED Requirements

### Requirement: 付丧之冢燃料表
付丧之冢 SHALL 仅接受「考古垃圾」级燃料：20 种原版陶片（`minecraft:*_pottery_sherd`）与 16 种原版音乐唱片（`minecraft:music_disc_*`）。盔甲纹饰模板与 `decorated_pot` MUST NOT 进入燃料表。燃料表取值 SHALL 由数据文件 `data/gensokyou/ritual_special/tsukumogami_fuel.json` 的 `entries:[[itemId, points], ...]` 给定，默认值此处钉死：

- 陶片：单件总量分布在 7000~20000（L0，每个物品固定一个点位）
- 唱片：单件总量分布在 15000~45000（L0）

每件燃料 SHALL 在其点位的基础上随阶级乘以 `5^level`。燃料表 SHALL 以数据文件 `data/gensokyou/ritual_special/tsukumogami_fuel.json` 提供、随 RitualDataSyncPayload 下发（`tsukumogami_fuel` 键），JEI 与指导书页读数 SHALL 不再完全取决于客户端本地配置。

#### Scenario: 陶片入台即吞
- **WHEN** 燃烧中的仪式其一座祭品台放有任意陶片
- **THEN** 该陶片当场销毁（不返还），该槽开始一个燃烧批次，批次时长 = 该陶片 L0 点位 × 5^level ÷ 50 × 5^level = 点位 ÷ 50 秒（阶级无关）

#### Scenario: 模板与陶罐入台无反应
- **WHEN** 祭品台放有盔甲纹饰模板或 decorated_pot
- **THEN** 不点火、不消耗、保持待机

### Requirement: 串行燃烧与产灵公式
付丧之冢 SHALL 整座仪式同一时刻只维持一个燃烧批次（串行点火即吞，与迦具土口径一致）：每个批次的产灵速率 SHALL 为 `50 × 5^level` 灵力/秒。产灵入账 SHALL 仅在存在燃烧批次（含缓存满的空烧期）时发生——无燃料待机与停等 MUST NOT 产出。批次烧尽 SHALL 同 tick 尝试点燃下一个燃料（按祭品台规范序扫描第一个合格物），批次溢出 tick 结转。仪式停机时批次冻结存盘；结构失效时批次作废、已吞燃料不返还。

#### Scenario: 兑行串行产灵
- **WHEN** 多座祭品台同时备有合格燃料且仪式等级 0
- **THEN** 整座仪式每秒净入账 50，一个批次结束后才点燃下一个

#### Scenario: 无缝换批
- **WHEN** 当前批次在烧尽的同 tick 台面另有燃料
- **THEN** 新批次同 tick 点火，无断档帧

### Requirement: 缓存、空烧与停等
仪式缓存上限 SHALL 为 `400000 × 5^level`。燃烧中缓存触顶 SHALL 继续消耗批次计时但停止入账（空烧）；缓存回落后下一 tick 立即恢复该槽入账，台面仍有燃料且无满缓存时立即点新批。停等期间未点火的台位物品 MUST NOT 被消耗。

#### Scenario: 满缓存空烧
- **WHEN** 缓存已满且某槽剩余 100 tick
- **THEN** 该 100 tick 照常流逝、缓存数值不变、燃烧态视觉不断，烧完后若仍满则不选下一批

### Requirement: 槽核与供灵输出
仪式核心 SHALL 提供一个灵力核心槽（同迦具土）：缓存灵力按所插核心注灵速率逐 tick 节流转入。供灵出率 SHALL 由独立配置基项声明（默认与产灵基项同值 50×5^level），路由（万象共鸣）可抽取该上限；产灵基项与供灵基项 MUST NOT 共用 getter。

#### Scenario: 拆核掉电池
- **WHEN** 槽内插有灵力核心且仪式核心方块被破坏
- **THEN** 灵力核心（含已存灵力）作为物品掉落

### Requirement: 全客户端黑色烟雾
燃烧态期间仪式 SHALL 以客户端本地绘制的黑色烟雾呈现（整座仪式统一冒黑气）：暗色（近黑）烟片自台面上升、billboard、alpha 混合、尺寸/密度/不透明度随阶级增强。服务端 MUST NOT 发送任何粒子包；服务端仅通过渲染态字段下发 enabled/level/是否燃烧的位掩码。非燃烧态（停等/待机）MUST 零烟雾，启停有淡入淡出包络。

#### Scenario: 服务端零粒子
- **WHEN** 多名玩家在燃烧的付丧之冢附近
- **THEN** 服务端不产出任何粒子相关网络包，烟雾完全由客户端绘制

#### Scenario: 停等即散
- **WHEN** 仪式从燃烧进入停等
- **THEN** 烟柱随包络淡出至零烟雾，界面文案切换

### Requirement: GUI 信息行
仪式 GUI SHALL 显示当前燃烧批次的燃料图标与剩余秒数（逐 tick 精确同步），并沿用既有面板展示缓存/上限/等级/祭品台数。停等/无料/缓存满/等待电池应各自独立文案行（同迦具土）。

#### Scenario: 单槽计时
- **WHEN** 存在燃烧批次且界面停留 10 秒
- **THEN** 界面显示其燃料图标与逐秒递减的剩余秒数
