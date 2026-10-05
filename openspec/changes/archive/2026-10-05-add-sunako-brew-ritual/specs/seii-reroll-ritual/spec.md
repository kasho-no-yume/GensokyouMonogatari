## MODIFIED Requirements

### Requirement: 核不进配方 ingredients，走核心 GUI 专用槽

洗练目标核 SHALL 存放在**核心 GUI 的专用目标物品槽**（紧邻灵力核心槽右侧，由行为的额外物品槽声明机制声明显隐），SHALL NOT 占用祭品台位 —— 祭品台一台一件且是配方催化剂的载体，1 阶只有 4 台，核若占一台就只剩 3 个催化剂位。

核 SHALL NOT 声明为 `ritual_recipes` 的 `ingredients` 条目（`RitualRecipeMatcher.apply` 会真实消耗 takes，核被消耗则赌注不可退、abort 无法退款）。行为 SHALL 读核心的额外槽定位 `AmpCoreItem`，并以 `effect` 命名空间编码目标核阶（`seii:core_1` / `seii:core_2` / `seii:core_3`）自行挑选配方。

配方 SHALL 使用 `match: MAX`（子集命中），使未被消耗的多余催化剂留台不动。行为 MUST NOT 依赖 `RitualRecipeMatcher.matchMax`的 `Σcount` 排序选择配方（核不在 ingredients 后多条配方可能同时命中，Σcount 排序会选错）。

目标槽 SHALL 随存档持久化（组件式存储，非 session 态），结构拆解失配时仍可取出。核必须是从武器合成台取下的**裸核**。

槽位 SHALL 经框架的**泛化额外物品槽机制**声明（1 格，坐标与既有目标槽一致），客户端 MUST NOT 硬编码 `AmpCoreItem` 校验（校验由服务端权威执行）。迁移到泛化机制后，核的存取、持久化、防吞件与坐标语义 MUST NOT 发生玩家可见变化。

#### Scenario: 核不被消耗

- **WHEN** 洗练成功完成并采纳
- **THEN** 核仍留在核心的目标槽内，仅 `rune_affixes` 组件被改写

#### Scenario: 祭品台全留给催化剂

- **WHEN** 1 阶结构（4 台）摆满 4 件催化剂
- **THEN** 4 个台位全部可用（核不占台），`seii:core_1` 的 4 条 ingredients 全部可被匹配

#### Scenario: 目标槽持久化

- **WHEN** 核放入目标槽后关闭界面、存档并退出游戏
- **THEN** 重新登录后核仍在目标槽内，未被吞件

#### Scenario: 核在武器槽内不算

- **WHEN** 增幅核仍装在某武器的 slot3 内、目标槽为空
- **THEN** 拒绝启动（玩家须先用弹幕合成台取核再放入目标槽）

#### Scenario: 迁移后行为不变

- **WHEN** 星移之仪迁移到泛化额外槽机制后玩家进行完整洗练流程
- **THEN** 核的存取、持久化与洗练结果与迁移前完全一致