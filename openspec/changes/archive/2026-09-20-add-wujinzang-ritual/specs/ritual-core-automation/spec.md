# ritual-core-automation Specification (delta: add-wujinzang-ritual)

## ADDED Requirements

### Requirement: 核心物品接入面按图案分派

仪式核心对外暴露的 `IItemHandler` SHALL 按当前匹配图案分派实现。无尽藏之仪（`gensokyou:wujinzang_circle`）SHALL 暴露「跨晶块合并箱」：`getSlots` 为在线晶块条目数之和；`insertItem` 忽略传入槽号、按分类与动态分区写入（可落任一合适晶块）并返回未收纳余量；`extractItem` 按条目定位返回不超过堆叠上限的真实栈；`simulate` 零副作用。其余图案 SHALL 维持既有祭品台代理语义。

#### Scenario: 无尽藏合并箱写入
- **WHEN** 漏斗向成型无尽藏核心推入一组可堆叠物品
- **THEN** 物品经分区算法落入某类型制晶块，合并计数

#### Scenario: 无尽藏合并箱抽取
- **WHEN** 漏斗从无尽藏核心抽取某条目
- **THEN** 从持有该条目的晶块扣减并返回真实栈

#### Scenario: 其他图案不受影响
- **WHEN** 漏斗接入八方归元或其他图案的核心
- **THEN** 表现为既有祭品台代理箱

### Requirement: 未成型无尽藏零槽

无尽藏核心未成型时，其物品接入面 SHALL 报告 0 槽，插入返回全部余量、抽取返回空。

#### Scenario: 未成型零槽
- **WHEN** 结构未成型时管道接入核心
- **THEN** 接口报告 0 槽，不产生世界变化
