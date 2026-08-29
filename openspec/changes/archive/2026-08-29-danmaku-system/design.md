## Context

The current danmaku system consists of a single `DanmakuProjectile` class extending `ThrowableProjectile`, with basic sphere rendering via `BillboardRenderer`. It supports only basic projectile behavior: flies straight, disappears on collision, has particles. 

To achieve authentic Touhou-style bullet hell gameplay, we need four distinct danmaku types with unique behaviors: sphere (basic), knife (piercing), talisman (homing), and laser (stationary beam). Each requires different collision mechanics, rendering approaches, and parameter sets.

**Current Architecture:**
- `DanmakuProjectile.java` - Single entity class, 200 tick lifetime
- `BillboardRenderer.java` - Simple camera-facing quad renderer
- `FanDanmakuGoal.java`, `EightAngleDanmakuGoal.java` - AI goals that spawn danmaku

**Constraints:**
- NeoForge 21.1 / Minecraft 1.21.1 APIs
- Dual-side deterministic physics (minimal network sync)
- Must scale to 100+ bullets on screen simultaneously
- Client rendering performance critical

**Stakeholders:**
- Fairy/boss entities (shooters)
- Player (primary target)
- Future pattern systems (华丽的弹幕阵列)

## Goals / Non-Goals

**Goals:**
- Four fully functional danmaku types with spec-compliant behaviors
- Outer glow rendering for sphere, talisman, and laser (not knife)
- Whitelist-based collision filtering to prevent friendly fire
- Velocity mutation API for dynamic pattern creation
- Server-authoritative color synchronization
- 60-second lifetime for all danmaku types
- Deterministic dual-side physics with minimal network packets

**Non-Goals:**
- Advanced rendering optimization (instanced rendering, shader bloom) - defer until performance profiling
- Complex curved trajectories - velocity mutation API enables this, but pre-built curves are out of scope
- Danmaku-specific particle systems - keep existing particle trail or remove
- Custom damage types per danmaku - all use existing `ModDamageTypes.danmaku()`
- Save/load of in-flight danmaku - acceptable for them to disappear on world reload

## Decisions

### Decision 1: Abstract Base Class Architecture

**Choice:** Create `AbstractDanmakuProjectile` base class with four concrete subclasses.

**Rationale:**
- Shared logic: lifetime tracking, whitelist filtering, velocity mutation, NBT serialization
- Type-specific behavior: collision (pierce vs disappear), rendering hints, homing logic
- Clean separation: each subclass owns its unique mechanics

**Alternatives Considered:**
- **Composition over inheritance:** Use components (CollisionBehavior, RenderingHints) attached to single DanmakuProjectile class. Rejected because NeoForge entity system is inheritance-based, and type-specific logic (homing, laser phases) would still need conditional branches.
- **Interface-based:** DanmakuProjectile implements multiple interfaces (IPiercing, IHoming). Rejected because shared code (lifetime, whitelist) would need helper classes, adding complexity.

**Class Hierarchy:**
```
AbstractDanmakuProjectile extends ThrowableProjectile
  ├─ Common: damage, lifetime (1200 ticks), whitelist (Set<EntityType<?>>)
  ├─ Common: velocity mutation methods (setVelocity, setDirection, addVelocity)
  ├─ Common: color (int RGB), owner tracking
  ├─ Abstract: onHitEntity(), onHitBlock(), getCollisionBehavior()
  │
  ├─ SphereDanmaku
  │    └─ Disappears on entity/block hit, random color if unspecified
  │
  ├─ KnifeDanmaku
  │    └─ Pierces entities (track hit UUIDs), disappears on block hit
  │
  ├─ TalismanDanmaku
  │    └─ Homing logic in tick(), disappears on entity/block hit
  │
  └─ LaserDanmaku
       └─ Phase state machine (DELAY → ACTIVE → DONE), stationary, raycast damage
```

### Decision 2: Glow Effect Implementation (Layered Rendering)

**Choice:** Render glow as enlarged semi-transparent emissive duplicate layer (Option B from exploration).

