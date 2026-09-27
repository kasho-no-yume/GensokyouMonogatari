# danmaku-sphere Specification

## Purpose
普通球型弹幕：随机/指定颜色、命中实体或方块消失、60 秒寿命、外发光。
## Requirements
### Requirement: Sphere danmaku spawning
The system SHALL allow spawning sphere-shaped danmaku projectiles with configurable size, damage, and color parameters.

#### Scenario: Spawn with random color
- **WHEN** sphere danmaku is spawned without specifying a color
- **THEN** server assigns a random color from the full RGB spectrum and synchronizes it to all clients

#### Scenario: Spawn with specified color
- **WHEN** sphere danmaku is spawned with a specific color (RGB integer)
- **THEN** danmaku uses the specified color on both server and client

#### Scenario: Spawn with size and damage
- **WHEN** sphere danmaku is spawned with size 0.5f and damage 8.0f
- **THEN** danmaku renders at 0.5 block diameter and deals 8.0 damage on hit

### Requirement: Sphere collision behavior
Sphere danmaku SHALL disappear when colliding with any damageable entity or block, unless the entity is in the whitelist.

#### Scenario: Hit non-whitelisted entity
- **WHEN** sphere danmaku collides with an entity not in its whitelist
- **THEN** danmaku deals damage to the entity and disappears immediately

#### Scenario: Hit whitelisted entity
- **WHEN** sphere danmaku collides with an entity in its whitelist (e.g., shooter or friendly type)
- **THEN** danmaku passes through without dealing damage or disappearing

#### Scenario: Hit shooter
- **WHEN** sphere danmaku collides with its owner entity
- **THEN** danmaku passes through without dealing damage or disappearing

#### Scenario: Hit block
- **WHEN** sphere danmaku collides with any solid block
- **THEN** danmaku disappears immediately without dealing damage

### Requirement: Sphere lifetime
Sphere danmaku SHALL automatically disappear after 60 seconds (1200 ticks) if it has not collided with anything.

#### Scenario: Lifetime expiration
- **WHEN** sphere danmaku has existed for 1200 ticks without collision
- **THEN** danmaku disappears automatically

### Requirement: Sphere visual glow effect
Sphere danmaku SHALL render as a three-layer soft-glow structure — white core, color-tinted gradient body, outer glow — using a desaturated high-resolution radial gradient texture, with all color provided by the entity color at render time. The gradient body layer (alpha blending) and the outer glow layer (additive blending) SHALL both write to the depth buffer, so every visible layer of the danmaku is occluded correctly relative to later-drawn translucent terrain (water) and clouds.

The three layers SHALL **NOT** be coplanar. Because they differ only by uniform scale about the same local origin, scaling alone leaves all of them on one screen-parallel plane; each layer SHALL therefore be offset along the billboard's local +Z (toward the camera, the same direction convention the su kima eyelid outline already uses) so that the body sits at 0, the glow at +0.004 blocks, and the core at +0.008 blocks.

Layers SHALL NOT depend on submission order to remain visible. Two layers that share one RenderType end up in the same buffer batch, and a depth-writing RenderType with `sortOnUpload` re-orders that batch's quads before drawing; therefore **at most one layer per batch may write depth**, and any layer that does write depth MUST be the farthest of the layers sharing its batch. For the sphere this means the gradient body and the outer glow keep writing depth (both are required to occlude later-drawn translucent terrain), while the innermost white core — which lies strictly inside both, being a 0.55x scale of the same texture inside a 1.35x scale — SHALL NOT write depth. Offsets SHALL be far below one screen pixel at typical viewing distance and MUST NOT read as a visible inter-layer displacement.

#### Scenario: Three-layer rendering
- **WHEN** sphere danmaku is rendered on client
- **THEN** system renders, from inner to outer: a white core layer (additive blending, ~0.5x scale, always white regardless of entity color, non-depth-writing), a gradient body layer (depth-writing alpha blending, entity color, radial gradient with smoothly fading edges), and an enlarged semi-transparent emissive glow layer at 1.35x scale (entity color, depth-writing additive blending); the three are separated along the billboard's local +Z by 0.004-block steps

