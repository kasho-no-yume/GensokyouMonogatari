## 1. 灵力伤害属性地基

- [x] 1.1 `SpiritPowerData` 加 `spiritDamage` 字段（optionalFieldOf 默认=配置值），更新 CODEC 与初始值
- [x] 1.2 `GensokyouConfig` 新增 `BASE_SPIRIT_DAMAGE`、`SPIRIT_DAMAGE_PER_TEMPER`
- [x] 1.3 `TemperingBehavior` 淬炼成功时同步提升 spiritDamage；提供统一读取入口（ModAttachments 静态方法）
- [x] 1.4 runServer 验证旧档加载与新档存取正常

## 2. 数据组件与核物品框架

- [x] 2.1 注册数据组件 `weapon_slots`（三槽 ItemStack record + Codec）与 `rune_affixes`（词条列表 Codec）
- [x] 2.2 定义 `FirePattern` record（danmakuFactory/count/spread/speed/lifetime + 激光参数 + 灵符拾取标志）与核定值字段（coreBaseMult/spiritCost/attackRateTicks/requiredTier）
- [x] 2.3 实现 `BulletCoreItem` / `WeaponLevelCoreItem` / `AmpCoreItem` 基类（构造定值 + tooltip 显示需要武器 Lv.N）
- [x] 2.4 实现 `WeaponSlotsHelper`：读取等级（slot2 栈即等级）、写回时统一校验（requiredTier <= 等级，不满足弹出返还/掉落）、交换返回旧核语义

## 3. 主武器与占位内容注册

- [x] 3.1 主武器物品：stacksTo(1)、无耐久、ATTACK_DAMAGE=0 属性修饰符、潜行右键打开菜单入口
- [x] 3.2 首批占位核注册：单发球/散弹球/飞刀/灵符/激光机枪/激光炮 + 武器等级核 Lv.1~3 + 增幅核（按 tier）；数值全部走 config
- [x] 3.3 GensokyouConfig 新增武器组配置（各核数值、weaponLevelMult 表、灵符拾取距离等）
- [x] 3.4 创造标签页、语言文件（en_us/zh_cn）、贴图占位接入 tools 管道
- [x] 3.5 占位合成配方 JSON（武器本体）

## 4. 装入 GUI

- [x] 4.1 `WeaponCoreMenu`：3 核槽 Container，打开时从武器组件载入，setChanged 即时写回（走 WeaponSlotsHelper 校验）
- [x] 4.2 `WeaponCoreScreen`：槽位渲染、不可放入的核灰显、悬停 tooltip「需要武器 Lv.N」
- [x] 4.3 stillValid 手持武器校验：武器失效即关菜单并返还容器余核
- [x] 4.4 潜行右键 openMenu 网络链路（参照 RitualCoreMenu 范式）

## 5. 发射系统

- [x] 5.1 `use()` 服务端流程：冷却门控（getCooldowns）→ 灵力校验（不足提示无消耗）→ 按 firePattern 生成弹幕 → addCooldown
- [x] 5.2 散弹 pattern：count 枚扇形分布生成；激光 pattern：eye 位+look 方向实例化 LaserDanmaku（机枪=短延迟短持续、炮=长延迟长持续）
- [x] 5.3 `DanmakuTargetPicker`：眼位沿 look 射线 clip 实体，取配置距离内第一个非玩家 LivingEntity；灵符核发射时传入 TalismanDanmaku，落空传 null
- [ ] 5.4 长按连发验证（原版右键重试 + 冷却门控），确认无冷却绕过路径

## 6. RuneGenerator 与词条结算

- [x] 6.1 词条池配置格式：`{affixId, effectType(damage_pct|attack_rate_pct|spirit_cost_pct), min, max, weight, minTier}` 列表（占位数值）
- [x] 6.2 RuneGenerator：按 tier 过滤 → 权重 roll 词条 → 取值写入 `rune_affixes` 组件；预留组件重写接口
- [x] 6.3 结算接入：发射时消费 Σdamage_pct、攻速词条缩冷却、灵力消耗词条乘 cost

## 7. 客户端目标准星

- [x] 7.1 HUD 层叠加（RenderGuiLayerEvent.Post）：持武器且 slot1 为灵符核时每帧跑 DanmakuTargetPicker，命中即绘制 2D 准星框标记
- [ ] 7.2 验证不用实体发光、不影响既有 HUD（灵力条/技能槽）

## 8. 验证与收尾

- [x] 8.1 `gradlew.bat build` 通过；runServer 注册表无错误
- [ ] 8.2 游戏内验收：对照 spec 全部 Scenario（发射/冷却/灵力不足/GUI 写回/等级回落连带取核/交换返回/灵符拾取与落空退化/玩家不作目标/无近战/淬炼双提升/旧档迁移）
- [ ] 8.3 存档重载回归：武器组件、已装核、词条、属性值持久化一致
