## 1. Base Architecture

- [x] 1.1 Refactor existing DanmakuProjectile.java into AbstractDanmakuProjectile base class
- [x] 1.2 Add common fields: damage, lifetime (1200 ticks), color (int RGB), whitelist (Set<EntityType<?>>)
- [x] 1.3 Implement velocity mutation API: setVelocity(), setDirection(), addVelocity()
- [x] 1.4 Add abstract methods: getCollisionBehavior(), hasGlowEffect()
- [x] 1.5 Implement common tick() logic: lifetime tracking, velocity application
- [x] 1.6 Implement NBT serialization for common fields (damage, color, lifetime, whitelist)
- [x] 1.7 Add whitelist collision filtering helper: isWhitelisted(Entity)

## 2. Sphere Danmaku Implementation

- [x] 2.1 Create SphereDanmaku.java extending AbstractDanmakuProjectile
- [x] 2.2 Add constructor: SphereDanmaku(Level, LivingEntity owner, float damage, int color, float size, Set<EntityType<?>> whitelist)
- [x] 2.3 Implement random color generation when color not specified (server-side)
- [x] 2.4 Implement onHitEntity(): damage entity, discard danmaku (respect whitelist)
- [x] 2.5 Implement onHitBlock(): discard danmaku immediately
- [x] 2.6 Store size parameter and expose via getter for renderer
- [x] 2.7 Add NBT serialization for sphere-specific fields (size)
- [x] 2.8 Register SphereDanmaku entity type in ModEntityTypes

## 3. Knife Danmaku Implementation

- [x] 3.1 Create KnifeDanmaku.java extending AbstractDanmakuProjectile
- [x] 3.2 Add constructor: KnifeDanmaku(Level, LivingEntity owner, float damage, Set<EntityType<?>> whitelist)
- [x] 3.3 Add hitEntities field: Set<UUID> to track damaged entities
- [x] 3.4 Implement onHitEntity(): damage if not in hitEntities, add UUID to set, do NOT discard (respect whitelist)
- [x] 3.5 Implement onHitBlock(): discard danmaku immediately
- [x] 3.6 Override hasGlowEffect(): return false (no glow for knife)
- [x] 3.7 Add NBT serialization for hitEntities set
- [x] 3.8 Register KnifeDanmaku entity type in ModEntityTypes

## 4. Talisman Danmaku Implementation

- [x] 4.1 Create TalismanDanmaku.java extending AbstractDanmakuProjectile
- [x] 4.2 Add constructor: TalismanDanmaku(Level, LivingEntity owner, float damage, int color, Entity target, double sensitivity, Set<EntityType<?>> whitelist)
- [x] 4.3 Add fields: target (Entity), targetUUID (UUID), sensitivity (double degrees/sec)
- [x] 4.4 Implement default red color (0xFF0000) when color not specified
- [x] 4.5 Implement tickHoming() method: calculate steering toward target, apply turn rate limit
- [x] 4.6 Implement vector steering math: steerTowards(currentDir, targetDir, maxTurnRadians)
- [x] 4.7 Override tick(): call super.tick() then tickHoming() if target exists
- [x] 4.8 Implement onHitEntity(): damage entity, discard danmaku (respect whitelist)
- [x] 4.9 Implement onHitBlock(): discard danmaku immediately
- [x] 4.10 Add setTarget() and setSensitivity() mutation methods
- [x] 4.11 Add NBT serialization for target UUID and sensitivity
- [x] 4.12 Handle client-side target lookup: resolve UUID to entity, gracefully degrade if not found
- [x] 4.13 Register TalismanDanmaku entity type in ModEntityTypes

## 5. Laser Danmaku Implementation