#### Scenario: Color correctness for arbitrary colors
- **WHEN** sphere danmaku is spawned with any RGB color (e.g., blue 0x0000FF)
- **THEN** the rendered body and glow tint to that color without darkening; the core remains white

#### Scenario: Soft edge rendering
- **WHEN** sphere danmaku body is rendered
- **THEN** edge alpha fades smoothly to zero (no cutout hard edge)

#### Scenario: Occlusion by water and clouds
- **WHEN** a sphere danmaku is nearer to the camera than a translucent water surface or a cloud layer
- **THEN** neither the body nor the outer glow is covered by the later-drawn water/cloud (the nearer layer is depth-rejected there)

#### Scenario: No square depth hole
- **WHEN** a sphere danmaku writes depth over a translucent surface
- **THEN** only its visible circular silhouette occludes the surface; fully transparent texture corners (alpha < 0.1) are discarded and MUST NOT create a square hole

#### Scenario: Glow rendering
- **WHEN** sphere danmaku is rendered on client
- **THEN** system renders an enlarged semi-transparent emissive layer behind the body at 1.35x scale

#### Scenario: No depth-fighting stripes
- **WHEN** a sphere danmaku is observed from any distance and with the camera moving, over any background
- **THEN** no intermittent dark stripes, flicker, or partial-layer dropout appear anywhere on it, in particular not across the core and the bright center of the body; all three layers are simultaneously visible regardless of the order in which the renderer happens to submit them

### Requirement: Sphere velocity mutation
Sphere danmaku SHALL provide methods to dynamically change velocity and direction during flight.

#### Scenario: Change velocity vector
- **WHEN** code calls `setVelocity(Vec3)` on a sphere danmaku
- **THEN** danmaku immediately adopts the new velocity vector for subsequent ticks

#### Scenario: Change direction with speed
- **WHEN** code calls `setDirection(Vec3, double)` on a sphere danmaku
- **THEN** danmaku changes direction to the normalized vector and adjusts speed to the specified value

### Requirement: Sphere texture asset
Sphere danmaku SHALL use a desaturated (grayscale/white) high-resolution radial gradient texture whose colors are provided entirely by vertex tinting.

#### Scenario: Texture replacement
- **WHEN** the sphere danmaku texture asset is loaded
- **THEN** it is a 64x64 (or higher) desaturated radial gradient with alpha fading to zero at edges, containing no baked-in hue

### Requirement: Laser emitter magic circle
Laser danmaku SHALL render a pentagram magic circle texture at the beam emitter during the active phase, oriented perpendicular to the beam, expanding and collapsing with the beam thickness envelope, continuously rotating around the beam axis, tinted with the laser entity color using additive blending.

#### Scenario: Circle appears on firing
- **WHEN** laser transitions from delay phase to active phase
- **THEN** a pentagram magic circle renders at the beam emitter (beam start position), facing perpendicular to the beam direction

#### Scenario: Envelope-synchronized expansion and collapse
- **WHEN** the beam thickness envelope expands at fire onset or collapses at beam end
- **THEN** the magic circle scales in sync with the same envelope (full size during stable beam, zero at phase edges)

#### Scenario: Continuous rotation
- **WHEN** the laser remains in active phase
- **THEN** the magic circle rotates continuously around the beam axis at a constant rate

#### Scenario: Color tinting
- **WHEN** laser is spawned with any RGB color
- **THEN** the magic circle renders tinted with the laser entity color using additive blending at full brightness

#### Scenario: No circle during delay phase
- **WHEN** laser is in delay phase
- **THEN** only the semi-transparent red warning indicator line renders; no magic circle appears

### Requirement: Magic circle texture asset
The laser magic circle SHALL use a high-resolution, procedurally drawn (non-pixel-art) texture: white linework pentagram with an outer ring on a transparent background, desaturated so entity color provides all hue.

#### Scenario: Texture quality
- **WHEN** the magic circle texture asset is inspected
- **THEN** it is 256x256 (or higher) with smooth anti-aliased linework (outer ring, inscribed pentagon, pentagram), transparent background, and no baked-in hue

