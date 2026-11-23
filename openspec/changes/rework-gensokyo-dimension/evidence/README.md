# Layout probe exports

The [ecology texture sheet](ecology-textures.png) shows the new block and item textures at 4× scale. In a new world with seed `987654321`, the dedicated-server ecology probe verified the shoreline, landmark pads, bamboo, existing underground ores, and 36 rooted Starcrown log columns across three forest sample chunks. Inspection of the generated chunk palettes found all eight new regional ores. The run log is `build/agent-logs/20261124-004532-runGensokyoWorldgenProbe.log`.

A fresh-world rerun with seed `987654321` after the Hakugyokurou root-placement update passed the eight landmark checks, both shoreline checks, the bamboo and ore sample, Chireiden debug teleport, twin-gate repair, and the layout export. The forest sample contained 34 rooted Starcrown log columns. The run log is `build/agent-logs/20261124-005150-runGensokyoWorldgenProbe.log`.

The shoreline revision passed the dedicated-server probe in a fresh world with seed `987654321`. The east Sanzu coast and east Misty Lake bank passed the source-water and dry-ground checks. The Moriya landmark had grass at Y=189. The bamboo landmark chunk contained 43 spirit iron ore blocks, 15 cinnabar ore blocks, and 151 bamboo columns at least 16 blocks high. A direct read of the first run's saved chunk confirmed a height range of 16–22 blocks. The same repeat run passed the eight landmark checks, Chireiden shaft and debug teleport checks, twin-gate repair check, and layout export. The run log is `build/agent-logs/20261124-001624-runGensokyoWorldgenProbe.log`.

The seeded terrain revision passed the dedicated-server landmark and ocean probes in both new worlds. Compare the biome exports for [424242](biomes-seeded-424242.png) and [987654321](biomes-seeded-987654321.png). The [relief preview](relief-987654321.png) samples the wired terrain density across the island before surface blocks and decorations.

The revised layout passed the dedicated-server probe in new worlds with seeds `424242` and `987654321`. Both runs checked the eight landmark biomes and dry pads, Misty Lake, the Sanzu River ocean, and the Chireiden shaft. The [revised biome export](biomes-irregular.png) shows the curved region boundaries and coastline.

These 256×256 PNGs document the initial layout, before the coastline and biome boundaries were revised. The height image samples actual `OCEAN_FLOOR` heights from generated chunks at the center of a 4×4 grid. The biome image was rasterized from the shared layout used by the biome source at the time.

| Seed | Biome layout | Sampled floor heights |
| --- | --- | --- |
| `424242` | [biomes-424242.png](biomes-424242.png) | [heights-424242.png](heights-424242.png) |
| `987654321` | [biomes-987654321.png](biomes-987654321.png) | [heights-987654321.png](heights-987654321.png) |

The offline layout test additionally checks that all eight regions are non-empty and 4-neighbor connected on a 32-block grid. Actual generated-block sampling checks the eight landmark biomes and dry pads, Misty Lake, the Sanzu River ocean, and the Chireiden shaft in both seeds.
