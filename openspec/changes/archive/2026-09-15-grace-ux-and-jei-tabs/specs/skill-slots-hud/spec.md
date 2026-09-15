# skill-slots-hud Specification (delta)

## ADDED Requirements

### Requirement: 键位名称本地化覆盖
全部 5 个技能槽键位（`key.gensokyou.skill1..skill5`）SHALL 在 en_us 与 zh_cn 语言文件中均有对应条目，按键绑定界面的显示名 MUST NOT 出现裸键名。显示名 MUST NOT 使用"超人类"字样。

#### Scenario: 按键设置界面
- **WHEN** 玩家在原版控制设置界面查看本 mod 键位
- **THEN** 技能槽 1-5 五条键位均以当前语言显示名称
