## MODIFIED Requirements

### Requirement: Talisman visual glow effect
Talisman danmaku SHALL render as a flat rectangular paper sheet lying HORIZONTAL (face parallel to the ground), rotated 90° about its face normal relative to a side-on layout: the long axis aligns with the flight axis (dart-like, ofuda head leading toward the target) and the short edges lie across it — one short edge facing the shooter, the opposite one toward the target — with an outer glow effect. It SHALL NOT billboard to face the camera, and SHALL NOT fly standing on its long edge.

#### Scenario: Flat rectangular shape
- **WHEN** talisman danmaku is rendered on client
- **THEN** the paper lies flat with its long axis along the flight direction, a short edge facing the shooter, tilting with the flight pitch but never rotating to face the camera

#### Scenario: Glow rendering
- **WHEN** talisman danmaku is rendered on client
- **THEN** system renders the base rectangle plus an enlarged semi-transparent emissive layer at 1.2x scale