**Rationale:**
- Simple to implement: render base entity, then render 1.2x scaled duplicate with alpha blending
- No shader complexity: works with vanilla render pipeline
- Good enough aesthetics: layered glow is standard in 2D bullet hell games
- Upgrade path clear: if performance is acceptable but visuals need improvement, add shader bloom later

**Alternatives Considered:**
- **Shader-based bloom:** True HDR bloom with shader pipeline. Rejected for initial implementation due to complexity and NeoForge shader API learning curve. May revisit if layered approach is insufficient.
- **Particle-based glow:** Spawn particles around bullet. Rejected due to particle system overhead (each bullet spawning 4-8 particles every tick = 400-800 particles for 100 bullets).

**Rendering Architecture:**
```
Per-danmaku renderer (e.g., SphereDanmakuRenderer):
  1. Render base shape with RenderType.entityCutoutNoCull(texture)
  2. If hasGlow():
       a. Scale pose stack by 1.2x
       b. Render same shape with RenderType.eyes(texture) or entityTranslucentEmissive
       c. Alpha = 0.4, full brightness (packedLight = 0xF000F0)
```

**Render Types:**
- Sphere/Talisman: Billboard quads (always face camera)
- Knife: Oriented quad (rotate to velocity direction)
- Laser: Custom vertex buffer (cylinder + hemisphere caps)

### Decision 3: Whitelist as Per-Instance Set

**Choice:** Each danmaku instance stores `Set<EntityType<?>> whitelist` passed at spawn time.

**Rationale:**
- Maximum flexibility: different enemies can have different friendly-fire rules
- Future-proof: allows per-danmaku whitelist without refactoring
- Performance: HashSet lookup is O(1), acceptable overhead in collision check

