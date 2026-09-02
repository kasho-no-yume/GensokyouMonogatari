## ADDED Requirements

### Requirement: 形态变种族注册
SHALL 按品阶独立注册 3 族共 18 个装饰方块：`ritual_stone_slab_0..5`（台阶，`SlabBlock`）、`ritual_stone_stairs_0..5`（楼梯，`StairBlock`，基态为同品阶 `ritual_stone_N`）、`ritual_stone_wall_0..5`（墙，`WallBlock`），复用 `ritual_stone_N` 贴图，不新增贴图。

#### Scenario: 注册完整性
- **WHEN** 游戏启动完成注册
- **THEN** 18 个方块与 18 个 `TieredBlockItem`（品阶染名）全部就绪，无注册错误

#### Scenario: 视觉一致
- **WHEN** 放置任意品阶的台阶/楼梯/墙
- **THEN** 方块表面贴图与同品阶仪式石满方块一致

### Requirement: 方块行为继承原版
变种方块 SHALL 完整继承原版对应方块类的放置与连接行为，碰撞与硬度对齐仪式石本体（1.5F/6F）。

#### Scenario: 台阶半砖与双砖
- **WHEN** 对台阶上半面或下半面放置
- **THEN** 产生 top/bottom 半砖，两半砖叠放合并为双砖（显示满块贴图）

#### Scenario: 楼梯转角
- **WHEN** 楼梯相互拼接
- **THEN** 产生内/外转角形态，可倒置

#### Scenario: 墙自动连接
- **WHEN** 墙与相邻方块拼接
- **THEN** 产生 post/low/tall 连接形态

### Requirement: 装饰定位——不参与仪式结构
变种方块 SHALL 为纯装饰：`ModBlocks.tierOf()` 语义不变（仅认满方块仪式石/祭品台），图案谓词不接纳变种，仪式 maxTier 统计不受变种影响。

#### Scenario: 变种置于祭坛不改变仪式语义
- **WHEN** 玩家用台阶/楼梯/墙装饰祭坛平台（图案槽位之外）
- **THEN** 仪式匹配结果与装饰前完全一致

#### Scenario: 变种无法顶替图案槽位
- **WHEN** 变种方块被放置在要求满方块仪式石的图案槽位上
- **THEN** 该槽位校验不通过

### Requirement: 获取途径
变种方块 SHALL 可通过合成与切石获得：合成（同品阶仪式石：台阶 3→6、楼梯 6→4、墙 6→6）、切石（1→1），并全部收录进 `gensokyou` 创造标签。

#### Scenario: 合成产出
- **WHEN** 用 3/6/6 个同品阶仪式石按对应排布合成
- **THEN** 分别产出 6 个台阶 / 4 个楼梯 / 6 个墙，品阶对应

#### Scenario: 切石产出
- **WHEN** 在切石机用 1 个 ritual_stone_N 切割
- **THEN** 可产出同品阶台阶/楼梯/墙各 1 个

#### Scenario: 创造标签收录
- **WHEN** 打开 gensokyou 创造标签
- **THEN** 18 个变种物品按品阶序列展示

### Requirement: 掉落规则
变种方块 SHALL 自掉落；台阶按半砖状态掉落（双砖掉 2，半砖掉 1），不掉落其他品阶的变种。

#### Scenario: 双砖掉落
- **WHEN** 挖掉一个双台阶
- **THEN** 掉落 2 个同品阶台阶

#### Scenario: 楼梯墙自掉落
- **WHEN** 徒手（无工具需求，与仪式石本体一致）挖掉楼梯或墙
- **THEN** 掉落自身 1 个
