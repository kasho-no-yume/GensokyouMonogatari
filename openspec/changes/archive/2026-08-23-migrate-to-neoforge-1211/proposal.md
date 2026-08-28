# Proposal: 将 Gensokyou 从 1.12.2 Forge 迁移到 1.21.1 NeoForge

## Why

原仓库 kasho-no-yume/Gensokyou 是一个 1.12.2 Forge 的未完成开发版（作者已声明"后面打算直接上新版本"）。本地工程已搭好 1.21.1 NeoForge (ModDevGradle) 模板。需要把旧代码中有价值的功能骨架迁移过来，并借机规范化代码与资产，形成可继续开发的核心战斗闭环。

## What Changes

- **平台迁移**：1.12.2 Forge → 1.21.1 NeoForge（NeoForge 21.1.x / ModDevGradle / Parchment）
- **规范化**：
  - 包名 `com.bitsson2` → `com.bitsson.gensokyou`；类名统一 PascalCase
  - 注册方式从 `ForgeRegistries.register()` 改为 `DeferredRegister` 全家桶
  - 清除死代码：`Reflection.java`、被注释的旧 theWorld 实现、未注册的 `gensokyouIntroBook`
  - 清理本地模板残留示例（example_block/example_item/example_tab）
- **迁移并修正既有功能**：
  - 物品：光弹（lightOrb）、拉维坦剑、P 点/B 点/符卡星/碎符卡星、符卡基类 + 两张符卡
  - 弹幕系统：阴阳玉投射物、danmaku 数据驱动伤害类型（bypasses_armor 标签）、弹幕护盾/无力(MuPower)两个状态效果、受击伤害倍率事件
  - BOSS 芙兰朵露：500 血级 Boss 实体、ServerBossEvent 血条、4 个 AI Goal（闪现/分身/随机弹幕/八向弹幕）、分身实体、渲染、掉落物
  - 无想封印符卡：六玉环绕仪式（重写原 tick 循环中直接改坐标的实现）
  - **theWorld 符卡重新设计**：放弃旧的失败实现，改用"冻结非玩家实体逻辑"的新方案
- **配置化数值**：BOSS 属性、弹幕伤害、状态效果倍率、符卡参数等不再硬编码，统一走 ModConfigSpec 配置文件
- **资产迁移**：模型/贴图按新约定路径搬运；`.lang` → JSON 语言文件
- **暂不迁移**（后置变更）：幻想乡维度（届时数据包化）、Patchouli 导入书、刷怪蛋等外围内容

## Capabilities

### New Capabilities

- `mod-registration-skeleton`: 模组注册骨架 —— 主类、DeferredRegister 体系、创造模式标签、统一配置文件接入
- `items-and-points`: 物品体系 —— 光弹、拉维坦剑、P/B 点、符卡星/碎星、符卡基类及具体符卡物品
- `danmaku-system`: 弹幕系统 —— 阴阳玉投射物实体、danmaku 伤害类型、弹幕护盾/无力效果、弹幕伤害倍率规则
- `flandre-boss`: 芙兰朵露 BOSS —— Boss 实体、血条、AI Goals（闪现/分身/随机魔法弹幕/八向弹幕）、分身、掉落
- `spellcard-effects`: 符卡主动效果 —— 无想封印（六玉环绕）与 theWorld（时间冻结，重新设计）
- `game-config`: 统一数值配置 —— 所有可调数值经 ModConfigSpec 管理，含默认值表

### Modified Capabilities

（无——项目尚无既有 spec。）

## Impact

- **代码**：本地 `src/main/java/com/bitsson/gensokyou/**` 全量重写填充；参考远端 `com.bitsson2/**` 逐模块翻译
- **注册 ID**：保持 modid `gensokyou` 不变；实体/物品 registry name 沿用小写命名（yinyangorb、musoufuuin 等），修复旧代码中两个实体共用 `gensokyou:flandre` 的 bug
- **资源**：`src/main/resources/assets/gensokyou/` 新约定路径；需核实 point/spellcard 贴图引用完整性
- **依赖**：本期不引入 Patchouli 等第三方依赖；仅 NeoForge 本体
- **风险点**：1.21.1 渲染层（ModelLayer/EntityRenderer）为纯重写；数据驱动 damage type 为范式转换；旧版直接操作实体坐标的逻辑必须改为安全 API
