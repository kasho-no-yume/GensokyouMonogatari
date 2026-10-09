## MODIFIED Requirements

### Requirement: 召唤存活规则
经百鬼夜行降临的 BOSS SHALL 按野生 BOSS 处理。召唤仪器的关闭、拆除或结构失效 MUST NOT 直接移除已降临的 BOSS 实体。

BOSS 死亡时 SHALL 正常结算掉落与经验，并 MUST 清理其派生实体。

#### Scenario: 拆坛不移除 BOSS
- **WHEN** BOSS 已降临后玩家拆除召唤祭坛
- **THEN** 该 BOSS 实体仍然存在并继续战斗

#### Scenario: 死亡结算
- **WHEN** 玩家击杀任一只召唤 BOSS
- **THEN** 按配置掉落碎符卡星（黑谷山女额外保底掉隙间碎片），并按经验配置结算