- [x] 5.1 Create LaserDanmaku.java extending AbstractDanmakuProjectile
- [x] 5.2 Add constructor: LaserDanmaku(Level, Vec3 position, Vec3 direction, float damage, int color, double maxLength, double radius, double delayTime, double duration, Set<EntityType<?>> whitelist)
- [x] 5.3 Add fields: direction (Vec3), maxLength, radius, delayTicks, durationTicks, phase (enum: DELAY/ACTIVE/DONE)
- [x] 5.4 Implement default red color (0xFF0000) when color not specified
- [x] 5.5 Override tick(): state machine transition DELAY → ACTIVE → DONE
- [x] 5.6 Implement tickActive(): raycast damage every 5 ticks
- [x] 5.7 Implement raycastEntitiesInBeam(): AABB pre-filter + line-segment distance check
- [x] 5.8 Implement distanceToLineSegment() helper method
- [x] 5.9 Calculate actual laser length: min(maxLength, distance to first block in direction)
- [x] 5.10 Override setVelocity/setDirection: no-op (laser is stationary)
- [x] 5.11 Add NBT serialization for laser-specific fields (direction, maxLength, radius, phase, etc.)
- [x] 5.12 Register LaserDanmaku entity type in ModEntityTypes

## 6. Base Renderer Infrastructure

- [x] 6.1 Create AbstractDanmakuRenderer<T extends AbstractDanmakuProjectile> base class
- [x] 6.2 Implement renderGlowLayer() method: scale pose by 1.2x, render with alpha=0.4, full brightness
- [x] 6.3 Add getRenderType() helper: choose between entityCutoutNoCull and entityTranslucentEmissive
- [x] 6.4 Implement color application: extract RGB from int, apply to vertex colors
- [x] 6.5 Add vertex() helper method for consistent vertex emission

## 7. Sphere Danmaku Renderer

- [x] 7.1 Create SphereDanmakuRenderer.java extending AbstractDanmakuRenderer<SphereDanmaku>
- [x] 7.2 Implement render(): draw billboard quad facing camera
- [x] 7.3 Apply sphere size from entity.getSize()
- [x] 7.4 Apply color from entity.getColor()
- [x] 7.5 Call renderGlowLayer() after base rendering
- [x] 7.6 Register SphereDanmakuRenderer in GensokyouClient.onRegisterRenderers()
- [x] 7.7 Add texture to GensokyouTextures (SPHERE_DANMAKU)

## 8. Knife Danmaku Renderer

- [x] 8.1 Create KnifeDanmakuRenderer.java extending AbstractDanmakuRenderer<KnifeDanmaku>
- [x] 8.2 Implement render(): draw elongated quad (1.5 blocks long, 0.2 blocks wide)
- [x] 8.3 Rotate quad to align with velocity direction (use Axis rotations)
- [x] 8.4 Do NOT call renderGlowLayer() (knife has no glow)
- [x] 8.5 Register KnifeDanmakuRenderer in GensokyouClient.onRegisterRenderers()
- [x] 8.6 Add texture to GensokyouTextures (KNIFE_DANMAKU)

## 9. Talisman Danmaku Renderer

- [x] 9.1 Create TalismanDanmakuRenderer.java extending AbstractDanmakuRenderer<TalismanDanmaku>
- [x] 9.2 Implement render(): draw flat rectangular billboard facing camera
- [x] 9.3 Apply color from entity.getColor()
- [x] 9.4 Call renderGlowLayer() after base rendering
- [x] 9.5 Register TalismanDanmakuRenderer in GensokyouClient.onRegisterRenderers()
- [x] 9.6 Add texture to GensokyouTextures (TALISMAN_DANMAKU)

## 10. Laser Danmaku Renderer

- [x] 10.1 Create LaserDanmakuRenderer.java extending AbstractDanmakuRenderer<LaserDanmaku>
- [x] 10.2 Implement renderDelayIndicator(): semi-transparent red line from start to end
- [x] 10.3 Implement renderActiveLaser(): cylinder geometry with hemispherical caps
- [x] 10.4 Create cylinder vertices: circular cross-section with 12-16 segments
- [x] 10.5 Create hemisphere cap vertices at laser end
- [x] 10.6 Apply color from entity.getColor()
- [x] 10.7 Call renderGlowLayer() for active phase (scale cylinder radius by 1.2x)
- [x] 10.8 Register LaserDanmakuRenderer in GensokyouClient.onRegisterRenderers()
- [x] 10.9 Add texture to GensokyouTextures (LASER_DANMAKU)

