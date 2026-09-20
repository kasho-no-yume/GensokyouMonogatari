## ADDED Requirements

### Requirement: Talisman texture desaturated redesign
Talisman danmaku SHALL use a desaturated high-resolution paper talisman texture tinted by entity color at render time; geometry, orientation, and glow structure SHALL remain unchanged.

#### Scenario: Talisman color correctness
- **WHEN** talisman danmaku is spawned with any RGB color
- **THEN** the talisman renders correctly in that color without darkening (no baked-in dark gray base)

#### Scenario: Geometry unchanged
- **WHEN** talisman danmaku is rendered after the texture replacement
- **THEN** its flat paper geometry, flight-axis orientation, and outer glow layer are visually identical in structure to before
