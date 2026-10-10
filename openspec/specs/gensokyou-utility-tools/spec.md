# gensokyou-utility-tools Specification

## Purpose
TBD - created by archiving change add-gensokyou-material-uses. Update Purpose after archive.
## Requirements
### Requirement: 灵力筑基器
系统 SHALL 注册一个用于仪式建筑施工的灵力筑基器方块（`gensokyou:landscaping_tool`）。该方块 SHALL 具备标准方块的碰撞箱与贴图，破坏后 SHALL 掉回物品。右键该方块 SHALL 打开配置界面，可设定长（X）、宽（Z）、高（Y）；长/宽各自独立可调且以方块为中心，高以方块下方一格为底向上。范围 SHALL 以线框指示，被方块遮挡的部分 SHALL 以半透明（alpha 0.5）显示。启动 SHALL 消耗玩家灵力池（消耗 = 基数 × 长 × 宽 × 高，不足则不可启动），并按盒体<b>平等删除除「仪式结构方块」与基岩外的一切方块</b>，再把盒体底层铺成泥土（SHALL NOT 覆盖基岩或仪式结构方块）。该灵力筑基器 SHALL 无耐久消耗并受冷却约束。

#### Scenario: 放置与外观
- **WHEN** 玩家用灵力筑基器物品右键地面
- **THEN** 放置出一个有碰撞箱与贴图的灵力筑基器方块，玩家无法穿过它

#### Scenario: 右键开界面并设定范围
- **WHEN** 玩家右键已放置的灵力筑基器
- **THEN** 打开配置界面，显示长 / 宽 / 高三个可调参数、估算消耗与启动按钮

#### Scenario: 范围线框指示
- **WHEN** 玩家在界面上调大或调小任一参数并松手
- **THEN** 世界中的线框随之更新：长/宽以方块为中心、高自方块下方一格向上，被遮挡处呈半透明

#### Scenario: 平等删除
- **WHEN** 玩家在同时存在石头、泥土、原木、树叶、杂草、箱子、矿石的地形上启动灵力筑基器
- **THEN** 盒体内除仪式结构方块与基岩外的一切方块被删除

#### Scenario: 保留仪式结构与基岩
- **WHEN** 盒体内存在 `gensokyou:ritual_core`、`gensokyou:ritual_pedestal`、仪式石族、`gensokyou:crystal` 或基岩
- **THEN** 它们保持原状，不被删除或替换

#### Scenario: 底层铺平不覆盖基岩
- **WHEN** 盒体底层存在基岩
- **THEN** 基岩保持原状，其余底层格铺成 `minecraft:dirt`

#### Scenario: 灵力消耗
- **WHEN** 玩家启动灵力筑基器且灵力池足以覆盖估算消耗
- **THEN** 扣除对应灵力并执行筑基

#### Scenario: 灵力不足不可启动
- **WHEN** 玩家启动但灵力池低于估算消耗
- **THEN** 启动被拒，灵力不扣

#### Scenario: 冷却约束
- **WHEN** 玩家连续两次启动同一灵力筑基器且间隔短于冷却时长
- **THEN** 第二次不生效，直至冷却结束

#### Scenario: 创造模式绕过低
- **WHEN** 创造模式玩家启动灵力筑基器
- **THEN** 灵力筑基器正常生效，不受灵力、泥土与冷却限制

### Requirement: 灵力引爆器
系统 SHALL 注册一个可放置的灵力引爆器方块（`gensokyou:spirit_bomb`）。该方块 SHALL 具备标准方块的碰撞箱与贴图，破坏沉睡态方块 SHALL 掉回引爆器物品。右键该方块 SHALL 打开配置界面，可设定起爆时间、强度与半径。启动 SHALL 消耗玩家灵力池，灵力不足时 SHALL 无法启动。爆炸 SHALL 掉落所有因爆炸而毁坏的方块，且引爆器本体 SHALL 在起爆后以物品形态掉落（可重复使用）。

#### Scenario: 放置与配置
- **WHEN** 玩家右键地面放下引爆器后再右键它
- **THEN** 打开配置界面，显示起爆时间、强度、半径三个可调参数与启动按钮

#### Scenario: 碰撞与不可穿行
- **WHEN** 玩家尝试走过已放置的引爆器
- **THEN** 引爆器像普通方块一样阻挡玩家

#### Scenario: 启动扣除灵力
- **WHEN** 玩家在配置界面点击启动且灵力池足以覆盖估算消耗
- **THEN** 引爆器进入倒计时，玩家灵力池扣除对应消耗

#### Scenario: 灵力不足不可启动
- **WHEN** 玩家点击启动但 `SpiritPowerData.current` 低于估算消耗
- **THEN** 启动按钮不可用，引爆器保持沉睡，灵力不扣除

#### Scenario: 凡人不可用
- **WHEN** 阶级为 0（灵力池恒为 0/0）的玩家尝试启动
- **THEN** 无法启动

#### Scenario: 全掉落爆炸
- **WHEN** 引爆器起爆并毁坏了范围内若干方块
- **THEN** 所有被毁坏方块以物品形态掉落，其内容物（TileEntity 内物品）一并掉落

#### Scenario: 沉睡可收回
- **WHEN** 玩家破坏一个未启动的引爆器
- **THEN** 该引爆器以物品形态回到玩家手中

#### Scenario: 已启动不可收回
- **WHEN** 玩家破坏一个已进入倒计时的引爆器
- **THEN** 引爆器不掉落物品

#### Scenario: 自主计时
- **WHEN** 引爆器已启动且放置者离线
- **THEN** 引爆器继续倒计时并按设定时间起爆

#### Scenario: 参数硬上限
- **WHEN** 玩家尝试将半径或强度调至超过配置上限
- **THEN** 参数被钳制在上限值，无法超出

#### Scenario: 掉落产物限额
- **WHEN** 一次大半径全掉落爆炸产生的物品实体数超过配置上限
- **THEN** 超出部分的产物被丢弃并在服务端日志告警，服务器不发生卡死

