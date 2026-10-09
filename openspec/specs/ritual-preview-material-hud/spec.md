# ritual-preview-material-hud Specification

## Purpose
手持仪式构建杖进入预览时，在屏幕右缘实时显示完成本次搭建所缺的材料缺口 top-3，使玩家无需打开菜单即可核验"还差什么、差多少"。
## Requirements
### Requirement: 预览材料缺口 HUD 需求口径
材料缺口的需求格数 SHALL 以"完成本次搭建"为语义：客户端每帧经共用三分类 `classify` 取待放置格（pending）与被非目标方块占据格（conflicts），二者 SHALL 按 `resolveState` 解析目标方块计数，AIR 谓词被占格（airConflicts）与解析失败的格 SHALL NOT 计入。已满足谓词的格 SHALL NOT 计入。

#### Scenario: 红幽灵格计入需求
- **WHEN** 预览中某格被石头占据且目标应为仪式石（显示红色幽灵）
- **THEN** 该格计入仪式石的需求格数

#### Scenario: AIR 红框格不计入需求
- **WHEN** 预览中某 AIR 谓词格被方块占据（显示红色线框）
- **THEN** 该格不产生任何材料需求

#### Scenario: 升级预览只计差量
- **WHEN** 一阶已成型的核心上预览二阶
- **THEN** 已满足的低阶格不计入需求，仅差量格（含被改动占据的格）计入

### Requirement: 缺口数量计算

某方块的缺少数量 SHALL 为 `max(0, 需求格数 − 持有数)`；其中持有数 SHALL 为 `玩家背包持有数 + 绑定无尽藏库存（可用时）`。背包持有数 SHALL 按该方块对应物品精确匹配、遍历全部 36 格背包槽位；仓储持有数 SHALL 取自服务端下发的绑定仓储计数（规则见 `ritual-builder-wujinzang-binding`），不可用时按 0 计。两者口径 SHALL 与搭建扣料一致。HUD 仅显示实际会放置的方块类型（`resolveState` 成功解析者），且仅显示持有不足（缺口 > 0）者。

**无物品形态的方块**（`block.asItem() == Items.AIR`，如各类盆栽）SHALL 特殊处理：其缺口恒为 0、SHALL NOT 出现在 HUD 中，HUD 的门控（"至少一种缺口 > 0"）SHALL NOT 因其点亮。持有数统计 SHALL NOT 依赖 `countItem(Items.AIR)`（该调用恒为 0，会把此类方块误判为永久缺料）。

#### Scenario: 摆放方块缺口当帧缩减
- **WHEN** HUD 显示"仪式石 ×5"期间玩家手动放下一块仪式石（该格转为已满足）
- **THEN** 缺口当帧变为 ×4，无任何服务端交互

#### Scenario: 持有充足的方块不上榜
- **WHEN** 某方块需求 4、背包持有 10
- **THEN** 该行不出现在 HUD 中

#### Scenario: 仓储补足后不上榜
- **WHEN** 某方块需求 8、背包持有 3、绑定无尽藏已启动且存有 10 个
- **THEN** 合成持有数为 13，该方块不出现在缺口 HUD

#### Scenario: 仓储不可用时按背包计缺口
- **WHEN** 某方块需求 8、背包持有 3、绑定无尽藏未启动
- **THEN** 缺口为 5，该方块出现在 HUD 缺口列表

#### Scenario: 无物品方块不产生缺口
- **WHEN** 预览中某待放置格解析为 `potted_dead_bush`（无物品形态）且玩家背包无任何盆栽
- **THEN** 该方块不出现在 HUD 缺口列表，且当其余方块缺口全为 0 时 HUD 整体不显示

### Requirement: Top-3 排序与截取
HUD SHALL 最多显示 3 行，按 缺少数量降序 → 需求格数降序 → 方块注册名字典序 三级比较取前 3；排序 SHALL 稳定（同输入不跳行）。

#### Scenario: 缺口最大者优先
- **WHEN** 仪式石缺 12、铜块缺 3、灵基座缺 1、末地石缺 7
- **THEN** 依次显示 仪式石 ×12、末地石 ×7、铜块 ×3

### Requirement: HUD 显隐门控
HUD SHALL 仅在以下全部条件满足时绘制，任一不满足 SHALL 当帧整体消失：预览态在场且维度匹配；玩家主手或副手持构建杖；玩家非创造模式（`hasInfiniteMaterials` 为 false）；至少一种方块缺口 > 0；所选图案仍可解析。GUI 隐藏（F1 `hideGui`）时 SHALL NOT 绘制。

#### Scenario: 切换手持当帧隐藏
- **WHEN** 预览 HUD 显示中，玩家滚轮切换到其他物品栏位
- **THEN** HUD 当帧消失；切回构建杖且仍在预览时恢复显示

#### Scenario: 材料集齐整块消失
- **WHEN** 玩家补齐全部缺料（缺口全为 0）
- **THEN** HUD 整体消失，不保留"材料充足"占位

#### Scenario: 创造模式不显示
- **WHEN** 创造模式玩家进入预览
- **THEN** HUD 不出现

### Requirement: 呈现版式
HUD SHALL 绘制于屏幕右缘竖直居中（GUI 缩放坐标），逐行右对齐、行首为物品图标；每行内容为 方块图标 + 方块名 + `×缺少数量`，数量 SHALL 以红色呈现，文本 SHALL 带阴影以保证世界背景下的可读性，且 SHALL NOT 覆盖屏幕中央准星区域。

#### Scenario: 右缘布局不遮准星
- **WHEN** 三行 HUD 显示中，玩家转动视角
- **THEN** 面板固定于屏幕右缘中部，准星与视野中心无任何绘制元素

