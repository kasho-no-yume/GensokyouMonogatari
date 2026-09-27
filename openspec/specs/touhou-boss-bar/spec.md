# touhou-boss-bar Specification

## Purpose
TBD - created by archiving change add-hyakki-yagyo-summon-ritual. Update Purpose after archive.
## Requirements
### Requirement: 东方 BOSS 使用咒符条血条

模组 SHALL 为其**东方 BOSS**（本模组注册的 BOSS 类实体）改绘血条：取消原版血条，改绘一套**固定的咒符条**造型。造型 SHALL **统一适用于全部东方 BOSS**，MUST NOT 逐 BOSS 定制形状或配色；后续新增东方 BOSS SHALL 自动套用同一造型，SHALL NOT 需要为其单独实现绘制。

改绘 SHALL 通过**取消原版逐条血条事件并自绘**实现，MUST NOT 依赖对原版血条容器字段的反射或 mixin 注入实现。

作用范围 SHALL **严格限定**于本模组的东方 BOSS。原版血条（凋灵、末影龙、袭击事件 BOSS 等）SHALL 保持原版渲染，MUST NOT 被本能力波及。

掉血平滑 SHALL 保留（受击后血条 SHALL 平滑过渡到新血量而非瞬切）。多根血条同时在场时 SHALL 正确纵向堆叠，行高 SHALL 高于原版以容纳咒符条的边框、撕边与副行，堆叠超出屏幕 1/3 高度时 SHALL 与原版一致地截断。

#### Scenario: 东方 BOSS 出场

- **WHEN** 玩家进入某东方 BOSS 的可追踪范围
- **THEN** 顶部出现咒符条造型血条，原版 182×5 血条不出现

#### Scenario: 掉血平滑

- **WHEN** 东方 BOSS 一次性损失大量生命
- **THEN** 血条血量在短时内平滑滑落到新值，不发生瞬切

#### Scenario: 原版血条不受影响

- **WHEN** 玩家在场且原版凋灵 BOSS 血条同时激活
- **THEN** 凋灵血条保持原版渲染，咒符条只作用于东方 BOSS

#### Scenario: 多根咒符条堆叠

- **WHEN** 场上有两根以上东方 BOSS 血条
- **THEN** 纵向堆叠、互不重叠，堆叠总高超出屏幕 1/3 时最上方的被截断

#### Scenario: 新增 BOSS 自动套用

- **WHEN** 后续新增一个东方 BOSS 实体并给它挂了原版 BOSS 血条
- **THEN** 其血条自动呈现为咒符条，无需为其新增绘制代码

### Requirement: 作用范围判别

系统 SHALL 以**纯客户端**方式判别一根血条是否属于东方 BOSS，判据 SHALL 为该血条对应的实体 UUID 所指实体是否为本模组的东方 BOSS 类型。BOSS 血条只对能观察到该 BOSS 的玩家呈现，故该实体必然处于已加载状态，判别 SHALL NOT 需要额外的服务端同步通道。

系统 MUST NOT 采用以下脆弱的隐式标记作为判别依据：血条名称的前缀约定、把某条 `BossBarColor` / `BossBarOverlay` 组合当作专用标记。这些做法在新增实体或与其它模组共用时会静默误判。

#### Scenario: 判别不依赖名字

- **WHEN** 某东方 BOSS 的显示名被翻译或改写
- **THEN** 仍被正确判别为东方 BOSS 并使用咒符条

#### Scenario: 判别不依赖颜色约定

- **WHEN** 某东方 BOSS 的血条颜色与原版某 BOSS 恰好相同
- **THEN** 两者仍被正确区分，不出现原版 BOSS 被误绘成咒符条

#### Scenario: 实体已卸载

- **WHEN** 某根血条的实体在客户端已不可解析
- **THEN** 回退为原版渲染而非崩溃或错绘

