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
球状弹幕 SHALL 渲染一个外发光层，其放大倍率与透明度 SHALL 由视觉档案决定，MUST NOT 为编译期常量。球状弹幕 SHALL 额外渲染一个核心亮核层，其缩放与透明度同样由视觉档案决定。核心层 MUST 使用**不写深度**的渲染类型：它与外发光层分属不同渲染类型，绘制时立即结算前一批，从而避开「写深度的加法混合」类型在批内按质心距离重排所导致的整片拒写。

#### Scenario: 发光与核心参数来自档案
- **WHEN** 读取任一球弹的发光放大倍率与核心缩放
- **THEN** 二者取自其视觉档案而非渲染器常量

#### Scenario: 核心层不被批内重排拒写
- **WHEN** 多枚球弹在同屏重叠
- **THEN** 每枚的核心亮核均完整可见，MUST NOT 出现因批内重排而丢失的亮核

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

