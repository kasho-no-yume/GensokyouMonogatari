# ritual-offerings Specification

## Purpose
祭品要求的声明式契约：仪式 JSON 中逐台声明所需物品，由独立门槛层完成校验与消耗。本能力域使"摆供品启动仪式"成为数据驱动的一等公民，匹配器保持纯结构判定。
## Requirements
### Requirement: 祭品要求声明
仪式 JSON SHALL 支持可选 `requirements` 数组，每项以字符键 `key` 与规范序号 `slot` 寻址单个祭品台（slot 为该键全部格位按 pattern 空间 z,x,y 升序的序号），并声明 `item`（物品 id 或 #标签）、`count`、`consume`（none/on_activate/periodic）及 periodic 专用 `period`。新字段 SHALL 带默认值，缺省视为无要求。

#### Scenario: 按位寻址
- **WHEN** 某仪式声明 key=P slot=0 要求钻石、key=P slot=1 要求金锭
- **THEN** 两项要求分别绑定 P 格位集合中规范序第 0、1 的两个祭品台，互不混淆

#### Scenario: 缺省兼容
- **WHEN** 某 rituals JSON 不含 requirements 字段
- **THEN** 该仪式加载成功且无任何祭品门槛

### Requirement: 门槛校验独立于结构匹配
结构匹配器 SHALL 保持纯方块判定；祭品要求校验 SHALL 由独立的检查层在启动前与界面渲染时执行，读取各目标祭品台的持有物对照要求。祭品台被取走物品 SHALL NOT 触发结构失效回调。

#### Scenario: 取物不塌结构
- **WHEN** 已成型的仪式被人从祭品台取走要求物品
- **THEN** 结构仍判定成立，仅门槛清单转为未满足

### Requirement: 激活消耗
consume=on_activate 的要求 SHALL 在启动动作成功瞬间按 count 扣减对应祭品台持有物；扣减失败（数量不足）SHALL 阻止本次启动且不产生部分扣除。

#### Scenario: 启动扣供
- **WHEN** 四台各摆齐要求的供品并点击启动
- **THEN** 各台按要求扣减，仪式进入运行态

#### Scenario: 供品不足拒启
- **WHEN** 任一 on_activate 要求未满足时点击启动
- **THEN** 启动被拒绝，所有祭品台物品保持原样

### Requirement: 周期供给与断供停机
consume=periodic 的要求 SHALL 在运行期每 period 刻扣减一次；到期无法足额扣减时仪式 SHALL 自动停止（enabled 置否），需玩家重新启动。不做缓冲宽限。

#### Scenario: 正常续供
- **WHEN** 运行期每个周期到期时各台供品充足
- **THEN** 按量扣减，仪式持续运行

#### Scenario: 断供停机
- **WHEN** 某周期到期时任一 periodic 要求无法满足
- **THEN** 仪式立即自动停止，UI 显示已停机
