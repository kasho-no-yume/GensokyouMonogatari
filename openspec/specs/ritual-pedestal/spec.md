# ritual-pedestal Specification

## Purpose
祭品台：仪式图案的承台组件，全局单一注册方块 + `tier` blockstate 自动染色；支持单件存取、平躺/悬浮自转双态渲染；其生存获取为不依赖任何仪式的工作台配方，是整条仪式链的开局 bootstrap 入口。
## Requirements
### Requirement: 祭品台存取
祭品台方块 SHALL 支持右键放入单个物品手中物品、空手右键取回；内容随方块实体持久化。台面持有量 SHALL 受**单件不变量**约束：任何写入路径（含仪式逻辑与自动化代理）使台面物品数超过 1 时，超出部分 MUST 当场在台面位置掉落为物品实体（仅服务端）。手动放料既有交互不变——台面非空时右键仍为"取出/换料"而非堆叠。存档加载 MAY 容忍历史超限栈，但此类台面 SHALL 视为满槽（不可再插入），并随消耗/抽出自然回落。

#### Scenario: 放置与取回
- **WHEN** 手持一组物品右键祭品台后空手再右键
- **THEN** 物品先被收纳（台面 1 件、手中余量不消失）、后被取回

#### Scenario: 超限写入余量落地
- **WHEN** 某写入路径向空台面放入 count>1 的物品栈
- **THEN** 台面仅持 1 件，其余数量在台面处掉落为物品实体

#### Scenario: 非空台右键换料
- **WHEN** 台面已有物品时玩家持另一物品右键（非潜行）
- **THEN** 既有行为不变：取出台面物品，不放入

### Requirement: 悬浮渲染
祭品台上的物品 SHALL 由方块实体渲染器绘制，亮度取台面上方一格的环境光照。姿态分两态：**静置态**物品平躺静置于台面——其最低缘 SHALL 落于台面（离隙 ≤0.05 格，仅防深度冲突），2D 贴图物品、方块类物品与自定义 3D 模型物品 MUST 一律满足此不变量，MUST NOT 因 FIXED 上下文自带平移或烘焙模型枢轴差异而被抬升；**所属仪式激活态**物品 SHALL 悬浮于台面上方、保持**竖直立起**并绕世界 Y 轴缓慢自转，立起物品的底缘 MUST NOT 切入台面。两态之间 SHALL 以过渡进度平滑插值（倾角随进度从平躺渐变到竖直、悬浮高度同步渐变），启停切换 MUST NOT 出现瞬移/跳帧姿态。破坏方块后不再渲染。激活态标志 SHALL 为纯渲染态：MUST NOT 持久化进存档，其唯一来源为所属核心在启动/停止/结构重扫时的广播；区块重载或服务器重启后台面物品 MUST 以核心当前运行态为准呈现姿态。

#### Scenario: 静置态底缘贴台
- **WHEN** 分别将一根棍状 2D 物品、一个方块类物品、一枚自定义 3D 模型物品放入未激活（停机/无仪式）的祭品台
- **THEN** 三者均平躺静置于台面，底缘离台不超过一丝缝隙，不出现悬浮半格

#### Scenario: 激活态立姿自转
- **WHEN** 祭品台所属仪式处于激活态且台面有物品
- **THEN** 物品竖直悬浮于台面上方并持续绕纵轴旋转，底缘不切入台面

#### Scenario: 启停过渡无跳变
- **WHEN** 仪式启动或停止，激活态在约 0.7 秒内切换
- **THEN** 物品姿态为连续渐变（平躺↔立起同步升降浮动），无单帧翻转跳变

#### Scenario: 重启后无陈旧悬浮
- **WHEN** 仪式运行中服务端崩溃/强制关闭，世界重开后仪式处于未启动态
- **THEN** 台面物品呈静置平躺贴台姿态，不保持悬浮自转

### Requirement: 单方块祭品台随仪式等级变色
祭品台 SHALL 为单一注册方块 `gensokyou:ritual_pedestal`——物品形态、创造栏条目、掉落表各仅一个，物品名 SHALL NOT 染品阶色。方块 SHALL 携带 `tier`（0-5）BlockState 属性切换 `ritual_pedestal_0..5` 变体模型（分面贴图 `_top_N/_bottom_N/_N` 全数保留复用）；放置时恒为 `tier=0` 灰外观。服务端在仪式结构重扫时 SHALL 将匹配结构内全部祭品台的 `tier` 写为当前仪式等级（与核心同源同值），仅在值变化时写块；结构失效或裸放时 SHALL 回落 0。`tier` 属性变化 MUST NOT 影响方块实体持久化（台面物品无损）与结构匹配（标签按方块身份判定）。既存的 `ritual_pedestal_0..5` 六方块及其注册、物品、blockstate、掉落表 SHALL 全部移除，无存档迁移（dev 世界旧台子消失，重建即可）。

