## MODIFIED Requirements

### Requirement: Sphere visual glow effect
Sphere danmaku SHALL render as a three-layer soft-glow structure — white core, color-tinted gradient body, outer glow — using a desaturated high-resolution radial gradient texture, with all color provided by the entity color at render time.

#### Scenario: Three-layer rendering
- **WHEN** sphere danmaku is rendered on client
- **THEN** system renders, from inner to outer: a white core layer (additive blending, ~0.5x scale, always white regardless of entity color), a gradient body layer (translucent alpha blending, entity color, radial gradient with smoothly fading edges), and an enlarged semi-transparent emissive glow layer at 1.35x scale (entity color)

#### Scenario: Color correctness for arbitrary colors
- **WHEN** sphere danmaku is spawned with any RGB color (e.g., blue 0x0000FF)
- **THEN** the rendered body and glow tint to that color without darkening; the core remains white

#### Scenario: Soft edge rendering
- **WHEN** sphere danmaku body is rendered
- **THEN** edge alpha fades smoothly to zero (no cutout hard edge)

#### Scenario: Glow rendering
- **WHEN** sphere danmaku is rendered on client
- **THEN** system renders an enlarged semi-transparent emissive layer behind the body at 1.35x scale

## ADDED Requirements

### Requirement: Sphere texture asset
Sphere danmaku SHALL use a desaturated (grayscale/white) high-resolution radial gradient texture whose colors are provided entirely by vertex tinting.

#### Scenario: Texture replacement
- **WHEN** the sphere danmaku texture asset is loaded
- **THEN** it is a 64x64 (or higher) desaturated radial gradient with alpha fading to zero at edges, containing no baked-in hue

### Requirement: Laser emitter magic circle
Laser danmaku SHALL render a pentagram magic circle texture at the beam emitter during the active phase, oriented perpendicular to the beam, expanding and collapsing with the beam thickness envelope, continuously rotating around the beam axis, tinted with the laser entity color using additive blending.

#### Scenario: Circle appears on firing
- **WHEN** laser transitions from delay phase to active phase
- **THEN** a pentagram magic circle renders at the beam emitter (beam start position), facing perpendicular to the beam direction

#### Scenario: Envelope-synchronized expansion and collapse
- **WHEN** the beam thickness envelope expands at fire onset or collapses at beam end
- **THEN** the magic circle scales in sync with the same envelope (full size during stable beam, zero at phase edges)

#### Scenario: Continuous rotation
- **WHEN** the laser remains in active phase
- **THEN** the magic circle rotates continuously around the beam axis at a constant rate

#### Scenario: Color tinting
- **WHEN** laser is spawned with any RGB color
- **THEN** the magic circle renders tinted with the laser entity color using additive blending at full brightness

#### Scenario: No circle during delay phase
- **WHEN** laser is in delay phase
- **THEN** only the semi-transparent red warning indicator line renders; no magic circle appears

### Requirement: Magic circle texture asset
The laser magic circle SHALL use a high-resolution, procedurally drawn (non-pixel-art) texture: white linework pentagram with an outer ring on a transparent background, desaturated so entity color provides all hue.

#### Scenario: Texture quality
- **WHEN** the magic circle texture asset is inspected
- **THEN** it is 256x256 (or higher) with smooth anti-aliased linework (outer ring, inscribed pentagon, pentagram), transparent background, and no baked-in hue
