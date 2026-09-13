# player-spirit-attributes Delta

## REMOVED Requirements

### Requirement: 炼体提升灵力伤害
**Reason**: 淬体（tempering）仪式为无 pattern 的占位孤儿，随本变更整体退役；成长玩法将来另立新仪式立项。
**Migration**: `temperLevel`/`playerSpiritDamage` 附件字段与 HUD 展示保留，旧档无损加载；删除后不再有增长途径，属预期退役行为。
