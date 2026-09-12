# ritual-editor-save-validate Specification

## Purpose
TBD - created by archiving change ritual-wand-dev-editor. Update Purpose after archive.
## Requirements
### Requirement: 两级保存语义
阶级保存 SHALL 把 diff 捕获产物存为**世界级草稿覆盖层**（按 patternId+level 键，存于 saved data，重启不丢），SHALL NOT 运行合规校验、SHALL NOT 写任何文件——仪式允许处于施工中残缺态。仪式保存 SHALL 以（jar/datapack 原 pattern ⊕ 草稿覆盖层）合成完整结构运行全量合规校验，通过后方可落盘。

#### Scenario: 草稿不挡施工
- **WHEN** 玩家对一个尚未完成合规的仪式执行阶级保存
- **THEN** 草稿保存成功且无校验错误回显，world 文件未被改动

#### Scenario: 仪式保存拦截不合规
- **WHEN** 仪式保存时任一校验 ERROR 成立
- **THEN** 不落盘，逐条回显 ERROR（含格位与原因），草稿保留供继续修改

### Requirement: 游戏内合规校验移植
`RitualPatternValidator` SHALL 以纯函数移植 `validate_ritual_pattern.py` 全部 5 条规则并与其结论一致：① 某阶增量展开后与低阶累积切片任意格位相交（含同 key 重复登记）= ERROR；② level 号重复 = ERROR；③ 锚点全文件恰一次、位于 (0,0,0)、只写在最低阶增量 = ERROR；④ 品阶下限（key 首现层 L 的标签下限须 ≥ L，含石特例；祭品台标签无品阶、不参与本规则）= WARN；⑤ 跨 pattern 劫持（A 某层为 B 任一层的子集 → B 被劫持）= ERROR。校验输入 SHALL 为合成后的完整 pattern 与全体已加载 pattern 列表。SHALL 附对表测试：对仓库 `data/gensokyou/rituals/*.json` 全体 pattern，Java 校验器 ERROR/WARN 集合与 python 校验器输出一致；loader 自身拒载逻辑 SHALL NOT 改动。

#### Scenario: 内置仪式全员过检
- **WHEN** 对现有全部内置 pattern 运行 Java 校验器
- **THEN** 与 python 校验器同样报告"全部通过"，无多报漏报

#### Scenario: 劫持检出
- **WHEN** 草稿使新仪式某层成为既有召唤环某层的子集
- **THEN** 仪式保存报"劫持 <pattern>"ERROR 并拒绝落盘

### Requirement: 保存产出双写覆写
仪式保存通过后 SHALL 将合成 pattern 完整重序列化为 v5 JSON 并**直接覆写**世界数据包 `<world>/datapacks/gs_dev/data/gensokyou/rituals/<path>.json`（不存在时创建 `pack.mcmeta` 与目录），完成后回显"执行 /reload 生效"提示；同 id 再次保存覆写自身产物。SHALL 机会性探测 GAMEDIR 上层 `src/main/resources/data/gensokyou/rituals/`（dev 环境特征），存在则同步覆写源码树，不存在则跳过并在回执注明。保存成功 SHALL 清除该 pattern 全部阶级草稿。
#### Scenario: 秒生效闭环
- **WHEN** 玩家仪式保存后执行 /reload
- **THEN** 修改后的仪式即刻可被匹配/构建/再次编辑，无需重启或人肉搬文件

#### Scenario: 生产环境跳过回声
- **WHEN** 服务器运行环境探测不到 dev 源码树目录
- **THEN** 仅写 gs_dev 数据包，回执注明"源码树未同步"，功能不报错

#### Scenario: 保存清草稿
- **WHEN** 仪式保存成功后再次打开该仪式任一阶级
- **THEN** 展示的是已保存结构，无陈旧草稿残留

