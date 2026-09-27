## MODIFIED Requirements

### Requirement: Sphere visual glow effect
Sphere danmaku SHALL render as a three-layer soft-glow structure — white core, color-tinted gradient body, outer glow — using a desaturated high-resolution radial gradient texture, with all color provided by the entity color at render time. The gradient body layer (alpha blending) and the outer glow layer (additive blending) SHALL both write to the depth buffer, so every visible layer of the danmaku is occluded correctly relative to later-drawn translucent terrain (water) and clouds.

The three layers SHALL **NOT** be coplanar. Because they differ only by uniform scale about the same local origin, scaling alone leaves all of them on one screen-parallel plane; each layer SHALL therefore be offset along the billboard's local +Z (toward the camera, the same direction convention the su kima eyelid outline already uses) so that the body sits at 0, the glow at +0.004 blocks, and the core at +0.008 blocks.

Layers SHALL NOT depend on submission order to remain visible. Two layers that share one RenderType end up in the same buffer batch, and a depth-writing RenderType with `sortOnUpload` re-orders that batch's quads before drawing; therefore **at most one layer per batch may write depth**, and any layer that does write depth MUST be the farthest of the layers sharing its batch. For the sphere this means the gradient body and the outer glow keep writing depth (both are required to occlude later-drawn translucent terrain), while the innermost white core — which lies strictly inside both, being a 0.55x scale of the same texture inside a 1.35x scale — SHALL NOT write depth. Offsets SHALL be far below one screen pixel at typical viewing distance and MUST NOT read as a visible inter-layer displacement.

#### Scenario: Three-layer rendering
- **WHEN** sphere danmaku is rendered on client
- **THEN** system renders, from inner to outer: a white core layer (additive blending, ~0.5x scale, always white regardless of entity color, non-depth-writing), a gradient body layer (depth-writing alpha blending, entity color, radial gradient with smoothly fading edges), and an enlarged semi-transparent emissive glow layer at 1.35x scale (entity color, depth-writing additive blending); the three are separated along the billboard's local +Z by 0.004-block steps

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

#### Scenario: No depth-fighting stripes
- **WHEN** a sphere danmaku is observed from any distance and with the camera moving, over any background
- **THEN** no intermittent dark stripes, flicker, or partial-layer dropout appear anywhere on it, in particular not across the core and the bright center of the body; all three layers are simultaneously visible regardless of the order in which the renderer happens to submit them
