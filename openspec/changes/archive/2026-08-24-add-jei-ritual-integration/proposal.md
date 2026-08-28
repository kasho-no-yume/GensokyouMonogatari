# Proposal: add-jei-ritual-integration

## Why

仪式系统已数据驱动（6 个仪式的 RitualPattern JSON），但玩家在游戏内没有任何途径查看多方块搭建方式——只能翻源码里的 JSON。接入 JEI 后，玩家在 JEI 中即可直观看到各仪式的结构分层与所需方块，显著降低入门门槛；同时为未来仪式行为配方（分解/聚合输入输出等）预留展示通道。

## What Changes

- 构建侧引入 JEI 依赖（compileOnly API + localRuntime 完整包，BlameJared maven），JEI 仅存在于开发运行时，不进入发布依赖
- 新增 `com.bitsson.gensokyou.jei` 兼容包：JEI 插件入口（NeoForge entrypoint，软依赖，JEI 不存在时零加载）
- 注册一个自定义 recipe category「仪式结环」：每个 RitualPattern 展示为一个"配方"
  - 配方区按层级（levels）分组绘制分层物品网格
  - palette 的 tag 条目显示代表物品；锚点核心高亮标注
- 查找入口：在祭仪核心、祭品台及各催化剂物品上按 U（用途）列出关联仪式
- 语言条目：category 标题 zh_cn / en_us 同步

## Capabilities

### New Capabilities

- `jei-ritual-display`: JEI 联动展示仪式多方块结构——插件入口的软依赖安全性、每个 RitualPattern 映射为一个 JEI 条目、分层网格渲染规则（tag 代表物、锚点标注、多级 levels）、查找入口挂载点

### Modified Capabilities

<!-- 无：ritual-pattern-system 的格式与匹配行为不变，JEI 只读消费其数据 -->

## Impact

- **build.gradle / gradle.properties**：新增 jei_version 属性、BlameJared maven repository、三条依赖声明（均为 compileOnly/localRuntime，不传染发布产物）
- **新增代码**：`src/main/java/com/bitsson/gensokyou/jei/`（插件入口、category、recipe 包装类）；注册声明于 neoforge.mods.toml 的 entrypoints
- **只读依赖**：`RitualPatternLoader.all()` 静态表，不修改 ritual 包任何现有代码；单人开发场景客户端经共享 JVM 直接可用，dedicated server 远程客户端的图鉴展示为已知限制（非目标，见 design D7）
- **资产**：lang 文件两处追加；无贴图需求（复用方块/物品 icon）
- **风险边界**：dedicated server 上 JEI entrypoint 不触发客户端类加载；所有绘制逻辑仅客户端