#### Scenario: 变色与核心同源
- **WHEN** 结构内最高品阶仪式石为 2 级且仪式成型
- **THEN** 全部祭品台呈现 tier 2 蓝外观，与仪式核心同色

#### Scenario: 换料变色不丢物
- **WHEN** 某祭品台台面放有物品后其 `tier` 属性被重扫改写
- **THEN** 台面物品纹丝不动，方块实体未被重建

#### Scenario: 失效回落灰
- **WHEN** 仪式结构失效
- **THEN** 核心与全部祭品台的 `tier` 回落 0，呈灰色

#### Scenario: 创造栏唯一
- **WHEN** 玩家在创造栏查找祭品台
- **THEN** 仅见一个"祭品台"条目，白字无名染

### Requirement: 姿态表现状态与自转相位
祭品台渲染的姿态过渡进度与自转相位 SHALL 仅由当前台面持有物与所属仪式当前激活态决定：台面清空（含被仪式吞食、玩家取走）时该位姿表现状态 SHALL 被清除，MUST NOT 残留；新物品放入 MUST 从静置态（过渡进度 0、自转相位 0）起算，MUST NOT 继承前一件物品的进度或残余转速。自转角度 SHALL 由随激活进度累积的相位给出（激活时相位随进度加速累积、停用时随进度减速至停），MUST NOT 以「绝对游戏时间 × 激活进度」直接计算角度。

#### Scenario: 吞料后放新物品不残留
- **WHEN** 造化合成飞行阶段吞掉祭品台上的原料清空台面，随后向该未激活台面放入一件新物品
- **THEN** 新物品立即平躺静置于台面，MUST NOT 出现急速旋转或残留悬浮

#### Scenario: 激活起旋与停用停旋连续
- **WHEN** 台面有物品时仪式启动随后停止
- **THEN** 物品自转随激活进度平滑加速、停止时平滑减速至静止，任一帧转角增量与转速成正比，无暴旋/单帧扫圈

### Requirement: 祭品台由工作台平滑石祭台配方产出
系统 SHALL 提供普通工作台有序合成配方 `gensokyou:ritual_pedestal`，图案为 `SSS / RPR / SSS`（`S`=`minecraft:smooth_stone`、`R`=`gensokyou:ritual_stone_0`、`P`=`gensokyou:ppoint`），产出 `ritual_pedestal×1`。该配方 MUST NOT 出现在任何 `ritual_recipes/*.json` 中，MUST NOT 声明 `spCost`、`minTier` 或任何灵力消耗，MUST NOT 依赖灵力核心、维度进度或已建成的仪式结构。

#### Scenario: 首个仪式前可造出祭品台
- **WHEN** 玩家尚未建成任何仪式、尚未获得任何灵力核心，仅有平滑石、圆石与 P 点
- **THEN** 玩家可在工作台完成 `SSS / RPR / SSS` 并获得一个祭品台

#### Scenario: 祭品台不需要灵力
- **WHEN** 玩家查看或执行该配方
- **THEN** 配方不消耗灵力，玩家的灵力池为空也能正常合成

#### Scenario: 祭品台不存在仪式产出路径
- **WHEN** 审查任一 `ritual_recipes/*.json`
- **THEN** 没有任何配方以 `ritual_pedestal` 为产物，祭品台只有本条工作台获取路径

#### Scenario: 配方原料全部为开局可得
- **WHEN** 审查配方原料
- **THEN** 原料仅为 `minecraft:smooth_stone`、`gensokyou:ritual_stone_0` 与 `gensokyou:ppoint`，不含需仪式或维度才能获得的材料

### Requirement: 祭品台指导书物品词条展示工作台配方
系统 SHALL 在 `gensokyou:items` 分类建立祭品台 Patchouli 物品词条 `item.gensokyou.ritual_pedestal`，包含 spotlight 说明页与 `patchouli:crafting` 配方页，指向 `gensokyou:ritual_pedestal` 工作台配方。该词条 MUST NOT 挂 `advancement` 或 `secret`，SHALL 常驻可见。

#### Scenario: 词条显示祭品台工作台配方
- **WHEN** 玩家在指导书中打开祭品台词条
- **THEN** 第二页显示 `SSS / RPR / SSS` 工作台配方与平滑石、仪式石、P 点三类原料

#### Scenario: 词条常驻可见
- **WHEN** 玩家尚未获得下界或末地进度
- **THEN** 祭品台词条仍可见且不显示为未解锁

