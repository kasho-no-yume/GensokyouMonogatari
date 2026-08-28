## 1. 骨架与占位资产管道

- [x] 1.1 清理 MDK 示例（example_* 与模板 Config），建立 com.bitsson.gensokyou 包结构（registry/item/block/entity/effect/config/client），主类改造为构造器注入并挂载注册器，gradlew build 通过
- [x] 1.2 实现 GensokyouConfig（COMMON：boss/fairy/danmaku/items/spawn 五节，默认值对照旧代码与设计 D2）
- [x] 1.3 编写占位资产提取脚本：从 gradle 缓存的 Minecraft 客户端 jar 拷贝 heart_of_the_sea/diamond_block/alex 到自有路径（最终命名），失败时输出手工指引
- [x] 1.4 建立 docs/asset-placeholder-list.md 占位清单并录入初始条目
- [x] 1.5 注册创造标签 gensokyou:gensokyou

## 2. 弹幕战斗基件

- [x] 2.1 data/gensokyou/damage_type/danmaku.json + minecraft:bypasses_armor 标签 JSON + ModDamageTypes（ResourceKey/Holder/DamageSource 工厂）
- [x] 2.2 DanmakuProjectile extends ThrowableProjectile：零重力、伤害入 NBT、仅服务端命中结算、实现 ItemSupplier
- [x] 2.3 注册弹幕 EntityType + ThrownItemRenderer 渲染
- [x] 2.4 DanmakuProtectEffect（(9-等级)/10 减伤、≥10 免疫）+ LivingIncomingDamageEvent 倍率应用

## 3. 物品层

- [x] 3.1 材料道具 ppoint/bpoint/spellcardstar/brokenspellcardstar + 円（堆叠/贴图/语言键按最终命名）
- [x] 3.2 拉维坦剑（白值代码常量 + TODO 注记），加入芙兰稀有掉落池
- [x] 3.3 SpellCardItem 基类（stacksTo(1)、消耗规则、效果钩子）
- [x] 3.4 OrbitYinYangOrb 实体（NBT 环绕状态、setPos 更新、到期结算 min(maxHP/2,cap)、宿主失效自毁）+ billboard 渲染
- [x] 3.5 无想封印卡（读配置生成 6 玉）；光反卡（纯占位）
- [x] 3.6 全部物品模型 JSON/blockstate 占位搭建 + zh_cn/en_us 语言文件

## 4. 妖精生态

- [x] 4.1 FairyEntity（游走+周期单发弹幕 AI，数值走配置）+ 人形缩放渲染
- [x] 4.2 BigFairyEntity（三连扇形弹幕、高数值）+ 掉引导书 loot table
- [x] 4.3 biome modifier 数据驱动刷怪（#minecraft:is_overworld，权重进 spawn 节）
- [x] 4.4 GuideBookItem（右键聊天栏分页介绍文本，不消耗）

## 5. 芙兰朵露 BOSS

- [x] 5.1 FlandreEntity + createAttributes 属性绑定（含移速归一 0.3）+ ServerBossEvent 追踪增删与百分比刷新
- [x] 5.2 Goal 移植：FlashToNearestPlayerGoal / RandomDanmakuGoal / EightAngleDanmakuGoal（间隔与伤害走配置）
- [x] 5.3 FakeFlandreEntity（独立 registry name）+ FourOfAKindGoal 生成分身
- [x] 5.4 本体 die() 钩子清除 30 格内分身；loot_table JSON（双星+円+拉维坦剑稀有权重+经验）
- [x] 5.5 HumanoidMobRenderer(ModelLayers.ZOMBIE) + 自有皮肤路径渲染本体与分身

## 6. 召唤仪式

- [x] 6.1 RitualCoreBlock / RitualStoneBlock（标准立方体占位模型与 blockstate）+ 注册
- [x] 6.2 MultiblockMatcher 结构校验工具（偏移表扫描+短缓存）
- [x] 6.3 SummonCatalystItem 激活逻辑（校验→消耗→核心上方生成 BOSS；无效提示不消耗）
- [x] 6.4 合成配方 JSON：仪式石/核心/催化剂（材料来自本阶段产出闭环）

## 7. 验证收尾

- [x] 7.1 gradlew build 通过 + 客户端/专用服务器启动零报错
- [x] 7.2 游戏内冒烟：完整走通 引导链→刷怪→合成仪式→召唤→BOSS 战→掉落 闭环，逐条核对 specs 场景
- [x] 7.3 对照 project.md §5 审计资产引用（无 minecraft: 直接引用）并复核占位清单
