## Why

项目已有多个可被玩家感知的里程碑（获得关键物品、进入幻想乡、击败大妖精、启动核心仪式等），但此前没有落地成就体系，玩家无法获得这些探索/进度的反馈，设计表 `sheet/achieve.xlsx` 中的成就也未接线。

## What Changes

- 新增成就系统：将 `sheet/achieve.xlsx` 的 15 条成就落地为原版 Advancement，统一复用原版成就页与奖励反馈。
- 成就条件分两层触发：
  - 物品/击杀/维度/药水/附魔等，优先使用原版或既有事件判定
  - 仪式类（无尽藏、万象共鸣、八方归元、梦渡之座灵力产出、附魔成功、神恩阶级）在 RitualBehavior/RitualSession 完成结算处统一发出成就事件
- 里程碑成就用原版 `display.frame = "challenge"` 的紫色挑战框呈现。
- 为每条成就提供稳定 ID、图标、名称/描述 lang key，以及父节点/根节点结构。

## Capabilities

### New Capabilities
- `achievements`: 幻想乡模组成就体系，定义成就触发、展示、里程碑样式和持久化语义

### Modified Capabilities
- （无）

## Impact

- `data/gensokyou/advancement/**`：新增成就 JSON
- `assets/gensokyou/lang/*.json`：新增成就名/描述
- `ritual` / `spirit` / `event`：新增成就事件触发点与工具方法
- 可能需要补充一个 debug 命令或测试挂钩，便于验证成就是否授予
