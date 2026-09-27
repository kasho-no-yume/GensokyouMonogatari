## Why

The `kanayamahiko_circle` structure already exists and is toggleable, but it has no registered behavior or smelting implementation, so it currently provides no way to process furnace inputs. The Gensokyo ore and rough-ore items also have no ritual-owned refining path, while their intended furnace exclusivity is not represented by an executable behavior.

This change turns the existing forge structure into a persistent, multi-pedestal furnace that consumes spirit power, preserves inputs during processing, and drops completed results beside the core. It also makes the existing Gensokyo material chain executable through the forge without adding vanilla furnace recipes.

## What Changes

- Register and implement the `kanayamahiko_circle` ritual behavior as a toggleable, continuously operating furnace.
- Resolve runtime recipes from `minecraft:smelting`, `minecraft:blasting`, and `minecraft:smoking`; do not use campfire cooking or third-party custom recipe types.
- Give each primary pedestal input an independent job with independent time, progress, and spirit-power consumption.
- Add forge-owned special rules for both block raw ores and their `rough_*` item counterparts:
  - cinnabar or rough cinnabar plus one spirit charcoal;
  - spirit iron ore or rough spirit iron ore plus one spirit charcoal;
  - star silver ore or rough star silver ore plus two spirit charcoal.
- Keep all inputs on their pedestals while a job runs; consume the primary input and bound charcoal only when the job completes.
- Cancel the affected job on the next server tick when any locked input is removed or replaced; do not refund spirit power already spent.
- Double the result only when the primary input is a block-form ore recognized by the Forge/NeoForge ore tags; rough item inputs and non-ore block items do not receive the multiplier.
- Add configurable per-level duration, consumption, cache capacity, and routed input rate, with the existing 0-2 level structure as the current exposed range.
- Show active jobs, progress, remaining time, and waiting/shortage states in the existing ritual information-line system.
- Preserve the current forge name and the current three-level pattern; do not add wood-to-spirit-charcoal production in this change.
- Do not register any vanilla furnace recipe for Gensokyo materials.

## Capabilities

### New Capabilities

- `kanayamahiko-smelting`: Multi-pedestal furnace behavior, runtime recipe resolution, special Gensokyo ore rules, input locking, independent progress, spirit-power scaling, result multiplication, persistence, and GUI status reporting.

### Modified Capabilities

- `gensokyo-materials`: Replace the previously documented `spirit_charcoal ×1 : raw ore ×4` ratio with the forge ratios: cinnabar and spirit iron use 1:1, star silver uses 1:2; both block raw ores and matching rough items are accepted, while only block-form ore inputs receive the result multiplier.

## Impact

- **Java behavior and ritual framework:** Add the Kanayamahiko behavior, per-core job/session persistence, recipe resolution, scheduler, output handling, GUI information lines, registration, capacity dispatch, and configurable in-rate declaration.
- **Data:** Add or update Forge-compatible ore tags for supported block ore items, add forge-specific smelting rules, update language keys, and add ritual guide/debug metadata. No ritual pattern geometry change is required.
- **Recipe compatibility:** Runtime `RecipeManager` lookups will see datapack and mod recipes using the three selected vanilla recipe types; custom recipe types remain out of scope.
- **Existing material behavior:** No new vanilla cooking recipes are added, and existing ritual recipes and resource production remain unchanged.
- **Validation:** Add static and integration coverage for recipe selection, support reservation, input removal, result multiplication, level scaling, power pause/resume, persistence, and GUI state reporting.
