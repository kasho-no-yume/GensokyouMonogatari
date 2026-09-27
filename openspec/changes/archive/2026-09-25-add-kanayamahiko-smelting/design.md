## Context

`kanayamahiko_circle` already has a 0-2 level pattern, `toggleable: true`, and 8/16/32 cumulative pedestal positions. It is not registered in `RitualBehaviors`, has no forge recipe data, and currently has no behavior-specific persistence or power capacity branch. The generic ritual recipe matcher aggregates all pedestal items into one whole-structure match, so it cannot represent one independently progressing job per primary input.

The existing ritual framework already provides the core block entity, toggle lifecycle, `InfoLine` GUI channel, unified output helper, configurable spirit endpoint rates, and per-level capacity dispatch. The new behavior must fit those contracts rather than introduce a second GUI or output system.

The forge has two recipe sources with different semantics:

```text
ordinary input ──> runtime minecraft:smelting / blasting / smoking recipe
Gensokyo input  ──> forge-owned charcoal-bound rule
```

The forge must lock the selected rule and its physical inputs for the lifetime of a job, while still allowing the runtime recipe manager to change for future jobs.

## Goals / Non-Goals

**Goals:**

- Turn the existing forge into a toggleable, multi-pedestal furnace.
- Support ordinary runtime recipes from furnace, blast furnace, and smoker recipe types.
- Keep each primary input's time, power budget, result, and lifecycle independent.
- Support Gensokyo block ores and matching rough items with the agreed charcoal ratios.
- Double only block-form ore results identified by Forge/NeoForge ore tags.
- Pause rather than cancel when spirit power is temporarily insufficient.
- Persist valid job state across world reloads and preserve physical pedestal identity across pattern expansion.
- Reuse the existing ritual GUI, output, configuration, and spirit-routing infrastructure.

**Non-Goals:**

- No wood-to-spirit-charcoal production recipe.
- No `minecraft:campfire_cooking` or third-party custom recipe-type adapter.
- No new vanilla cooking recipe for any Gensokyo material.
- No change to the ritual pattern geometry, name, or current 0-2 level range.
- No vanilla furnace experience reward; this change defines output, duration, power, and input semantics only.
- No general-purpose rewrite of `RitualRecipeMatcher` or a new GUI screen.

## Decisions

### D1. Separate the ordinary recipe resolver from forge-owned special rules

The behavior will use the server `RecipeManager` for ordinary inputs and query `RecipeType.SMELTING`, `RecipeType.BLASTING`, and `RecipeType.SMOKING`. It will not merge the existing `ritual_recipes` catalog into the runtime lookup: that catalog is designed for whole-structure activation/passive matches and has no independent job or support-binding semantics.

The supported type priority will be `SMELTING > BLASTING > SMOKING`. This makes duplicate vanilla transformations deterministic while still allowing the ritual to replace all three furnace categories. Recipe lookup will occur when a primary input is first discovered; the selected recipe ID, result stack, and source type will then be locked into the job.

A small dedicated forge-rule data file will describe only the multi-input Gensokyo rules. Its fields will include the pattern ID, accepted primary item IDs, auxiliary item ID, auxiliary count, result item, and base result count. The current rules are six item variants representing three transformations. A dedicated loader avoids encoding gameplay ratios in Java and avoids pretending that a multi-input forge job is an ordinary `RitualRecipe`.

Alternative considered: putting the special rules in `ritual_recipes/kanayamahiko_circle.json`. Rejected because the existing matcher selects one aggregate recipe and would couple all pedestals into one execution.

### D2. Model a job around one primary pedestal

Each eligible primary item creates one job. A special job owns the primary position and a reserved set of auxiliary positions; the auxiliary items never become jobs themselves.

```text
WAITING_FOR_SUPPORT
        │ resources reserved
        ▼
RUNNING ── power shortage ──> PAUSED
  │                              │
  │                              └── cache restored ──> RUNNING
  │
  ├── input changed/removed ──> CANCELLED
  └── duration reached ───────> COMPLETE
```

