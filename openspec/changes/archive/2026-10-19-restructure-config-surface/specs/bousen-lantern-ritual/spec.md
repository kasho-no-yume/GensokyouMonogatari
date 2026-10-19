## MODIFIED Requirements

### Requirement: 分阶灵力三件套

产灵速率、缓存上限、供灵端点速率三者 SHALL 全部按结构等级（`match.level()`）分阶取值，MUST NOT 使用 `base × 4^level` 一类的几何公式（三档均非等比）。取值 MUST 由 `gensokyou-server.toml`（SERVER）的分阶列表驱动而非硬编码。

| 等级 | 产灵/秒 | 缓存上限 | 供灵端点/秒 |
|---|---|---|---|
| 1 | 100 | 50000 | 1000 |
| 2 | 500 | 1000000 | 5000 |
| 3 | 3000 | 10000000 | 20000 |

缓存上限 SHALL 并入核心容量按图案分派（`getCapacity()`），缺此分支时三阶全部回落 `DEFAULT_CORE_CAPACITY` 而**不会报错**。供灵端点速率 MUST 为静态值（MUST NOT 随蜡烛明灭或启停变化）；受灵端点 `spiritInRatePerSecond` SHALL 保持默认 0，本仪式不可被路由选为受灵汇。

#### Scenario: 三阶缓存不回落默认值

- **WHEN** 3 阶忘川成型
- **THEN** `getCapacity()` 返回 10000000，而非 `DEFAULT_CORE_CAPACITY`

#### Scenario: 可被路由选为供灵源

- **WHEN** 万象共鸣寻找供灵源，且某 3 阶忘川核心缓存存有灵力
- **THEN** 该核心以 20000/秒 上限出现在候选中

#### Scenario: 明灭不致源闪断

- **WHEN** 2 阶忘川运行期间发生蜡烛熄灭又补点
- **THEN** 其供灵端点速率始终为 5000/秒，路由候选资格不发生跳变

#### Scenario: 不可作为受灵汇

- **WHEN** 万象共鸣寻找受灵汇
- **THEN** 忘川核心不在候选中

#### Scenario: 服务端配置可调而客户端一致

- **WHEN** 服主修改忘川分阶列表并重载，玩家打开引导书查看该仪式
- **THEN** 核心按新值运行，且玩家侧展示的数值与服务器一致（SERVER 配置随登录同步）
