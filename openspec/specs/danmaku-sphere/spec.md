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
Sphere danmaku SHALL render with an outer glow effect using a semi-transparent enlarged duplicate layer.

#### Scenario: Glow rendering
- **WHEN** sphere danmaku is rendered on client
- **THEN** system renders the base sphere plus an enlarged semi-transparent emissive layer behind it at 1.2x scale

### Requirement: Sphere velocity mutation
Sphere danmaku SHALL provide methods to dynamically change velocity and direction during flight.

#### Scenario: Change velocity vector
- **WHEN** code calls `setVelocity(Vec3)` on a sphere danmaku
- **THEN** danmaku immediately adopts the new velocity vector for subsequent ticks

#### Scenario: Change direction with speed
- **WHEN** code calls `setDirection(Vec3, double)` on a sphere danmaku
- **THEN** danmaku changes direction to the normalized vector and adjusts speed to the specified value