The job snapshot will include the primary position, expected primary stack identity, locked recipe identity and result, support positions and expected stacks, elapsed progress, locked ritual level, and a monotonic first-come sequence number. A job is advanced only after all locked stacks still match.

A completed job performs validation, exact input consumption, and result emission in that order. Cancellation performs no consumption and no result emission. Power already spent is not refunded.

The default forge startup accepts an empty or partially supplied structure. It remains enabled and idle until valid jobs appear. The stop control pauses advancement and power consumption; it does not erase valid job state.

Alternative considered: use the existing aggregate `RitualRecipeMatcher`. Rejected because it cannot represent four simultaneous iron jobs plus four charcoal supports without coupling them into one recipe.

### D3. Use deterministic first-come allocation

A primary's first detection assigns its queue sequence. At each scheduling pass, waiting jobs are considered in sequence order. A special job binds all required auxiliary stacks atomically; it never reserves a partial charcoal set. Bound auxiliary positions are excluded from later allocations until the owning job completes or cancels.

Power is allocated in the same deterministic order. A job that cannot pay its current tick cost pauses without spending that unavailable amount. Any remaining cache may be considered by later jobs, but no job may consume a budget needed to preserve an earlier job's already-reserved state. The implementation should use fixed-point carry for configurable fractional rates so a paused or resumed job does not lose sub-tick power.

A cancelled job releases its auxiliary bindings immediately, allowing the next waiting job to claim still-present charcoal.

### D4. Persist state in the core, keyed by absolute positions

The core block entity will own a versioned Kanayamahiko session. The session will be serialized in the core's save/load path and migrated by version. The authoritative job key is the absolute `BlockPos` of the primary pedestal, with the sequence number used only for ordering. Sorted pedestal indices are not stable across pattern expansion and will not be used as identity.

The session will preserve valid jobs when the same pattern changes level. Existing jobs retain their locked level, duration, and rate; newly created jobs use the current level. A structure loss clears the session. A recipe reload that removes a locked recipe or changes its result cancels that job. A world reload restores the session and revalidates all physical stacks before resuming.

Because a core is the owner of the ritual, dismantling the core discards its session while the pedestal contents remain governed by normal pedestal persistence. This is preferable to storing authoritative progress on the item, which would allow progress to move between rituals.

### D5. Keep power in the core cache and expose a routed input endpoint

The forge will declare `spiritInRatePerSecond = 4000 × 4^level` and `spiritOutRatePerSecond = 0`. The core capacity branch will return `40000 × 4^level`. Runtime consumption will use the core cache only; it will not silently pull from unrelated nearby cores or bypass the cache through the socket. This keeps the configured capacity, route rate, and forge drain one coherent budget.

The per-level values will be COMMON configuration entries, with separate base values for duration, drain, capacity, input rate, and the level multipliers. The current pattern exposes levels 0-2, but the calculation will be written in a form that can be extended to later levels without changing persisted job meaning.

The current formulas produce the following values:

| Level | Duration | Drain per primary | Capacity | Routed input |
|---:|---:|---:|---:|---:|
| 0 | 8 s | 200/s | 40,000 | 4,000/s |
| 1 | 4 s | 800/s | 160,000 | 16,000/s |
| 2 | 2 s | 3,200/s | 640,000 | 64,000/s |

The total power per completed job is therefore `1600 × 2^level`, which is intentional under the requested duration and drain formulas.

### D6. Resolve block-ore doubling through Forge/NeoForge tags

The multiplier predicate will require all of the following:

1. The primary item is a `BlockItem`.
2. Its block is in `c:block:ores`.
3. Its item is in `c:item:ores`.

The supported Gensokyo block ore items will be added to the appropriate common tags. Rough items will not be added to the block-ore classification path and will never be doubled. `oni_stone` will not receive a special smelting rule in this change; adding it to an ore classification tag does not itself make it smeltable.

