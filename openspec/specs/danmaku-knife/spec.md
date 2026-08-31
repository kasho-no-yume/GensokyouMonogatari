# danmaku-knife Specification

## Purpose
飞刀型弹幕：长端朝向速度方向、穿透实体且每目标单次判伤、碰方块才消失、无外发光。
## Requirements
### Requirement: Knife danmaku spawning
The system SHALL allow spawning knife-shaped danmaku projectiles that render as a kunai model (tapered diamond-section blade, guard and wrapped handle) aligned to their velocity direction, with the model geometry matching the texture atlas.

#### Scenario: Spawn knife with damage
- **WHEN** knife danmaku is spawned with damage 6.0f
- **THEN** danmaku is created as a kunai-model entity that deals 6.0 damage per hit

#### Scenario: Knife orientation
- **WHEN** knife danmaku velocity direction changes
- **THEN** knife's long axis immediately rotates to align with the new velocity vector

### Requirement: Knife piercing behavior
Knife danmaku SHALL pierce through all entities, damaging each entity exactly once; on hitting a block it SHALL stick in the wall like an arrow instead of vanishing (unless disabled by config).

#### Scenario: Pierce multiple entities
- **WHEN** knife danmaku travels through three entities in sequence
- **THEN** danmaku damages each entity once and continues flying without disappearing

#### Scenario: Avoid duplicate damage
- **WHEN** knife danmaku has already hit an entity and collides with it again
- **THEN** danmaku passes through without dealing damage again

#### Scenario: Respect whitelist during pierce
- **WHEN** knife danmaku pierces through its owner or whitelisted entity
- **THEN** danmaku passes through without dealing damage and continues flying

#### Scenario: Stick on block collision
- **WHEN** knife danmaku collides with a solid block
- **THEN** the knife freezes with its blade tip embedded in the block face, aligned to its incoming flight direction, and stops dealing damage

### Requirement: Knife wall-stuck duration
A stuck knife danmaku SHALL remain visible for a configurable duration (`knifeStickTicks`, default 100 ticks; 0 = legacy vanish) and then despawn; the stuck state and remaining ticks SHALL persist across save/reload.

#### Scenario: Despawn after duration
- **WHEN** a knife has been stuck for the configured tick count
- **THEN** it is removed from the world on both sides

#### Scenario: Disabled by config
- **WHEN** `knifeStickTicks` is set to 0
- **THEN** knives vanish immediately on block hit (legacy behavior)
