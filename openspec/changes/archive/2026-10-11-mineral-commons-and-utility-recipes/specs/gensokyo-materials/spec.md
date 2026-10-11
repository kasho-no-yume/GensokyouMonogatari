## ADDED Requirements

### Requirement: 矿产仪式下界建材体积产出
矿产仪式（`gensokyou:oyamatsumi_circle`）的 `nether` 条件池 SHALL 追加原版
`minecraft:blackstone` 与 `minecraft:basalt`。二者 SHALL 在该仪式的**全部工具材质档位**
恒定产出，且 SHALL NOT 另立为 mod 素材——只以原版方块物品形态作为建材「体积」产出。
MUST NOT 改动 `nether` 池的加法语义（额外加权、不下沉 commons 桶），MUST NOT 改动其余
献祭仪式的下界池。

#### Scenario: 下界解锁后产出黑石与玄武岩
- **WHEN** 矿产仪式的祭品台上摆放 ≥3 个凋灵骷髅头，且某个祭品台持有合规镐并结算
- **THEN** 下界池结算结果中可出现 `minecraft:blackstone` 与 `minecraft:basalt`

#### Scenario: 各工具材质档均可产出
- **WHEN** 分别以 wood / stone / gold / iron / diamond / netherite 材质的镐结算矿产仪式
- **THEN** 黑石与玄武岩在每一档的下界池中均具备非零权重

#### Scenario: 不另立 mod 素材
- **WHEN** 审查 mod 物品注册表与素材目录
- **THEN** 不存在与黑石 / 玄武岩同形或重名的 mod 素材，二者只以原版物品出现

#### Scenario: 不影响其他献祭仪式
- **WHEN** 审查土产 / 木材 / 植物仪式的 `ritual_loot` 数据
- **THEN** 其 `nether` 池内容与本变更前一致
