## MODIFIED Requirements

### Requirement: 数据驱动的权重表加载

权重表 SHALL 存放于 `data/gensokyou/ritual_loot/*.json` 并经服务端重载监听器加载。加载器 SHALL 保持原单池格式兼容，并支持可选 `output_channels`：每个通道可声明独立 commons、工具材质表与条件池；声明通道后允许省略传统顶层 commons 池。非法物品 id 或负权重 MUST 拒载对应文件并记录原因。

#### Scenario: 旧版单池文件继续加载

- **WHEN** 传统掉落文件仅声明 commons、材质 special 与条件池
- **THEN** 文件仍按既有规则加载并只生成原单一概率池

#### Scenario: 加载独立通道

- **WHEN** 大山祇掉落文件声明石料与矿物 `output_channels`
- **THEN** 各通道具有独立 commons、材质特殊项与条件池，并可分别抽取

## ADDED Requirements

### Requirement: 大山祇独立保底产出通道

大山祇每次结算 SHALL 独立抽取石料与矿物配额。0 阶石料基数为 30、默认每阶倍率 ×4；0 阶矿物基数为 10、默认每阶倍率 ×2。材质池与地狱/末地/幻想乡条件项只影响各自通道的物品权重，不得减少另一通道的件数。其他三种工具献祭仪式保持既有 `20 × 4^level` 总次数。

#### Scenario: 阶级配额

- **WHEN** 大山祇处于 0、1、2 阶且配置为默认值
- **THEN** 分别产出 30+10、120+20、480+40 件，总计 40、140、520 件

#### Scenario: 互不稀释

- **WHEN** 任意条件池解锁或镐材质变化
- **THEN** 石料与矿物应产数量不变，仅对应通道的具体产物概率改变
