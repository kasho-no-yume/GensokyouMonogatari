# ritual-offerings Delta Spec

## MODIFIED Requirements

### Requirement: 祭品要求声明
仪式 JSON SHALL 支持可选 `requirements` 数组，每项以字符键 `key` 与规范序号 `slot` 寻址单个祭品台（slot 为该键全部格位按 pattern 空间 z,x,y 升序的序号），并声明 `item`（物品 id 或 #标签）、`consume`（none/on_activate/periodic）及 periodic 专用 `period`。**单条要求恒为 1 个物品**（祭品台单件不变量）：`count` 字段废弃，loader 遇显式 `count > 1` SHALL 拒载该文件并报因；字段缺省或等于 1 SHALL 正常加载。多件需求 SHALL 以多条 requirement 绑定不同台位槽表达。新字段 SHALL 带默认值，缺省 requirements 视为无要求。

#### Scenario: 按位寻址
- **WHEN** 某仪式声明 key=P slot=0 要求钻石、key=P slot=1 要求金锭
- **THEN** 两项要求分别绑定 P 格位集合中规范序第 0、1 的两个祭品台，互不混淆

#### Scenario: count 大于 1 拒载
- **WHEN** 某 requirement 写了 `"count": 8`
- **THEN** 该 pattern 文件被拒绝加载，日志说明单条要求恒为 1 件

#### Scenario: 缺省兼容
- **WHEN** 某 rituals JSON 不含 requirements 字段
- **THEN** 该仪式加载成功且无任何祭品门槛

### Requirement: 激活消耗
consume=on_activate 的要求 SHALL 在启动动作成功瞬间扣减对应祭品台持有的那 1 件物品（置空台面）；任一要求未满足 SHALL 阻止本次启动且不产生部分扣除。

#### Scenario: 启动扣供
- **WHEN** 四台各摆齐要求的供品并点击启动
- **THEN** 各台要求物品被扣走、台面清空，仪式进入运行态

#### Scenario: 供品不足拒启
- **WHEN** 任一 on_activate 要求未满足时点击启动
- **THEN** 启动被拒绝，所有祭品台物品保持原样

### Requirement: 周期供给与断供停机
consume=periodic 的要求 SHALL 在运行期每 period 刻扣减一次对应台面的 1 件物品；到期时台面缺失或物品不符即视为断供，仪式 SHALL 自动停止（enabled 置否），需玩家重新启动。不做缓冲宽限。

#### Scenario: 正常续供
- **WHEN** 运行期每个周期到期时各要求台面均有正确物品
- **THEN** 逐台扣 1 件，仪式持续运行

#### Scenario: 断供停机
- **WHEN** 某周期到期时任一 periodic 要求的台面为空或物品不符
- **THEN** 仪式立即自动停止，UI 显示已停机
