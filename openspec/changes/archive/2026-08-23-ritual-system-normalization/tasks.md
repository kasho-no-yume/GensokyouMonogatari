## 1. 框架

- [x] 1.1 RitualPattern/RitualMatch 数据模型 + RitualPatternLoader 重载监听器
- [x] 1.2 RitualMatcher（8 朝向枚举、自顶向下多级匹配、键位坐标收集）
- [x] 1.3 标签数据 gensokyou:ritual_stones + 首份 summon_circle.json
- [x] 1.4 /gs_ritual_capture 采集命令（RegisterCommandsEvent）

## 2. 祭品台

- [x] 2.1 RitualPedestalBlock + BE（单槽存取/NBT）
- [x] 2.2 RitualPedestalRenderer 悬浮渲染 + 注册
- [x] 2.3 方块资产/贴图占位/语言条目/创造标签/方块物品

## 3. 迁移与收尾

- [x] 3.1 召唤催化剂/发电机核心/淬炼祭坛三处切换 RitualMatcher，删除 MultiblockMatcher
- [x] 3.2 gradlew build 通过 + 游戏内验证：召唤环仍可用、采集命令输出正确、祭品台悬浮显示
