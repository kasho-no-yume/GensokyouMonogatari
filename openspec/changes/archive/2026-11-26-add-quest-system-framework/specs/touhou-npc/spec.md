# touhou-npc Specification

## ADDED Requirements

### Requirement: 稗田阿求常驻于人间之里稗田居所

系统 SHALL 在 `gensokyou:gensokyo` 人间之里 `hv_hieda_residence` 建筑内部生成一个独立服务端稗田阿求 NPC。阿求 SHALL 使用固定局部锚点，根据已保存建筑实例原点、朝向和布局版本换算世界坐标；继承东方 NPC 的持久化/保护行为，并 SHALL NOT 作为客户端环境居民生成。居所完整放置后 SHALL 幂等生成；区块顺序变化、存档重载、v1/v2 布局或旧存档补齐 MUST NOT 造成重复阿求。

#### Scenario: 居所放置后生成阿求

- **WHEN** 稗田居所首次完成所有相关区块的放置
- **THEN** 居所内部 SHALL 出现一个固定位置的阿求 NPC

#### Scenario: 区块反向加载

- **WHEN** 居所跨多个区块且区块以不同顺序加载
- **THEN** 系统 SHALL 仍只生成一个阿求

#### Scenario: 已有居所旧存档

- **WHEN** 加载更新前已生成稗田居所、但尚未生成阿求的存档
- **THEN** 系统 SHALL 补生成一个阿求，不要求重新生成建筑

### Requirement: NPC 任务标记按观察者玩家判定

头顶 `!/?` 任务标记 SHALL 根据观察者玩家可见的任务状态决定，而 MUST NOT 由一个 NPC 全局字段代表所有玩家。`!` SHALL 表示当前世界共享主线下一步要求与该 NPC 对话；`?` SHALL 表示该观察玩家可在该 NPC 处接取支线/委托或有待向该 NPC 回报的任务。若两者同时成立，显示 `!`，但交互 SHALL 保留全部可用任务操作。

#### Scenario: 私人委托标记互不泄露

- **WHEN** 玩家 A 在阿求处有可接/待回报委托，而玩家 B 没有
- **THEN** A 看到阿求头顶 `?`，B MUST NOT 因 A 的个人状态看到 `?`

#### Scenario: 共享主线对话标记

- **WHEN** 当前共享主线要求与某 NPC 对话
- **THEN** 所有玩家 SHALL 看到该 NPC 的 `!`，直到共享主线推进离开此交互条件

#### Scenario: 同一 NPC 有多类任务

- **WHEN** 玩家对同一 NPC 同时满足主线对话和个人委托提示
- **THEN** NPC 头顶优先显示 `!`，打开交互后仍可访问可接取/待回报委托
