## 1. Data and configuration

- [x] 1.1 Confirm the existing `kanayamahiko_circle` pattern remains unchanged, remains `toggleable`, and exposes levels 0-2 with 8/16/32 cumulative pedestals.
- [x] 1.2 Add the three supported Gensokyo block ore items (`cinnabar`, `spirit_iron_ore`, `star_silver_ore`) to the appropriate `c:block:ores` and `c:item:ores` data tags without adding vanilla cooking recipes.
- [x] 1.3 Add a dedicated forge-rule data file for the six special input variants, their charcoal counts, base results, and result counts; keep all `rough_*` variants out of the block-ore multiplier data.
- [x] 1.4 Add a hot-reloadable loader/validator for the dedicated forge-rule data with clear errors for unknown items, duplicate primaries, invalid counts, and missing results.
- [x] 1.5 Add COMMON configuration entries for the base duration, duration level divisor, base drain, base cache capacity, base routed input rate, and level multipliers, with defaults matching the requested formulas.
- [x] 1.6 Confirm no wood-to-spirit-charcoal recipe is added and no existing ritual recipe is moved into the forge data.

## 2. Behavior registration and recipe resolution

- [x] 2.1 Add the `KANAYAMAHIKO` pattern constant and register a new `KanayamahikoBehavior` in `RitualBehaviors`.
- [x] 2.2 Implement server-side runtime lookup for `RecipeType.SMELTING`, `RecipeType.BLASTING`, and `RecipeType.SMOKING`, excluding campfire and custom recipe types.
- [x] 2.3 Implement deterministic duplicate-type priority `SMELTING > BLASTING > SMOKING` and retain the selected recipe identity and source type in each job.
- [x] 2.4 Implement special-rule precedence over generic runtime recipes for cinnabar, spirit iron, star silver, and their rough-item counterparts.
- [x] 2.5 Implement the block-ore predicate using `BlockItem`, `c:block:ores`, and `c:item:ores`; apply the result multiplier only after recipe/rule result resolution.
- [x] 2.6 Preserve result item components while changing only the count, and reject or defer invalid empty results according to the forge-rule validator.

## 3. Job state and scheduling

- [x] 3.1 Define a versioned Kanayamahiko session stored by the core block entity, with absolute primary `BlockPos` identity and monotonic first-come sequence numbers.
- [x] 3.2 Persist locked primary/auxiliary positions, expected stack identities, recipe/rule identity, result, elapsed progress, locked level, and fixed-point power carry in the core save/load path.
- [x] 3.3 Implement pedestal scanning that recognizes only the current structure's `#gensokyou:ritual_pedestals` positions and treats each primary input independently.
- [x] 3.4 Implement special-job support allocation in first-come order, with atomic charcoal binding and exclusive use of each auxiliary pedestal.
- [x] 3.5 Release auxiliary bindings when a job is cancelled and allow the next waiting job to claim still-present charcoal.
- [x] 3.6 Implement per-tick validation of every locked primary and auxiliary stack; cancel on empty, replacement, or component mismatch without consuming inputs or emitting output.
- [x] 3.7 Implement the state transitions for waiting, running, power-paused, cancelled, and completed jobs; preserve valid state across stop/start.
- [x] 3.8 Reconcile restored jobs after world reload and cancel jobs whose locked recipe was removed or changed by a datapack reload.
- [x] 3.9 Preserve surviving jobs by physical position when the same pattern changes level; apply the new level only to newly created jobs.
- [x] 3.10 Clear forge session state on structure loss and ensure dismantling the core does not delete pedestal contents.

## 4. Timing, power, and output

