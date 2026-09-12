# spirit-core-item Delta Spec

## MODIFIED Requirements

### Requirement: 灵力核心物品与定值参数
系统 SHALL 提供 `spirit_core_0..5`（六阶灵力核心）储能物品：容量与注灵速率作为构造定值（0 阶：容量 50000、注灵速率 1000/s；每高一阶容量 ×12、速率 ×8），品阶即新物品新定值，MUST NOT 依赖单一全局配置承载档位差异。物品 SHALL 不可堆叠，创造栏可取，语言键中英双备。旧 `spirit_core` 单档物品 SHALL 删除（WIP 阶段无存档迁移）。

#### Scenario: 档位即物品
- **WHEN** 未来加入更高阶灵力核心
- **THEN** 仅需注册新物品实例并传入更大容量/速率，仪式与组件逻辑零改动

### Requirement: 存储数据组件
灵力核心 SHALL 以数据组件记录已存灵力量，类型为 long（64 位）；数值在 tooltip SHALL 以"已存/容量"形式展示，并 SHALL 增加输入输出速率行（品阶色着色）。组件缺失时视为空核（stored = 0），MUST NOT 导致物品加载失败。

#### Scenario: 空核兜底
- **WHEN** 一个无数据组件的灵力核心被加载
- **THEN** 正常显示为 0/50000，可正常接受注灵

### Requirement: 充放语义
灵力核心被仪式输出槽注灵时 SHALL 按自身注灵速率受限、至容量上限为止；本能力 MUST NOT 提供任何从核心向玩家或其他机能的放电路径（存储/传输仪式后续立项）。

#### Scenario: 装满即止
- **WHEN** 缓存灵力持续注入直至核心满 50000（0 阶）
- **THEN** 注入停止、溢出留在仪式缓存，核心可取出且数值不超容

## REMOVED Requirements

### Requirement: 占位合成配方
**Reason**: 六阶核心的获取/合成体系（低阶合高阶等）尚未设计，六物品暂仅创造栏可取。
**Migration**: 后续配方立项时在 `spirit-core-tiers` 能力下新增获取途径规格。
