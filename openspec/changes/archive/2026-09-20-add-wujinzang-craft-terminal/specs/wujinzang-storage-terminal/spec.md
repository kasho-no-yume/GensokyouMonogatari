# wujinzang-storage-terminal Specification (delta: add-wujinzang-craft-terminal)

## REMOVED Requirements

### Requirement: 批 1 不含自动抽料合成

**Reason**: 批 2 引入从仓储取料填入合成格与 JEI 联动，该排他约束不再成立。

**Migration**: 由 `wujinzang-craft-terminal` 的「仓储取料填充合成格」与「JEI 配方转移联动」需求取代；自动循环合成仍不在范围内。
