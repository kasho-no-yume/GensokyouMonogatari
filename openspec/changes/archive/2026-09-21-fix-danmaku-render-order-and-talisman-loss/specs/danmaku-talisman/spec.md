## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Talisman body and glow occlusion, full brightness
The talisman danmaku body SHALL write to the depth buffer while retaining smooth alpha-blended edges, and its outer glow layer SHALL likewise write to the depth buffer (additive blending), so neither the body nor the glow is covered by later-drawn translucent terrain (water) or clouds. The body SHALL render at full brightness (self-illuminated) regardless of the ambient light at its position. Fully transparent texture corners (alpha < 0.1) MUST be discarded so they do not create a square depth hole.

#### Scenario: Occlusion by water and clouds
- **WHEN** a talisman danmaku is nearer to the camera than a translucent water surface or a cloud layer
- **THEN** neither the body nor its outer glow is covered by the later-drawn water/cloud

#### Scenario: No square depth hole
- **WHEN** a talisman danmaku writes depth over a translucent surface
- **THEN** only its visible paper silhouette occludes the surface; transparent texture regions do not create a square hole

#### Scenario: Full brightness in darkness
- **WHEN** a talisman danmaku flies through a dark or occluded area
- **THEN** its body renders at full brightness, not dimmed by ambient light
