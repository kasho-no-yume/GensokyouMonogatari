# boss-summon-effect Specification

## Purpose
TBD - created by archiving change add-remnant-touhou-bosses. Update Purpose after archive.
## Requirements
### Requirement: 召唤 effect 注册与落地
模组 SHALL 提供 `effect` 字符串 id 到 BOSS 实体工厂的注册表，供百鬼夜行召唤仪式的配方 `effect` 字段引用。

注册 SHALL 在加载期完成；引用未注册的 id 时 SHALL 给出明确错误而非静默无反应。BOSS 实体的生成位置 SHALL 为仪式核心正上方，与百鬼夜行「降临光柱」的落点一致。

本能力 SHALL NOT 改动 `HyakkiYagyoBehavior` 的会话、充能、容量或演出语义，也 SHALL NOT 定义配方条目本身。

#### Scenario: 注册可解析
- **WHEN** 以四只 BOSS 各自的 effect id 查询注册表
- **THEN** 每次均解析到对应的实体工厂

#### Scenario: 未注册 id 报错
- **WHEN** 配方引用了一个未注册的 effect id
- **THEN** 产生明确的错误信息，MUST NOT 表现为无任何反应的静默失败

#### Scenario: 生成位置在核心上方
- **WHEN** 召唤会话完成演出并落地
- **THEN** BOSS 实体生成于仪式核心正上方，与降临光柱落点一致

#### Scenario: 不改会话语义
- **WHEN** 比对本变更前后 `HyakkiYagyoBehavior` 的实现
- **THEN** 会话启动、无门票、充能、容量、演出各环节语义不变

### Requirement: 召唤存活规则
经百鬼夜行降临的 BOSS SHALL 按野生 BOSS 处理。召唤仪器的关闭、拆除或结构失效 MUST NOT 直接移除已降临的 BOSS 实体。

BOSS 死亡时 SHALL 正常结算掉落与经验，并 MUST 清理其派生实体。

#### Scenario: 拆坛不移除 BOSS
- **WHEN** BOSS 已降临后玩家拆除召唤祭坛
- **THEN** 该 BOSS 实体仍然存在并继续战斗

#### Scenario: 死亡结算
- **WHEN** 玩家击杀任一只召唤 BOSS
- **THEN** 按配置掉落碎符卡星（鬼蛛额外保底掉隙间碎片），并按经验配置结算

