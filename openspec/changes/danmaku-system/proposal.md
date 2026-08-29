## Why

The current danmaku system only supports basic sphere projectiles. To create authentic Touhou-style bullet hell patterns, we need a comprehensive system with four distinct danmaku types (sphere, knife, talisman, laser), each with unique collision behaviors, visual effects, and movement capabilities. This enables complex, visually stunning attack patterns that are the hallmark of bullet hell gameplay.

## What Changes

- Replace the existing `DanmakuProjectile` with an abstract base class and four specialized danmaku types
- Add outer glow rendering effects for sphere, talisman, and laser danmaku (excluding knife)
- Implement whitelist-based collision filtering to prevent friendly fire between specific entity types
- Add velocity/direction mutation API to enable dynamic pattern formations (curved trajectories, spirals, etc.)
- Implement piercing mechanics for knife danmaku (hits all entities in path, damages each once)
- Add homing behavior for talisman danmaku with configurable turn sensitivity
- Implement two-phase laser system (delay warning indicator → active damage beam)
- Add server-side color synchronization for random and custom colors
- Extend lifetime to 60 seconds (1200 ticks) for all danmaku types

## Capabilities

### New Capabilities
- `danmaku-sphere`: Sphere-shaped danmaku with random or specified colors, explodes on entity/block collision
- `danmaku-knife`: Elongated box projectile that pierces entities, only stops on block collision, no glow effect
- `danmaku-talisman`: Flat rectangular homing danmaku with configurable target tracking and turn sensitivity
- `danmaku-laser`: Cylindrical beam with delay phase (warning indicator) and active damage phase, hits all entities in beam continuously

### Modified Capabilities
<!-- No existing capabilities being modified - this is a replacement/refactor of existing danmaku -->

## Impact

**Modified Files:**
- `DanmakuProjectile.java` - Refactored into `AbstractDanmakuProjectile` base class
- `ModEntityTypes.java` - Register four new entity types
- `BillboardRenderer.java` - May need extension for glow layers and shape variations
- `GensokyouTextures.java` - Add texture references for new danmaku types
- `GensokyouClient.java` - Register renderers for all four danmaku types
- AI Goal classes (`FanDanmakuGoal`, `EightAngleDanmakuGoal`) - Update to spawn new danmaku types

**New Files:**
- `AbstractDanmakuProjectile.java` - Base class with common lifetime, whitelist, velocity mutation
- `SphereDanmaku.java`, `KnifeDanmaku.java`, `TalismanDanmaku.java`, `LaserDanmaku.java` - Entity implementations
- `SphereDanmakuRenderer.java`, `KnifeDanmakuRenderer.java`, `TalismanDanmakuRenderer.java`, `LaserDanmakuRenderer.java` - Client renderers
- `GlowLayerRenderer.java` or similar - Shared glow effect rendering utility

**Performance Considerations:**
- Glow effects double draw calls per bullet (except knife) - may need instanced rendering for 100+ bullets
- Laser raycast checks all entities in beam volume every tick during active phase
- Homing calculations run every tick for talisman danmaku with active targets

**Backward Compatibility:**
- Existing danmaku spawning code needs migration to new sphere type
- Existing `DanmakuProjectile` entity type should remain for save compatibility, redirect to sphere implementation
