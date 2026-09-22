## MODIFIED Requirements

### Requirement: Sphere visual glow effect
Sphere danmaku SHALL render as a three-layer soft-glow structure — white core, color-tinted gradient body, outer glow — using a desaturated high-resolution radial gradient texture, with all color provided by the entity color at render time. The gradient body layer (alpha blending) and the outer glow layer (additive blending) SHALL both write to the depth buffer, so every visible layer of the danmaku is occluded correctly relative to later-drawn translucent terrain (water) and clouds.

#### Scenario: Three-layer rendering
- **WHEN** sphere danmaku is rendered on client
- **THEN** system renders, from inner to outer: a white core layer (additive blending, ~0.5x scale, always white regardless of entity color), a gradient body layer (depth-writing alpha blending, entity color, radial gradient with smoothly fading edges), and an enlarged semi-transparent emissive glow layer at 1.35x scale (entity color, depth-writing additive blending)

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
