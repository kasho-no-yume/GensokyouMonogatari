## ADDED Requirements

### Requirement: B 点作为符卡配方第二道 mod 门

现有 9 张道具符卡的源初造化配方 SHALL 各增加 `gensokyou:bpoint` 作为
与碎符卡星/符卡星并列的第二道 mod 门；B 点 MUST NOT 替代星类材料。

建议数量（可按后续平衡调整）：

- 低档（`spider_web` / `plague_repay` / `light_reflect`）：1 个 B 点；
- 中档（`healing_garden` / `flower_armor` / `demarcation` / `icicle_fall`）：2 个 B 点；
- 高档（`fox_servant` / `musou_fuuin`）：2 个 B 点。

加入 B 点后每条配方的 `Σcount` MUST NOT 超过其 `minTier` 对应的祭品台数（1 阶 = 16）。
`musou_fuuin` 现有 14 件原料，只能加 2 个 B 点；若坚持 4 个，必须同时减 2 件其它原料或抬到 2 阶。

B 点自身的转换配方 SHALL 为 `8×ppoint → 1×bpoint`（3×3 八格环绕），
MUST NOT 继续使用 4 P 的旧比例。

#### Scenario: 低档卡消耗 1 B 点

- **WHEN** 玩家在 1 阶源初造化合成 `spider_web`
- **THEN** 配方要求 1 个 B 点，产出道具卡

#### Scenario: 高档卡不超容量

- **WHEN** 玩家在 1 阶源初造化合成 `musou_fuuin`
- **THEN** 原料总量恰为 16（含 2 个 B 点），不超祭品台数

#### Scenario: B 点由 8 P 合成

- **WHEN** 玩家在 3×3 工作台用 8 个 P 点围出环形配方
- **THEN** 产出 1 个 B 点，P 点被消耗 8 个

#### Scenario: 不做纯 B 点替代

- **WHEN** 审查任一符卡配方
- **THEN** 仍含碎符卡星或符卡星，B 点只是追加门
