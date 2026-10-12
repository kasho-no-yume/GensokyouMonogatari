## MODIFIED Requirements

### Requirement: 数值以秒带与挨弹带约束

黑谷山女的生命 SHALL 由「参照玩家 DPS × 战斗秒数」得出，弹伤 SHALL 由「参照玩家 EHP ÷ 挨弹数」
得出；秒数与挨弹数 MUST 从 config（`yamameBossSeconds` / `yamameBossHits`）读取。

其参照阶 SHALL 为 T1，秒数 SHALL 落在 2~15 分钟秒带内（配置区间 120~900s）。有效生命 SHALL 为
**确定值**，MUST NOT 施加出生随机。秒数为**目标值而非保证**：SHALL 以 `/gs_boss spawn` 实测后回写。

#### Scenario: 数值可配置
- **WHEN** 调整 `yamameBossSeconds` 或 `yamameBossHits`
- **THEN** 其生命与单发弹伤随之改变，无需改动实体代码

#### Scenario: 秒带内
- **WHEN** 以默认配置计算黑谷山女的战斗秒数
- **THEN** 结果落在 2~15 分钟秒带内，且两次召唤结果一致（无随机偏移）
