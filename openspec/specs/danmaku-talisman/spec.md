# danmaku-talisman Specification

## Purpose
灵符型弹幕：可指定追踪目标，灵敏度（度/秒）限制偏转速率，默认红色，外发光。
## Requirements
### Requirement: Talisman danmaku spawning
The system SHALL allow spawning talisman-shaped danmaku projectiles with configurable color, damage, target entity, and turn sensitivity.

#### Scenario: Spawn with default red color
- **WHEN** talisman danmaku is spawned without specifying a color
- **THEN** danmaku uses red color (0xFF0000) by default

#### Scenario: Spawn with custom color
- **WHEN** talisman danmaku is spawned with color 0x00FF00 (green)
- **THEN** danmaku renders in green on both server and client

#### Scenario: Spawn without target
- **WHEN** talisman danmaku is spawned with null target entity
- **THEN** danmaku behaves like a normal sphere danmaku (flies straight)

#### Scenario: Spawn with target and sensitivity
- **WHEN** talisman danmaku is spawned with target entity and sensitivity 180.0 (degrees per second)
- **THEN** danmaku tracks the target with maximum turn rate of 9.0 degrees per tick (180/20)

### Requirement: Talisman homing behavior
Talisman danmaku SHALL steer toward its target entity every tick, respecting the configured turn sensitivity limit. It SHALL permanently stop steering when the angle between its current velocity direction and the direction from the talisman to the target exceeds a configured loss threshold (default 120 degrees, read from configuration); after losing the target it SHALL continue flying straight at its current speed and MUST NOT re-acquire the target.

#### Scenario: Steer toward target
- **WHEN** talisman danmaku has an alive target entity 10 blocks away
- **THEN** danmaku adjusts its velocity direction toward the target position, limited by turn sensitivity

#### Scenario: Turn rate limiting
- **WHEN** talisman danmaku with sensitivity 90.0 deg/sec needs to turn 10 degrees toward target
- **THEN** danmaku turns 4.5 degrees (90/20 per tick) and continues turning next tick

#### Scenario: Target dies or despawns
- **WHEN** talisman danmaku's target entity dies or is removed
- **THEN** danmaku continues flying in its last direction without further steering

#### Scenario: Target falls outside the loss angle
- **WHEN** the angle between the talisman's velocity direction and the direction from the talisman to its target exceeds the configured loss threshold (e.g., the talisman has overshot and the target is nearly behind it)
- **THEN** danmaku permanently stops steering and flies straight at its current speed for the rest of its lifetime

#### Scenario: Client cannot find target
- **WHEN** client receives talisman spawn packet but cannot find target entity by id
- **THEN** danmaku flies straight without homing behavior (graceful degradation)

### Requirement: Talisman collision behavior
Talisman danmaku SHALL disappear when colliding with any damageable entity or block, unless the entity is in the whitelist.

#### Scenario: Hit target entity
- **WHEN** talisman danmaku collides with its tracking target
- **THEN** danmaku deals damage to the target and disappears immediately

#### Scenario: Hit non-target entity
- **WHEN** talisman danmaku collides with an entity that is not its target (and not whitelisted)
- **THEN** danmaku deals damage and disappears immediately

#### Scenario: Respect whitelist
- **WHEN** talisman danmaku collides with whitelisted entity or owner
- **THEN** danmaku passes through without dealing damage or disappearing

#### Scenario: Hit block
- **WHEN** talisman danmaku collides with a solid block
- **THEN** danmaku disappears immediately

### Requirement: Talisman lifetime
Talisman danmaku SHALL automatically disappear after 60 seconds (1200 ticks) if it has not collided with anything.

#### Scenario: Lifetime expiration with active target
- **WHEN** talisman danmaku has been tracking a target for 1200 ticks without collision
- **THEN** danmaku disappears automatically

### Requirement: Talisman visual glow effect
Talisman danmaku SHALL render as a flat rectangular paper sheet lying HORIZONTAL (face parallel to the ground), rotated 90° about its face normal relative to a side-on layout: the long axis aligns with the flight axis (dart-like, ofuda head leading toward the target) and the short edges lie across it — one short edge facing the shooter, the opposite one toward the target — with an outer glow effect. It SHALL NOT billboard to face the camera, and SHALL NOT fly standing on its long edge.

#### Scenario: Flat rectangular shape
- **WHEN** talisman danmaku is rendered on client
- **THEN** the paper lies flat with its long axis along the flight direction, a short edge facing the shooter, tilting with the flight pitch but never rotating to face the camera

#### Scenario: Glow rendering
- **WHEN** talisman danmaku is rendered on client
- **THEN** system renders the base rectangle plus an enlarged semi-transparent emissive layer at 1.2x scale

### Requirement: Talisman velocity mutation
Talisman danmaku SHALL provide methods to change target entity and turn sensitivity dynamically during flight.

#### Scenario: Change target mid-flight
- **WHEN** code calls `setTarget(newEntity)` on a talisman danmaku
- **THEN** danmaku immediately begins tracking the new target entity

#### Scenario: Change sensitivity mid-flight
- **WHEN** code calls `setSensitivity(360.0)` on a talisman danmaku
- **THEN** danmaku immediately adopts the new turn rate of 18.0 degrees per tick

### Requirement: Talisman texture desaturated redesign
Talisman danmaku SHALL use a desaturated high-resolution paper talisman texture tinted by entity color at render time; geometry, orientation, and glow structure SHALL remain unchanged.

#### Scenario: Talisman color correctness
- **WHEN** talisman danmaku is spawned with any RGB color
- **THEN** the talisman renders correctly in that color without darkening (no baked-in dark gray base)

#### Scenario: Geometry unchanged
- **WHEN** talisman danmaku is rendered after the texture replacement
- **THEN** its flat paper geometry, flight-axis orientation, and outer glow layer are visually identical in structure to before

### Requirement: Talisman body and glow occlusion, full brightness
The talisman danmaku body SHALL write to the depth buffer while retaining smooth alpha-blended edges, and its outer glow layer SHALL likewise write to the depth buffer (additive blending), so neither the body nor the glow is covered by later-drawn translucent terrain (water) or clouds. The body SHALL render at full brightness (self-illuminated) regardless of the ambient light at its position. Fully transparent texture corners (alpha < 0.1) MUST be discarded so they do not create a square depth hole.

The body and its outer glow SHALL **NOT** be coplanar. The glow is a uniform 1.35x scale of the body about the same local origin, so without an offset both lie in the local y=0 paper plane and their per-vertex depths differ only by floating-point rounding; the glow SHALL therefore be offset along the paper's local +Y (its face normal) by 0.004 blocks, and the body SHALL be submitted first so the `LEQUAL` depth comparison admits both.

#### Scenario: Occlusion by water and clouds
- **WHEN** a talisman danmaku is nearer to the camera than a translucent water surface or a cloud layer
- **THEN** neither the body nor its outer glow is covered by the later-drawn water/cloud

#### Scenario: No square depth hole
- **WHEN** a talisman danmaku writes depth over a translucent surface
- **THEN** only its visible paper silhouette occludes the surface; transparent texture regions do not create a square hole

#### Scenario: Full brightness in darkness
- **WHEN** a talisman danmaku flies through a dark or occluded area
- **THEN** its body and glow render at full self-illumination, unaffected by ambient light

#### Scenario: No depth-fighting stripes
- **WHEN** a talisman danmaku is observed while flying, with the camera moving, over any background
- **THEN** no intermittent dark stripes, flicker, or partial-layer dropout appear on the paper or its halo, at any pitch angle

