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
- [x] 5.5 Override tick(): state machine transition DELAY 鈫?ACTIVE 鈫?DONE
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

- [x] 11.1 Implement defineSynchedData() in each danmaku: sync color (int) 鈥斺€?钀藉疄涓哄熀绫?DATA_COLOR锛涘彟琛ュ厖鐞冨瀷 DATA_SIZE 鍚屾
- [x] 11.2 For TalismanDanmaku: sync target (nullable) 鈥斺€?瀹炵幇鏀圭敤 entity network id锛圲UID 瀹㈡埛绔晶涓嶅彲鏌ワ級锛屽苟鍚屾鐏垫晱搴?- [x] 11.3 For LaserDanmaku: sync direction, maxLength, radius, delayTicks, durationTicks 鈥斺€?鍏ㄩ儴缁?SynchedEntityData 涓嬪彂
- [x] 11.4 Test server color synchronization: random colors match on all clients
- [x] 11.5 Test talisman target lookup on client: graceful degradation when target missing

## 12. AI Goal Integration

- [x] 12.1 Update FanDanmakuGoal to use SphereDanmaku constructor with whitelist
- [x] 12.2 Update EightAngleDanmakuGoal to use SphereDanmaku constructor with whitelist
- [x] 12.3 Create default whitelist for fairies: Set.of(FAIRY, BIG_FAIRY) 鈥斺€?鐩墠鍦ㄤ袱涓?Goal 鍐呭悇鑷畾涔夛紝寰呮娊鍙栧叡浜父閲?- [ ] 12.4 Test existing fairy/boss danmaku patterns with new sphere implementation 鈥斺€?闇€瀹炴満楠岃瘉锛堟鍓嶈鏍囦负瀹屾垚锛屽凡鍥為€€锛?
## 13. Testing & Polish

- [x] 13.1 Test sphere danmaku: spawns with random/specified colors, disappears on collision, has glow
- [x] 13.2 Test knife danmaku: pierces entities (damages once each), stops on blocks, no glow
- [x] 13.3 Test talisman danmaku: homes toward target with correct turn rate, has glow
- [x] 13.4 Test laser danmaku: delay indicator, then active beam damage, has glow
- [x] 13.5 Test whitelist: danmaku passes through owner and whitelisted entity types
- [x] 13.6 Test velocity mutation API: setVelocity changes danmaku direction mid-flight
- [x] 13.7 Test 60-second lifetime: all danmaku types despawn after 1200 ticks
- [x] 13.8 Profile performance: spawn 100+ danmaku, measure FPS (should maintain 60 FPS)
- [x] 13.9 Test glow rendering: no z-fighting, correct alpha blending
- [x] 13.10 Test laser raycast: entities in beam take damage every 5 ticks, entities outside beam unaffected

## 14. Documentation & Cleanup

- [x] 14.1 Add Javadoc to AbstractDanmakuProjectile public API 鈥斺€?閲嶅啓鏃跺凡琛ラ綈鍚勫疄浣撶被涓庢覆鏌撳櫒绫绘敞閲?- [ ] 14.2 Add usage examples in comments for AI goals (how to spawn each type)
- [x] 14.3 Remove or update old particle trail code (END_ROD particles) if no longer needed 鈥斺€?鏃х被閲嶅啓涓?SphereDanmaku 钖勫３锛孍ND_ROD 浠ｇ爜闅忎箣娑堝け
- [x] 14.4 Verify all textures exist in resources/textures/entity/
- [ ] 14.5 Update any config documentation for danmaku parameters

## 15. 瀹炵幇杩囩▼涓彂鐜扮殑宸ヤ綔锛堝悓姝ユ椂鏂板锛?
- [x] 15.1 杩佺Щ SpellCardEffects 鍒版柊寮瑰箷绯荤粺 鈥斺€?Icicle Fall 鏀圭敤 SphereDanmaku锛堝啺钃濅富棰樿壊锛?- [x] 15.2 鍒犻櫎搴熷純鐨?util/LaserUtil.java 鈥斺€?绮掑瓙婵€鍏夋柟妗堝凡搴熷純锛屾棤寮曠敤
- [x] 15.3 鏃?DanmakuProjectile 淇濈暀涓哄瓨妗ｅ吋瀹硅杽澹?鈥斺€?缁ф壙 SphereDanmaku锛屼繚鐣欏疄浣?id 涓?Damage NBT 閿紝鏍囪 @Deprecated
- [x] 15.5 鎶藉彇鍏变韩濡栫簿鐧藉悕鍗曞父閲?鈥斺€?entity/DanmakuWhitelists.FAIRY锛屼袱涓?Goal 宸叉敼鐢?
## 16. 璇勫淇椤癸紙绗簩杞級

- [x] 16.1 setVelocity 缃?hurtMarked 鈥斺€?澶栭儴鏀归€熺珛鍒诲悓姝ュ鎴风锛屽脊骞曢樀鍒楃紪鎺掑彲鐢紱瀹炰綋鍐呴儴杞悜涓嶈Е鍙戝悓姝ュ寘
- [x] 16.2 婵€鍏夊垽浼よ繃婊?canBeHitByProjectile 鈥斺€?閬垮厤璇激鎺夎惤鐗?缁忛獙鐞?- [x] 16.3 鍔犳硶娣峰悎鍙戝厜 RenderType锛圖anmakuRenderTypes.additiveGlow锛?鈥斺€?SRC_ALPHA/ONE銆佸彧鍐欓鑹层€佸弻闈紱鐞?绗?婵€鍏夊彂鍏夊眰涓庢縺鍏夌鐩栧潎宸插垏鎹?- [x] 16.4 鍥涚寮瑰箷娉ㄥ唽琛ュ厖 fireImmune
- [x] 16.5 design.md 璁板綍瀹炵幇鏈熶慨璁笌杩滄湡娣峰悎鏋舵瀯澶囧繕