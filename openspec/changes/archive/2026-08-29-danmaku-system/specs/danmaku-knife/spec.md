## ADDED Requirements

### Requirement: Knife danmaku spawning
The system SHALL allow spawning knife-shaped danmaku projectiles that render as elongated boxes aligned to their velocity direction.

#### Scenario: Spawn knife with damage
- **WHEN** knife danmaku is spawned with damage 6.0f
- **THEN** danmaku is created as an elongated box entity that deals 6.0 damage per hit

#### Scenario: Knife orientation
- **WHEN** knife danmaku velocity direction changes
- **THEN** knife's long axis immediately rotates to align with the new velocity vector

### Requirement: Knife piercing behavior
Knife danmaku SHALL pierce through all entities, damaging each entity exactly once, and only disappear when hitting a block.

#### Scenario: Pierce multiple entities
- **WHEN** knife danmaku travels through three entities in sequence
- **THEN** danmaku damages each entity once and continues flying without disappearing

#### Scenario: Avoid duplicate damage
- **WHEN** knife danmaku has already hit an entity and collides with it again
- **THEN** danmaku passes through without dealing damage again

#### Scenario: Respect whitelist during pierce
- **WHEN** knife danmaku pierces through its owner or whitelisted entity
- **THEN** danmaku passes through without dealing damage and continues flying

#### Scenario: Stop on block collision
- **WHEN** knife danmaku collides with a solid block
- **THEN** danmaku disappears immediately

### Requirement: Knife lifetime
Knife danmaku SHALL automatically disappear after 60 seconds (1200 ticks) if it has not collided with a block.

#### Scenario: Lifetime expiration
- **WHEN** knife danmaku has existed for 1200 ticks without hitting a block
- **THEN** danmaku disappears automatically

### Requirement: Knife visual appearance
Knife danmaku SHALL render as an elongated rectangular box without any glow effect.

#### Scenario: No glow rendering
- **WHEN** knife danmaku is rendered on client
- **THEN** system renders only the base knife shape with no additional glow layers

#### Scenario: Box dimensions
- **WHEN** knife danmaku is rendered
- **THEN** knife appears as a box approximately 1.5 blocks long and 0.2 blocks wide/tall

### Requirement: Knife velocity mutation
Knife danmaku SHALL provide methods to dynamically change velocity and direction during flight, with orientation automatically updating.

#### Scenario: Change velocity updates orientation
- **WHEN** code calls `setVelocity(Vec3)` on a knife danmaku
- **THEN** knife immediately adopts the new velocity and rotates its long axis to match the new direction
