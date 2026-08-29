## ADDED Requirements

### Requirement: Laser danmaku spawning
The system SHALL allow spawning laser-shaped danmaku with configurable color, damage, maximum length, radius, delay time, and duration.

#### Scenario: Spawn with default red color
- **WHEN** laser danmaku is spawned without specifying a color
- **THEN** laser uses red color (0xFF0000) by default

#### Scenario: Spawn with custom color
- **WHEN** laser danmaku is spawned with color 0x0000FF (blue)
- **THEN** laser renders in blue on both server and client

#### Scenario: Spawn with all parameters
- **WHEN** laser danmaku is spawned with maxLength=20, radius=0.3, delayTime=1.0s, duration=3.0s, damage=2.0
- **THEN** laser is created with specified dimensions and timing parameters

### Requirement: Laser delay phase
Laser danmaku SHALL display a semi-transparent red warning indicator during the delay phase before becoming active.

#### Scenario: Display warning indicator
- **WHEN** laser danmaku is in delay phase (tick < delayTicks)
- **THEN** client renders a semi-transparent red line from spawn position along fire direction for maxLength distance

#### Scenario: No damage during delay
- **WHEN** laser danmaku is in delay phase and entities are in the indicator line
- **THEN** laser deals no damage to any entities

#### Scenario: Transition to active phase
- **WHEN** laser danmaku delay timer reaches delayTicks
- **THEN** laser transitions to active phase and begins dealing damage

### Requirement: Laser active phase
Laser danmaku SHALL deal damage to all entities intersecting the beam every 5 ticks during the active phase.

#### Scenario: Damage entities in beam
- **WHEN** laser is in active phase and three entities are within the beam cylinder
- **THEN** laser damages all three entities every 5 ticks

#### Scenario: Entity enters beam mid-duration
- **WHEN** entity moves into active laser beam between damage ticks
- **THEN** entity receives damage on the next damage tick (multiple of 5 ticks)

#### Scenario: Entity exits beam
- **WHEN** entity moves out of the laser beam cylinder
- **THEN** entity immediately stops receiving damage (not a lingering burn effect)

#### Scenario: Respect whitelist during active phase
- **WHEN** laser beam overlaps with owner or whitelisted entities
- **THEN** laser does not damage those entities at any point

#### Scenario: Active phase expiration
- **WHEN** laser has been active for duration ticks
- **THEN** laser disappears immediately

### Requirement: Laser geometry
Laser danmaku SHALL render as a cylinder with hemispherical end caps, extending from spawn position along fire direction.

#### Scenario: Cylinder shape
- **WHEN** laser is in active phase
- **THEN** client renders a cylinder of specified radius extending along the fire direction

#### Scenario: Hemispherical caps
- **WHEN** laser cylinder is rendered
- **THEN** client renders hemispherical caps at both ends of the cylinder for smooth appearance

#### Scenario: Ray length calculation
- **WHEN** laser would extend beyond maxLength or hit a block
- **THEN** laser renders only up to the shorter of (maxLength, distance to first block)

### Requirement: Laser stationary behavior
Laser danmaku SHALL remain at its spawn position and direction throughout its lifetime.

#### Scenario: Fixed position
- **WHEN** laser danmaku is spawned at position (10, 64, 20) with direction (1, 0, 0)
- **THEN** laser remains at that position and direction through both delay and active phases

#### Scenario: No velocity mutation
- **WHEN** code attempts to call velocity mutation methods on laser danmaku
- **THEN** methods have no effect (laser position and direction are immutable after spawn)

### Requirement: Laser visual glow effect
Laser danmaku SHALL render with an outer glow effect using a semi-transparent enlarged duplicate layer.

#### Scenario: Glow rendering during active phase
- **WHEN** laser is in active phase
- **THEN** client renders base cylinder plus enlarged semi-transparent emissive layer at 1.2x radius

#### Scenario: No glow during delay phase
- **WHEN** laser is in delay phase
- **THEN** client renders only the semi-transparent red indicator line without glow

### Requirement: Laser lifetime
Laser danmaku SHALL exist for exactly (delayTime + duration) seconds, then disappear automatically.

#### Scenario: Total lifetime calculation
- **WHEN** laser is spawned with delayTime=2.0s and duration=5.0s
- **THEN** laser disappears automatically after 7.0 seconds (140 ticks)

#### Scenario: No early despawn
- **WHEN** laser is active and dealing damage
- **THEN** laser continues until duration expires regardless of how many entities it hits
