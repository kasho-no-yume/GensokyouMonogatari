# fairy-ecology Specification

## Purpose
TBD - created by archiving change phase-a-entry-loop. Update Purpose after archive.
## Requirements
### Requirement: 小妖精
模组 SHALL 提供敌对生物小妖精：主世界野外按配置权重随机刷新；行为为游走接近玩家并周期性发射单枚弹幕投射物；数值（生命/伤害/射速）从配置读取。

#### Scenario: 野外遭遇战
- **WHEN** 玩家在主世界地表停留一段时间
- **THEN** 附近刷新小妖精并向玩家发射弹幕，弹幕无视护甲造成 danmaku 伤害

#### Scenario: 击败掉落
- **WHEN** 玩家击杀小妖精
- **THEN** 按配置概率掉落 ppoint 与円

### Requirement: 大妖精
模组 SHALL 提供大妖精（小 BOSS）：低概率刷新，体型大于小妖精，生命值显著更高，发射三连扇形弹幕。

#### Scenario: 小 BOSS 战
- **WHEN** 玩家与大妖精交战
- **THEN** 其每次攻击发射水平扇形三枚弹幕，血量远高于小妖精

### Requirement: 引导书获取与内容
引导书 SHALL 仅通过击杀大妖精掉落获得（不开局赠送、不合成）；右键使用 SHALL 展示 mod 入门介绍（占位期以聊天消息呈现）。

#### Scenario: 入口链路
- **WHEN** 玩家首次击败大妖精并拾取引导书后右键使用
- **THEN** 聊天栏输出介绍文本（含灵力/符卡/仪式的占位说明），物品不消耗