**Alternatives Considered:**
- **Global config whitelist:** All danmaku share one whitelist. Rejected because limits design space (can't have fairy danmaku ignore fairies but boss danmaku ignore bosses).
- **Per-shooter whitelist:** Shooter entity defines whitelist, passes to spawned danmaku. This is actually compatible with per-instance approach (shooter builds Set and passes it).

**Usage Pattern:**
```java
// Fairy spawning danmaku
Set<EntityType<?>> whitelist = Set.of(
    ModEntityTypes.FAIRY.get(),
    ModEntityTypes.BIG_FAIRY.get()
);
SphereDanmaku bullet = new SphereDanmaku(level, fairy, damage, color, whitelist);
```

### Decision 4: Dual-Side Deterministic Physics

**Choice:** Both client and server tick physics identically; sync only at spawn and on external velocity changes.

**Rationale:**
- Minimal bandwidth: spawn packet + initial params, no continuous position sync
- Deterministic: Java floating-point is consistent, same inputs → same outputs
- Smooth client rendering: no interpolation jitter from position corrections

**Spawn Packet Contents:**
- Position (Vec3)
- Velocity (Vec3)
- Color (int RGB) - **server-authoritative** for random colors
- Target UUID (for talisman) - nullable
- Laser params (delay, duration, maxLength, radius) - for laser only
- Whitelist (not synced - client doesn't need it for rendering, server-side only)
**Edge Cases:**
- **Client target lookup failure (talisman):** If client can't find target entity by UUID (entity not loaded/despawned), gracefully degrade to straight flight. Spec already covers this.
- **Floating-point drift:** After 1200 ticks (60s), client/server position may drift by ~0.1 blocks. Acceptable because danmaku despawns at 1200 ticks, and collision is server-authoritative.

**Alternatives Considered:**
- **Continuous position sync:** Send position updates every N ticks. Rejected due to bandwidth cost (100 bullets × position packet = significant overhead).
- **Periodic corrections:** Server sends position corrections every 20 ticks. Rejected because causes visible jitter/teleporting on client.

### Decision 5: Knife Piercing with Hit Tracking

**Choice:** KnifeDanmaku stores `Set<UUID> hitEntities` to track which entities have been damaged.

**Rationale:**
- Prevents duplicate damage: each entity damaged exactly once per knife
- Entity lifecycle-aware: UUID-based, survives entity references changing
- Small memory footprint: typical knife hits 0-3 entities before hitting block

**Implementation:**
```java
class KnifeDanmaku extends AbstractDanmakuProjectile {
    private final Set<UUID> hitEntities = new HashSet<>();
    
    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        if (!hitEntities.contains(hit.getUUID()) && !isWhitelisted(hit)) {
            hit.hurt(...);
            hitEntities.add(hit.getUUID());
        }
        // Do NOT discard() - keep flying
    }
}
```

**Alternatives Considered:**
- **WeakReference cache:** Use WeakReference<Entity> instead of UUID. Rejected because entity references can change on reload, and garbage collection behavior is unpredictable.
- **No tracking, rely on invulnerability ticks:** Rejected because Minecraft invulnerability is 20 ticks, but knife may hit same entity multiple times in <20 ticks if entity moves with knife.

### Decision 6: Talisman Homing with Vector Steering

**Choice:** Each tick, calculate angle between current velocity and target direction, clamp to max turn rate, apply rotation.

**Rationale:**
- Smooth curves: gradual turning produces natural-looking arcs
- Predictable: turn rate in degrees/second is intuitive for designers
- Dual-side compatible: same calculation on client/server with same target position

**Steering Algorithm:**
```java
void tickHoming() {
    if (target == null || !target.isAlive()) return;
    
    Vec3 currentDir = getDeltaMovement().normalize();
    Vec3 toTarget = target.position().subtract(position()).normalize();
    
    double angleDiff = Math.acos(currentDir.dot(toTarget)); // radians
    double maxTurnRad = Math.toRadians(sensitivity / 20.0); // sensitivity = deg/sec
    
    if (angleDiff <= maxTurnRad) {
        // Can reach target direction this tick
        setDeltaMovement(toTarget.scale(speed));
    } else {
        // Partial turn via slerp or cross product rotation
        Vec3 newDir = steerTowards(currentDir, toTarget, maxTurnRad);
        setDeltaMovement(newDir.scale(speed));
    }
}
```

**Alternatives Considered:**
- **Instant turning:** Directly point at target each tick. Rejected because produces zigzag paths, not smooth curves.
- **Acceleration-based:** Apply acceleration toward target. Rejected because harder to tune (acceleration + max speed + friction = many parameters), and doesn't match "degrees per second" spec.

### Decision 7: Laser State Machine with Raycast Damage

**Choice:** LaserDanmaku uses enum state (DELAY, ACTIVE, DONE) and performs AABB + line-segment distance raycast every 5 ticks during ACTIVE.

**Rationale:**
- Clear phases: delay for warning, active for damage, done for cleanup
- Efficient collision: AABB pre-filter + line-distance check is faster than testing every entity individually
- Configurable damage rate: 5-tick interval (4 Hz) balances damage consistency with performance

**Laser Raycast Implementation:**
```java
void tickActive() {
    if (tickCount % 5 != 0) return; // Damage every 5 ticks
    
    Vec3 start = position();
    Vec3 end = start.add(direction.scale(getActualLength())); // min(maxLength, distToBlock)
    
    AABB scanBox = new AABB(start, end).inflate(radius);
    List<Entity> candidates = level.getEntities(this, scanBox);
    
    for (Entity entity : candidates) {
        if (isWhitelisted(entity)) continue;
        double dist = distanceToLineSegment(entity.position(), start, end);
        if (dist <= radius) {
            entity.hurt(ModDamageTypes.danmaku(entity, getOwner()), damage);
        }
    }
}
```

**Alternatives Considered:**
- **Damage every tick:** Rejected because 20 Hz damage rate is overkill and impacts performance with multiple lasers.
- **Single damage on enter, burn status:** Rejected because spec explicitly states "Entity exits beam, stops receiving damage" (not a lingering effect).
- **Bounding box collision:** Use AABB for entire laser. Rejected because rectangular hitbox doesn't match cylindrical visual (unfair hits at corners).

### Decision 8: Refactor Existing DanmakuProjectile

**Choice:** Keep existing `DanmakuProjectile` entity type registered, but make it delegate to `SphereDanmaku` implementation.
**Rationale:**
- Backward compatibility: existing saves with in-flight DanmakuProjectile entities load correctly
- Migration path: old entity type redirects to new SphereDanmaku behavior
- Gradual transition: existing AI goals continue working, can be updated incrementally

**Implementation Options:**

**Option A: Inheritance Chain**
```java
class SphereDanmaku extends AbstractDanmakuProjectile { /* sphere logic */ }
class DanmakuProjectile extends SphereDanmaku { /* legacy compat */ }
```
Clean, but DanmakuProjectile is now a subclass (confusing naming).

**Option B: Delegation**
```java
class DanmakuProjectile extends ThrowableProjectile {
    // Delegate all methods to internal SphereDanmaku instance
}
```
Cleaner semantically, but adds delegation boilerplate.

**Option C: Replace DanmakuProjectile with AbstractDanmakuProjectile**
```java
class AbstractDanmakuProjectile extends ThrowableProjectile { /* base logic */ }
// DanmakuProjectile no longer exists, ModEntityTypes.DANMAKU points to SphereDanmaku
```
**Chosen: Option C.** Rename DanmakuProjectile → AbstractDanmakuProjectile, keep entity type ID `gensokyou:danmaku` but map to SphereDanmaku. Saves load correctly because entity ID is unchanged.

## Risks / Trade-offs

### Risk: Performance with 100+ Danmaku + Glow Layers
**Impact:** Glow doubles draw calls (2 quads per bullet instead of 1). At 100 bullets = 200 quads, may drop FPS on lower-end clients.

**Mitigation:**
- Profile with 100+ bullets before optimizing prematurely
- If needed, implement instanced rendering (batch same-type bullets into single draw call)
- Provide config option to disable glow (fallback to base rendering)

### Risk: Laser Raycast with Multiple Active Lasers
**Impact:** Each laser raycasts all entities in AABB every 5 ticks. With 10 active lasers and 50 entities nearby, that's 100 distance checks per tick (10 lasers × 50 entities ÷ 5 ticks).

**Mitigation:**
- Spatial partitioning: use level.getEntities() AABB pre-filter (already planned)
- Limit concurrent lasers per shooter (design constraint, not code limitation)
- Damage tick interval configurable if needed (currently hardcoded 5)

### Risk: Client-Side Target Lookup Failure (Talisman)
**Impact:** If client can't resolve target UUID (entity chunk not loaded), talisman flies straight instead of homing. Reduces gameplay accuracy.

**Mitigation:**
- Graceful degradation acceptable (spec already defines this behavior)
- If critical, add periodic target position sync packets (rejected for initial impl due to complexity)

### Risk: Floating-Point Drift Between Client/Server
**Impact:** After 60 seconds, client and server danmaku positions may diverge by ~0.1 blocks due to FP rounding differences.

**Mitigation:**
- Acceptable: danmaku lifetime is 60s max, drift is small
- Collision is server-authoritative, so false client-side visuals don't affect gameplay
- If becomes problem, add periodic position corrections (not in initial scope)

### Trade-off: No Shader Bloom (Initial Implementation)
**Choice:** Use layered semi-transparent glow instead of true shader bloom.

**Impact:** Glow effect is less realistic (no light bleed, no HDR) compared to Touhou originals.

**Justification:** Pragmatic choice to ship faster. Layered glow is standard in many bullet hell games and acceptable to players. Shader bloom can be added later if visual quality is insufficient.

### Trade-off: Whitelist Not Synced to Client
**Choice:** Whitelist is server-side only, not included in spawn packet.

**Impact:** Client cannot predict whether danmaku will hit certain entities (for particle effects, sound, etc.).

**Justification:** Client doesn't need whitelist for rendering. Collision/damage is server-authoritative. Reduces spawn packet size (whitelist could be 5-10 entity types = 20-40 bytes per bullet).

## Migration Plan

### Phase 1: Core Implementation (No Breaking Changes)
1. Create `AbstractDanmakuProjectile` base class (refactor existing `DanmakuProjectile`)
2. Implement four subclasses: `SphereDanmaku`, `KnifeDanmaku`, `TalismanDanmaku`, `LaserDanmaku`
3. Register new entity types in `ModEntityTypes`
4. Keep existing `DANMAKU` entity type, point to `SphereDanmaku`

### Phase 2: Rendering
1. Create base `AbstractDanmakuRenderer` with glow layer logic
2. Implement four renderers (sphere/talisman use billboard, knife uses oriented quad, laser uses cylinder)
3. Register renderers in `GensokyouClient`

### Phase 3: Integration
1. Update `FanDanmakuGoal`, `EightAngleDanmakuGoal` to use new `SphereDanmaku` constructor
2. Add new AI goals for knife/talisman/laser patterns (optional, can be done incrementally)
3. Test with existing fairy/boss entities

### Phase 4: Polish
1. Tune glow layer scale/alpha for visual appeal
2. Add config options for glow (enable/disable, scale factor)
3. Profile performance with 100+ bullets, optimize if needed

### Rollback Strategy
If critical issues found post-merge:
- Phase 1-2: Revert commits, no save compatibility issues (new entities never spawned in prod)
- Phase 3+: If bullets in saves, keep entity types registered but disable spawning (existing bullets despawn naturally within 60s)

### Testing Checklist
- [ ] Each danmaku type spawns and renders correctly
- [ ] Whitelist prevents friendly fire
- [ ] Knife pierces entities, stops on blocks
- [ ] Talisman homes toward target with correct turn rate
- [ ] Laser shows delay indicator, then damages in beam
- [ ] Glow layers render without z-fighting
- [ ] 100+ bullets on screen maintains 60 FPS (profile)
- [ ] Velocity mutation API works (setVelocity, setDirection)
- [ ] Colors sync correctly (random colors match on all clients)

## Open Questions

None currently. All technical decisions have been made with clear rationale. If implementation reveals edge cases, document them here and update design accordingly.

## Implementation Amendments（实现期修订）

以下决策在实现与评审后对上文有所修正或补充：

1. **灵符目标同步用 entity network id，非 UUID。** UUID 查询（`getEntity(UUID)`）仅服务端可用，客户端无法解析；network id 经 `level.getEntity(int)` 双端可用。附带影响：target 不写入 NBT（重载世界后目标丢失，弹幕 60 秒寿命内可接受）。灵敏度一并走 SynchedEntityData，客户端执行相同偏转计算。

2. **速度变更接口带同步。** 外部调用 `setVelocity()` 时置 `hurtMarked = true`，由原版机制下发运动包，客户端立即对齐——否则弹幕阵列编排会「先错后跳」。实体内部每 tick 的转向（灵符追踪）直接调 `setDeltaMovement`，不触发同步包。位置纠偏采用阈值策略（误差 > 1 格才硬纠正），双端一致模拟下的微小误差不予处理，换取完全平滑的轨迹。

3. **发光层采用加法混合（SRC_ALPHA, ONE）自定义 RenderType。** 原版实体渲染类型均为 alpha 混合，弹幕重叠时辉光相互覆盖而非叠加，达不到表现要求。自定义层只写颜色不写深度，深度测试保留（不透墙）。这是「方案 b → shader bloom」之间的中间档，先落地观察表现，不达标再上 shader bloom。

4. **激光判伤过滤 `canBeHitByProjectile()`。** 避免射线捞到掉落物/经验球等非目标实体造成边角 bug。命中判定用实体碰撞箱与射线求交（AABB.clip），比中心点距离判定对大体型实体更公平。

5. **旧 DanmakuProjectile 保留为存档兼容薄壳。** 继承 SphereDanmaku，保留实体 id 与 "Damage" NBT 键，旧存档在飞的弹幕无缝转入新行为。新代码禁用该类。

### 远期架构备忘：高密度弹幕的规模天花板

当前「每发弹幕一个实体」方案在约 200+ 并发时，服务端实体管理与客户端实体渲染开销都会显著上升（东方正作同屏 300~500 发是常态）。若将来需要高密度符卡，迁移方向是**混合架构**：

- 有判伤意义的弹 → 保持实体（现状）
- 纯视觉的密集弹 → 服务端只计算轨迹，用一个批量自定义 payload 周期下发全部弹位，客户端绘制且不判伤；判伤由服务端对图案做粗粒度体积检测

本备忘仅为远期设计记录，当前范围不实施。