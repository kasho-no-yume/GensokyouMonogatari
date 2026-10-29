## ADDED Requirements

### Requirement: 聚宝盆的方块与物品形态

系统 SHALL 提供方块 `gensokyou:treasure_basin`（聚宝盆）及其方块物品形态。

- 方块 SHALL 为 15/16 格高的**方鼎形**异形方块：外形轮廓 MUST 含四足、深腹、
  向外探出 1 texel 的鼎口与两侧立耳；碰撞箱 SHALL 覆盖足+腹+口
  （`[2,0,2] → [14,12,14]`），立耳 MUST NOT 参与碰撞；选取箱 SHALL 覆盖到耳顶
  （`[1,0,1] → [15,15,15]`）。两者 MUST NOT 覆盖整格。
- 方块 SHALL 具有整数属性 `fill`（0~3），由容器占用格数派生：
  0 格 → 0、1~3 格 → 1、4~6 格 → 2、7~9 格 → 3。
- 方块 MAY 依 `fill` 发出 0~3 级微光，并 SHALL 依 `fill` 切换鼎腹符文贴图。
- 方块 MUST NOT 遮挡邻面、MUST NOT 阻挡天光（`noOcclusion` + 零光遮蔽），
  避免异形小模型在地面投下整格暗影。
- 该物品 SHALL 出现在本模组创造模式物品栏，并 SHALL 具备一条生存获取配方与一条掉落表。

#### Scenario: 放置与破坏

- **WHEN** 玩家手持聚宝盆右键地面
- **THEN** 在地面放置一个 15/16 高的方鼎形方块，玩家可以正常从其上方走过而不被整格挡住

#### Scenario: 符文随存量点亮

- **WHEN** 盆内已收下 5 件不同物品（占用 5 格）
- **THEN** 方块状态变为 `fill=2`，盆膛符文点亮第二段，方块发出 2 级微光

### Requirement: 吸附范围与过程

聚宝盆 SHALL 在服务端每 tick 牵引以自身方块为中心的 **7×7×7 立方**（各轴 ±3 格）内的掉落物。

- 牵引对象 SHALL 仅限 `ItemEntity`；经验球、箭、生物及其它实体 MUST NOT 受影响。
- 牵引 SHALL 通过覆盖实体的速度实现（设置朝向盆口的速度向量），
  MUST NOT 直接改写实体坐标（不瞬移）。
- 牵引速度 SHALL 随距离缩短而增大，并 SHALL 在远距离时带上扬分量，
  使物品以弧线越过盆沿而非贴地平移。
- 物品与盆口距离小于阈值时 SHALL 被收入容器；收不下的余量 MUST 留在世界中。
- 吸附过程 SHOULD 伴随粒子与音效反馈。
- 满仓时 MUST NOT 施加牵引（见「容量与满仓」）。

#### Scenario: 三格外的掉落物被吸入

- **WHEN** 玩家站在盆旁，在距离盆 3 格处丢下一组圆石
- **THEN** 圆石沿弧线飞向盆口并消失，盆内出现该组圆石

#### Scenario: 范围外不受影响

- **WHEN** 掉落物位于盆外 4 格处
- **THEN** 该掉落物保持原地，不被牵引

#### Scenario: 立方范围上下生效

- **WHEN** 掉落物位于聚宝盆正上方 2 格、或正下方 2 格
- **THEN** 该掉落物被牵引（7×7×7 立方语义）

#### Scenario: 非物品实体不受影响

- **WHEN** 经验球或箭矢进入 7×7×7 范围
- **THEN** 其运动轨迹不因聚宝盆而改变

### Requirement: 容量与满仓行为

聚宝盆 SHALL 提供 **9 格**物品容器，每格上限为该物品的最大堆叠数。

- 物品入槽 SHALL 优先并入同类型未满堆叠，其次占用空槽。
- 当容器**无法再收下任何物品**时，聚宝盆 MUST 停止牵引新物品；掉落的物品 MUST 保持原状。
- 当空位重新出现后，聚宝盆 SHALL 自动恢复牵引，无需玩家干预。
- 容器内容 SHALL 随方块实体 NBT 持久化，服务器重启或区块重载后完整恢复。

#### Scenario: 满仓停止吸附

- **WHEN** 聚宝盆 9 格全部装满，玩家在其旁丢下物品
- **THEN** 新丢下的物品不被牵引，留在地面；盆内 9 格内容不变

#### Scenario: 腾出空位后恢复

- **WHEN** 玩家从满仓的盆中取走一组物品，再在 3 格内丢下物品
- **THEN** 新物品被正常吸入

#### Scenario: 重载后内容保留

- **WHEN** 服务器保存并重新加载含 5 组物品的聚宝盆
- **THEN** 盆内仍是原来那 5 组物品

