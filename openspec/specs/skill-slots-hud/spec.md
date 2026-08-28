# skill-slots-hud Specification

## Purpose
TBD - created by archiving change phase-c-gensokyo-gate. Update Purpose after archive.
## Requirements
### Requirement: 学卡数据
每名玩家 SHALL 持有已学符卡列表（随存档持久化、死亡保留）；过渡期提供权限命令 `/gs_learn <card>` 写入（委托任务接入后移除）。

#### Scenario: 学习与持久化
- **WHEN** 玩家被授予 musou_fuuin 后退出重进或死亡重生
- **THEN** 已学列表保持包含该卡

### Requirement: 技能槽施放
SHALL 提供三个技能槽键位（默认 G/H/J，可改键）：按下后 C2S 请求服务端校验——已学、冷却结束、灵力足够——通过则执行对应符卡效果并扣灵力进入冷却；任一条件不满足则提示且无消耗。

#### Scenario: 正常施放
- **WHEN** 已学 musou_fuuin 且灵力足够的玩家按下槽位 1 键
- **THEN** 扣除配置灵力，六玉环绕生效，槽位进入冷却

#### Scenario: 未学习拦截
- **WHEN** 未学习任何卡的玩家按键
- **THEN** 提示未学习，无效果无消耗

### Requirement: HUD 冷却显示
HUD SHALL 在热键栏上方显示三个技能槽：已学卡绘制物品图标；冷却期间叠加遮罩与剩余秒数；未学习置灰。

#### Scenario: 冷却可视化
- **WHEN** 施放后观察 HUD
- **THEN** 对应槽位出现遮罩并倒数至冷却结束消失

