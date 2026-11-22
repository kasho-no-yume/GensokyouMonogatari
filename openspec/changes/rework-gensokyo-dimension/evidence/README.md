# Layout probe exports

These 256×256 PNGs were produced by the opt-in dedicated-server worldgen probe. The height image samples actual `OCEAN_FLOOR` heights from generated chunks at the center of a 4×4 grid; the biome image is rasterized from the same shared fixed layout used by the biome source. Colors are diagnostic, not final art.

| Seed | Biome layout | Sampled floor heights |
| --- | --- | --- |
| `424242` | [biomes-424242.png](biomes-424242.png) | [heights-424242.png](heights-424242.png) |
| `987654321` | [biomes-987654321.png](biomes-987654321.png) | [heights-987654321.png](heights-987654321.png) |

The offline layout test additionally checks that all eight regions are non-empty and 4-neighbor connected on a 32-block grid. Actual generated-block sampling checks the eight landmark biomes and dry pads, Misty Lake, the Sanzu River ocean, and the Chireiden shaft in both seeds.
