# ritual-editor-save-validate Delta Spec

## MODIFIED Requirements

### Requirement: 游戏内合规校验移植
`RitualPatternValidator` SHALL 以纯函数移植 `validate_ritual_pattern.py` 全部 5 条规则并与其结论一致：① 某阶增量展开后与低阶累积切片任意格位相交（含同 key 重复登记）= ERROR；② level 号重复 = ERROR；③ 锚点全文件恰一次、位于 (0,0,0)、只写在最低阶增量 = ERROR；④ 品阶下限（key 首现层 L 的标签下限须 ≥ L，含石特例；祭品台标签无品阶、不参与本规则）= WARN；⑤ 跨 pattern 劫持（A 某层为 B 任一层的子集 → B 被劫持）= ERROR。校验输入 SHALL 为合成后的完整 pattern 与全体已加载 pattern 列表。SHALL 附对表测试：对仓库 `data/gensokyou/rituals/*.json` 全体 pattern，Java 校验器 ERROR/WARN 集合与 python 校验器输出一致；loader 自身拒载逻辑 SHALL NOT 改动。

#### Scenario: 内置仪式全员过检
- **WHEN** 对现有全部内置 pattern 运行 Java 校验器
- **THEN** 与 python 校验器同样报告"全部通过"，无多报漏报

#### Scenario: 劫持检出
- **WHEN** 草稿使新仪式某层成为既有召唤环某层的子集
- **THEN** 仪式保存报"劫持 <pattern>"ERROR 并拒绝落盘
