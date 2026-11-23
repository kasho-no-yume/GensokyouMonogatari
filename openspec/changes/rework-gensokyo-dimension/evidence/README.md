# Layout probe exports

The revised layout passed the dedicated-server probe in new worlds with seeds `424242` and `987654321`. Both runs checked the eight landmark biomes and dry pads, Misty Lake, the Sanzu River ocean, and the Chireiden shaft. The [revised biome export](biomes-irregular.png) shows the curved region boundaries and coastline.

These 256×256 PNGs document the initial layout, before the coastline and biome boundaries were revised. The height image samples actual `OCEAN_FLOOR` heights from generated chunks at the center of a 4×4 grid. The biome image was rasterized from the shared layout used by the biome source at the time.

| Seed | Biome layout | Sampled floor heights |
| --- | --- | --- |
| `424242` | [biomes-424242.png](biomes-424242.png) | [heights-424242.png](heights-424242.png) |
| `987654321` | [biomes-987654321.png](biomes-987654321.png) | [heights-987654321.png](heights-987654321.png) |

The offline layout test additionally checks that all eight regions are non-empty and 4-neighbor connected on a 32-block grid. Actual generated-block sampling checks the eight landmark biomes and dry pads, Misty Lake, the Sanzu River ocean, and the Chireiden shaft in both seeds.