- [x] 4.1 Implement the per-level duration formula with fixed-point tick conversion so level 0/1/2 resolve to 8/4/2 seconds.
- [x] 4.2 Implement per-job spirit drain using the configured 200 × 4^level units per second and charge only the core's own cache.
- [x] 4.3 Add the Kanayamahiko capacity dispatch for 40000 × 4^level in `RitualCoreBlockEntity.getCapacity()`.
- [x] 4.4 Declare the static per-level routed input rate 4000 × 4^level and keep the output rate at zero.
- [x] 4.5 Implement deterministic per-tick power allocation and pause a job when its exact cost is unavailable, preserving progress and unused cache.
- [x] 4.6 Implement completion ordering as validate locked inputs, consume the primary and bound supports exactly once, then emit the result.
- [x] 4.7 Apply the block-ore multiplier to generic and special results, and split output stacks that exceed the result item's maximum stack size.
- [x] 4.8 Route completed results through `RitualOutputs.spawn` with the configured radius and pickup delay; never write results to a pedestal or core inventory.

## 5. GUI, diagnostics, and player-facing integration

- [x] 5.1 Add `uiInfo` summary data for cache, capacity, aggregate drain, active jobs, waiting jobs, and bound auxiliary count using compact values.
- [x] 5.2 Add one scrollable information line per running or waiting job with the primary item icon, short label, progress, and remaining time.
- [x] 5.3 Add localized labels for waiting charcoal, paused-for-power, idle, and active states without exposing raw item IDs.
- [x] 5.4 Add a machine-readable Kanayamahiko debug summary for automated probes, including level, job counts, locked recipe IDs, support bindings, cache, capacity, drain, and in-rate.
- [x] 5.5 Add or update the ritual guide entry to explain the three supported furnace recipe sources, Gensokyo ratios, block-only doubling, and that wood-to-spirit-charcoal is not included.
- [x] 5.6 Run the language audit and ensure all new Chinese keys are present with no missing or raw-key regressions.

## 6. Automated verification

- [x] 6.1 Add pure tests for recipe-type selection, deterministic duplicate priority, special-rule precedence, and reload invalidation.
- [x] 6.2 Add pure tests for the six special input variants, charcoal ratios, mixed-job first-come allocation, exclusive bindings, and released bindings.
- [x] 6.3 Add pure tests for block-ore versus rough-item doubling, non-ore blocks, result-count multiplication, component preservation, and max-stack splitting.
- [x] 6.4 Add persistence tests for active progress, auxiliary coordinates, locked level, first-come ordering, world reload, pattern expansion, and recipe removal.
- [x] 6.5 Add power tests for level 0/1/2 duration, per-job drain, capacity, routed input rate, fractional carry, pause/resume, and deterministic allocation.
- [x] 6.6 Add GUI data tests for active rows, waiting-charcoal rows, remaining time, compact totals, and scroll-safe line count.
- [x] 6.7 Add a data audit proving no Gensokyo material has a vanilla smelting, blasting, or smoking recipe and that common ore tags do not create recipes.

## 7. Build and runtime validation

- [x] 7.1 Run `openspec validate add-kanayamahiko-smelting --strict` and resolve all specification errors.
- [ ] 7.2 Run `gradlew.bat compileJava --console=plain` and the project test task used by the repository.
- [x] 7.3 Run `python tools/lang_audit.py` and confirm a zero exit code.
- [x] 7.4 Run `python tools/validate_ritual_pattern.py --test-out run/world/datapacks/gs_ritual_test` and confirm the existing Kanayamahiko pattern remains valid.
- [x] 7.5 Manually verify a runtime furnace, blast-furnace, and smoker recipe execute with fixed forge timing and no vanilla furnace recipe for Gensokyo materials.
- [x] 7.6 Manually verify all six Gensokyo special input variants, mixed charcoal allocation, block-only doubling, rough-item non-doubling, and ignored `oni_stone`.
- [x] 7.7 Manually verify removing a primary or bound charcoal cancels only its job, stopping pauses jobs, power shortage pauses and resumes, and results drop near the core.
- [x] 7.8 Manually verify world reload, structure upgrade, pattern loss, recipe reload, output stack splitting, and GUI scrolling at the maximum pedestal count.