### Requirement: 交互与取出

- 玩家右键聚宝盆 SHALL 打开 3×3 容器界面，界面 SHALL 同时展示玩家背包与快捷栏。
- 手持任意物品右键 MUST 打开界面并 MUST NOT 在相邻方块上误放置该物品。
- 潜行右键 SHALL 把盆内物品按槽位顺序倒入玩家背包；放不下的部分 MUST 留在盆内。
- 界面 MUST 在玩家离开方块 8 格后关闭（`stillValid` 距离 64 的平方）。

#### Scenario: 右键开界面

- **WHEN** 玩家空手或有手持物品时右键聚宝盆
- **THEN** 打开标题为「聚宝盆」的 3×3 界面，可达取放物品

#### Scenario: 潜行一键倒入

- **WHEN** 玩家潜行右键一个装有 3 组物品的聚宝盆，且背包有足够空位
- **THEN** 3 组物品进入玩家背包，盆内清空、`fill` 回到 0

#### Scenario: 背包放不下

- **WHEN** 潜行右键时玩家背包仅能容纳 1 组
- **THEN** 1 组进入背包，其余 2 组仍留在盆内

### Requirement: 自动化能力

聚宝盆 SHALL 向其他模组暴露标准 `ItemHandler` 方块能力（`Capabilities.ItemHandler.BLOCK`）。

- 该能力 SHALL 对**任意方向**可用（全向），既可抽出也可插入。
- 外部抽出后，`fill` 状态 SHALL 随之更新。
- 该能力 MUST NOT 复制物品：从容器抽出的栈 MUST 是同一份物品的引用/拷贝，
  且容器内相应槽位 MUST 被同步扣减。

#### Scenario: 漏斗抽取

- **WHEN** 漏斗贴着聚宝盆任意一面并从其抽取
- **THEN** 盆内物品被正常取走，`fill` 档位相应下降

#### Scenario: 管道插入

- **WHEN** 外部管道向聚宝盆插入物品且存在空位
- **THEN** 物品进入容器，`fill` 档位相应上升

### Requirement: 破坏与内容物归还

方块被破坏或被移除时，其内容物 SHALL 全部掉落到世界中，且本体 SHALL 正常掉落。

- 内容物掉落 MUST 与本体掉落相互独立，MUST NOT 出现复制或吞没。
- 移除过程 MUST NOT 因容器已失效而丢弃物品。

#### Scenario: 破坏满仓的聚宝盆

- **WHEN** 玩家破坏一个装满 9 组物品的聚宝盆
- **THEN** 地面出现 1 个聚宝盆物品与 9 组原有物品，总计无增减

### Requirement: 获取、文档与本地化

- 聚宝盆 SHALL 经**源初造化之仪**（`gensokyou:zaohua_circle`）配方 `zaohua_treasure_basin` 产出，
  该配方 SHALL 声明 `mode:"activation"`、`match:"max"`、`minTier:0`、`spCost:6000`。
- 该配方的原料 SHALL 为
  `minecraft:hopper×2 + minecraft:gold_block×2 + gensokyou:spirit_iron×4`，Σcount = 8。
- 原料总量 MUST NOT 超过 0 阶造化的祭品台数（8）。
- 配方 MUST NOT 依赖任何 T2 及以上材料或 BOSS 掉落。
- 指导书 SHALL 存在条目 `item_treasure_basin`，含 spotlight、仪式配方页与说明页三页，
  并 SHALL 说明吸附范围、9 格容量、满仓即停、以及"可被漏斗抽取"。
- 方块名、容器标题与书页正文 SHALL 同时提供 `zh_cn` 与 `en_us` 文案；物品 MUST NOT 附带自定义 tooltip
  （说明只进指导书）。
- 所有贴图模型引用 MUST 指向 `gensokyou:` 命名空间，MUST NOT 直接引用 `minecraft:` 贴图。

#### Scenario: 零阶造化可炼

- **WHEN** 玩家在 0 阶源初造化之仪摆齐漏斗 2、金块 2、灵铁 4，
  且灵力充足并触发合成
- **THEN** 消耗 6,000 灵力与一份原料，产出一个聚宝盆

#### Scenario: 原料超过台数则不可炼

- **WHEN** 审查该配方
- **THEN** Σcount = 8 ≤ 0 阶造化祭品台数 8（不触发容量失配）

#### Scenario: 入口配方不含高阶料

- **WHEN** 审查该配方的全部原料
- **THEN** 每一种均可在未击败任何 BOSS、且未取得任何 T2 材料的前提下取得

#### Scenario: 书页可查

- **WHEN** 玩家在引导书「物品」分类中打开聚宝盆条目
- **THEN** 看到聚宝盆图标、造化配方页与说明页，中文环境下全部为中文