## 11. Network Synchronization

- [x] 11.1 Implement defineSynchedData() in each danmaku: sync color (int) —— 落实为基类 DATA_COLOR；另补充球型 DATA_SIZE 同步
- [x] 11.2 For TalismanDanmaku: sync target (nullable) —— 实现改用 entity network id（UUID 客户端侧不可查），并同步灵敏度
- [x] 11.3 For LaserDanmaku: sync direction, maxLength, radius, delayTicks, durationTicks —— 全部经 SynchedEntityData 下发
- [ ] 11.4 Test server color synchronization: random colors match on all clients
- [ ] 11.5 Test talisman target lookup on client: graceful degradation when target missing

## 12. AI Goal Integration

- [x] 12.1 Update FanDanmakuGoal to use SphereDanmaku constructor with whitelist
- [x] 12.2 Update EightAngleDanmakuGoal to use SphereDanmaku constructor with whitelist
- [x] 12.3 Create default whitelist for fairies: Set.of(FAIRY, BIG_FAIRY) —— 目前在两个 Goal 内各自定义，待抽取共享常量
- [ ] 12.4 Test existing fairy/boss danmaku patterns with new sphere implementation —— 需实机验证（此前误标为完成，已回退）

## 13. Testing & Polish

- [ ] 13.1 Test sphere danmaku: spawns with random/specified colors, disappears on collision, has glow
- [ ] 13.2 Test knife danmaku: pierces entities (damages once each), stops on blocks, no glow
- [ ] 13.3 Test talisman danmaku: homes toward target with correct turn rate, has glow
- [ ] 13.4 Test laser danmaku: delay indicator, then active beam damage, has glow
- [ ] 13.5 Test whitelist: danmaku passes through owner and whitelisted entity types
- [ ] 13.6 Test velocity mutation API: setVelocity changes danmaku direction mid-flight
- [ ] 13.7 Test 60-second lifetime: all danmaku types despawn after 1200 ticks
- [ ] 13.8 Profile performance: spawn 100+ danmaku, measure FPS (should maintain 60 FPS)
- [ ] 13.9 Test glow rendering: no z-fighting, correct alpha blending
- [ ] 13.10 Test laser raycast: entities in beam take damage every 5 ticks, entities outside beam unaffected

## 14. Documentation & Cleanup

- [x] 14.1 Add Javadoc to AbstractDanmakuProjectile public API —— 重写时已补齐各实体类与渲染器类注释
- [ ] 14.2 Add usage examples in comments for AI goals (how to spawn each type)
- [ ] 14.3 Remove or update old particle trail code (END_ROD particles) if no longer needed
- [ ] 14.4 Verify all textures exist in resources/textures/entity/
- [ ] 14.5 Update any config documentation for danmaku parameters

## 15. 实现过程中发现的工作（同步时新增）

- [ ] 15.1 迁移 SpellCardEffects 到新弹幕系统 —— spirit/SpellCardEffects.java:88 仍在使用旧 DanmakuProjectile（玩家符卡技能）
- [ ] 15.2 删除废弃的 util/LaserUtil.java —— 粒子激光方案已废弃，当前无任何引用
- [ ] 15.3 决定旧 DanmakuProjectile 实体的去留 —— SpellCardEffects 迁移后移除注册，或保留作存档兼容
- [ ] 15.4 替换占位纹理 —— 四种弹幕与 laser_cap.png 均为旧 danmaku.png 复制品
- [ ] 15.5 抽取共享妖精白名单常量 —— 消除 FanDanmakuGoal / EightAngleDanmakuGoal 中的重复定义