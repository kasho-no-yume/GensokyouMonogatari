# Legacy hit and ownership inventory

本清单用于后续 gameplay/server 阶段接线，core 阶段不调用这些服务。

| 依赖 | 当前来源 | 新阶段接线方向 |
|---|---|---|
| owner | `AbstractDanmakuProjectile` / BOSS 实体引用 | `Encounter` owner UUID；运行时解析 |
| 基础伤害 | `danmaku_damage` 属性与 `DanmakuEmitter` | `DanmakuHitContext` 在服务端命中时读取 |
| 轨道伤害倍率 | `Track.damageScale` 与 shot 倍率 | lane/member profile 的显式乘区 |
| 玩家命中 | `DanmakuHitScan` 的 LivingEntity 查询 | 纯几何扫掠结果 + 服务端适配 |
| 方块命中 | `Level.clip` / `DanmakuHitScan` | 后续碰撞 profile 决定是否启用 |
| 擦弹 | 现有弹体/玩家判定入口 | 后续从实体身份抽离为 member identity |
| 白名单 | `DanmakuEmitter.NO_WHITELIST`、`DanmakuWhitelists` | HitContext 过滤器 |
| owner 属性 | `LivingEntity` 属性系统 | 命中时读取存活 owner，owner 消失时清理攻击 |
| 激光 | `LaserDanmaku` 的射线和地形裁剪 | 后续 Laser collision profile |
| 悬停/静止弹 | AABB 相交路径 | 后续静止几何相交入口 |
| 派生 | `AbstractDanmakuProjectile` 的 split/trail | 后续 batch 原子 ReplaceMembers |

禁止在 core 中引入 `Level`、`Entity`、`LivingEntity`、`ServerLevel` 或网络包类型。

## 已核实的命中语义差异

`SphereDanmaku.onHitEntity` 调用 `living.hurt(ModDamageTypes.danmaku(this,getOwner()),this.damage)`。
damage在构造时赋值，`DanmakuEmitter`在发射时乘shot倍率；所以旧球弹保存的是出生时伤害，不能把“命中时读owner属性”写成旧行为。后续HitContext需要保留owner UUID与lane/member倍率，并在命中时读取属性，这是明确的新契约。

`SphereDanmaku.spawnTrailChild/spawnSplitChildren` 继承damage、fromWeapon、critMult，white list传空集合；其预算逐颗检查，分裂可部分成功。新batch原子准入不能直接复用该循环。

球弹命中记录包含吸收量与生命差分；`hurt=false`不代表没有扣吸收量。碰撞桥接必须保留该统计口径。
`ModDamageTypes.danmaku`的directEntity是弹本体、attacker是owner；武器汲取等规则依赖directEntity类型，非实体BOSS接线必须单独审计事件消费者，不能伪造武器弹。

## 事件消费者接线清单

- `DamageEventHandler.onIncomingDamage`：按DANMAKU伤害类型分流，先MU_POWER，再DANMAKU_PROTECT，再玩家随机擦弹与指数护壁；不依赖directEntity。新后端必须继续走同一伤害类型和事件链，不能重复执行擦弹。
- `SpiritLeechHandler.onDamage`：要求directEntity为AbstractDanmakuProjectile且fromWeapon，attacker为ServerPlayer；BOSS数据弹不得进入玩家武器汲取。
- `SpellCardCombatHandler.onIncomingDamage`：鲜花之铠用`direct instanceof Projectile`辨认弹射物。新数据弹必须在gameplay阶段新增明确的命中来源识别，否则会绕过鲜花之铠。疫符回敬与黑暗结界依赖attacker，须保留owner。
- 新HitContext至少携带owner UUID、member identity、来源类别（BOSS/武器/技能）、lane/member倍率、伤害类型、碰撞profile和有效时窗；服务端适配负责映射上述事件语义。
