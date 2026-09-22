# fairy-ecology delta

## MODIFIED Requirements

### Requirement: 引导书获取与内容
引导书 SHALL 通过击杀大妖精掉落获得（占位逻辑保留，后续另行 change 调整）；并 SHALL 额外支持以 9 张记忆残页 3×3 有序合成获得。记忆残页 SHALL 增加凑书提示 tooltip。右键使用 SHALL 由服务端打开指导书 GUI（Patchouli；首次使用直接进入序言条目，见 guide-book 规范）。Patchouli 为外部必需依赖，缺失时游戏不加载。物品 MUST NOT 消耗。

#### Scenario: 大妖精掉落入口
- **WHEN** 玩家击败大妖精并拾取引导书
- **THEN** 书物品进入背包，右键可打开指导书 GUI

#### Scenario: 残页合成入口
- **WHEN** 玩家以 3×3 平铺 9 张记忆残页合成
- **THEN** 产出 1 本引导书，效果与掉落获得的书一致

#### Scenario: 使用行为
- **WHEN** 玩家右键使用引导书
- **THEN** 打开指导书 GUI（首次进入序言条目，后续落地页），物品不消耗
