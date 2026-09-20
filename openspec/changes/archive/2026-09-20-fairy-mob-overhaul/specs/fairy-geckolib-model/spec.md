## ADDED Requirements

### Requirement: GeckoLib 硬依赖
模组 SHALL 硬依赖 GeckoLib（`software.bernie.geckolib:geckolib-neoforge-1.21.1`，版本 `[4.9,)`）：`build.gradle` 声明依赖与 Cloudsmith 仓库，`neoforge.mods.toml` 声明 required；缺失时游戏 MUST 以清晰的依赖缺失提示拒绝加载。

#### Scenario: 缺失依赖
- **WHEN** 玩家未安装 GeckoLib 而安装本 mod
- **THEN** 加载被拒绝并提示缺少 geckolib

### Requirement: 小妖精模型资产
模组 SHALL 提供小妖精的 GeckoLib 资产：Bedrock 几何 `assets/gensokyou/geo/entity/lesser_fairy.geo.json`、动画 `assets/gensokyou/animations/entity/lesser_fairy.animation.json`、贴图 `assets/gensokyou/textures/entity/lesser_fairy.png`（128×128，cutout）；`FairyEntity` MUST 实现 `GeoAnimatable`，并 MUST 由 `GeoEntityRenderer` 渲染，渲染缩放 MUST 使模型总高对齐约 1 格（与 0.45×1.0 碰撞箱一致）。

#### Scenario: 模型替换生效
- **WHEN** 小妖精在游戏中生成
- **THEN** 以 GeckoLib 东方风模型渲染，尺寸约 1 格高，贴图路径指向 `gensokyou:` 命名空间自有资产

### Requirement: 小妖精动画状态机
小妖精 SHALL 具备 idle / fly / cast 三态动画：静止悬停播放 `idle`，水平移动超阈值播放 `fly`，攻击时触发一次 `cast`。

#### Scenario: 悬停与移动
- **WHEN** 小妖精静止悬停 / 水平快速移动
- **THEN** 分别播放 `idle` / `fly` 循环动画

#### Scenario: 施法动画
- **WHEN** 小妖精发动任一攻击变体
- **THEN** 触发一次 `cast` 动画

### Requirement: 小妖精性能约束
小妖精渲染 MUST 在配置的追踪距离内工作，并在同屏多只（目标 ≤10、上限 ≤30）时保持可接受帧率；如不达标 SHALL 提供降级手段（远距离裁减翅膀细分或收紧渲染距离）。

#### Scenario: 多只同屏
- **WHEN** 同屏出现约 10 只小妖精
- **THEN** 帧率保持可接受；必要时启用 LOD 降级
