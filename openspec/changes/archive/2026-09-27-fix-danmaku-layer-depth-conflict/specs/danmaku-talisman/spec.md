## MODIFIED Requirements

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