The multiplier is applied after recipe resolution, so a recipe result count of one becomes two and a recipe result count greater than one becomes twice that count. Item components and result identity are preserved. Output stacks are split by the result item's maximum stack size before being sent to `RitualOutputs`.

### D7. Use the existing output and information-line channels

Completed output will use `RitualOutputs.spawn`, with the existing configurable radius and pickup delay. No output slot or client screen will be added.

`uiInfo` will emit a summary line for cache, aggregate drain, and active-job count, followed by one line per running or waiting job. Each job line will use the primary item as its icon, a short localized label, a progress value, and remaining seconds. Special jobs waiting for charcoal will show the missing count in a compact label or tooltip. Long numeric totals will use `InfoLine.compact`.

The existing scrollable information area is sufficient for up to the pattern's maximum number of pedestals. Server-side particle effects are not part of this change; if visual fire effects are later added, they must follow the client BER rendering rule rather than per-tick server particle packets.

### D8. Register the behavior and keep the existing pattern

The implementation will add a `KANAYAMAHIKO` pattern constant and behavior registration in `RitualBehaviors`, add the capacity dispatch in `RitualCoreBlockEntity`, and add the in-rate declaration in the behavior. The existing pattern already has `toggleable: true`; no pattern geometry or palette change is required.

The behavior will expose a machine-readable debug summary containing pattern level, active/waiting/cancelled counts, bound support count, per-job drain, cache, capacity, and in-rate. This gives the external test harness a stable probe without exposing internal NBT structure.

## Risks / Trade-offs

- [A recipe is removed or changed during an active job] → Lock the recipe ID/result, revalidate on reload and before completion, and cancel without consuming inputs or emitting stale output.
- [A player removes and replaces an identical stack between server ticks] → Snapshot item and component identity for normal detection; if strict mutation-level detection is required later, add a pedestal revision counter rather than relying on item equality.
- [Many active jobs create synchronized output entities] → Split only when required by max stack size, retain the configured output path, and monitor entity counts during high-tier testing.
- [The first job consumes a shared power budget] → Use deterministic queue order, per-job pause semantics, and fixed-point power carry; never silently reset progress on a power shortage.
- [A recipe matches multiple supported types] → Use the fixed `SMELTING > BLASTING > SMOKING` priority and record the selected type in the job/debug state.
- [Adding common ore tags affects third-party tooling] → Keep tags limited to ore classification, do not register vanilla recipes, and test that no vanilla furnace recipe is generated.
- [The full-cache power budget differs from total concurrent drain] → Treat in-rate as a replenishment ceiling, expose aggregate drain in the GUI, and test sustained operation separately from a full-cache burst.
- [The generic ritual recipe model is accidentally reused] → Keep forge rules and job scheduling in a separate behavior-specific layer; do not alter aggregate matcher semantics.
- [GUI lines overflow with 32 jobs] → Use short labels, compact totals, tooltips, and the existing scroll mechanism; avoid a pattern-specific client screen.

## Migration Plan

1. Add the forge-rule data and common ore tags without changing the existing pattern.
2. Add configuration fields, behavior registration, capacity dispatch, session persistence, and debug output.
3. Add the resolver, scheduler, job state machine, output splitting, and GUI information lines.
4. Add static tests for recipe selection, support allocation, scaling, multiplier predicates, and persistence round trips.
5. Run language, pattern, compile, and runtime smoke checks; manually verify all three supported vanilla recipe types, all six Gensokyo special input variants, input removal, power pause/resume, reload, and both result-count paths.
6. Roll back by removing the behavior registration, forge-rule data, tags, and config entries; the pre-existing empty forge pattern remains harmless and can be left in place.

## Open Questions

- Vanilla recipe experience is intentionally not awarded in this change; adding XP parity can be handled as a separate balance decision.
- Only the three requested vanilla recipe types are supported. Third-party custom cooking types need an explicit adapter contract.
- The current pattern exposes levels 0-2. Extending the pattern to levels 3-5 should preserve the locked-level behavior and revalidate capacity/rate overflow before adding those tiers.
