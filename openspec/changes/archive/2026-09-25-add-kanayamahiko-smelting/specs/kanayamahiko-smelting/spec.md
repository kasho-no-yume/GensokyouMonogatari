## ADDED Requirements

### Requirement: Runtime furnace recipe scope
The Kanayamahiko forge SHALL resolve ordinary inputs against the runtime recipe manager for `minecraft:smelting`, `minecraft:blasting`, and `minecraft:smoking`. It MUST NOT use `minecraft:campfire_cooking` or third-party custom recipe types. If one input matches multiple supported recipe types, the forge SHALL select one recipe using the deterministic priority `SMELTING > BLASTING > SMOKING`. The forge SHALL use its own level-based duration instead of the recipe's `cookingtime` value.

#### Scenario: Supported recipe types execute
- **WHEN** a pedestal input matches a registered smelting, blasting, or smoking recipe
- **THEN** the forge creates a job using that recipe and produces its declared result

#### Scenario: Unsupported recipe types are ignored
- **WHEN** an input matches only a campfire or custom recipe type
- **THEN** the forge leaves the input idle and produces no result

#### Scenario: Duplicate supported recipes are deterministic
- **WHEN** one input matches supported recipes with different results
- **THEN** the forge selects the first available type in the order smelting, blasting, smoking

#### Scenario: Reloaded recipes are observed
- **WHEN** a supported recipe is added, removed, or changed by a datapack reload
- **THEN** new jobs use the new runtime recipe set and affected active jobs are cancelled when their locked recipe is no longer valid

### Requirement: Independent primary-input jobs
Each eligible primary pedestal SHALL create an independent forge job. Auxiliary ingredients SHALL be bound to their primary job and SHALL NOT create independent jobs. A job SHALL retain its primary input and bound auxiliary inputs on their pedestals while processing, and SHALL consume those inputs only after successful completion.

#### Scenario: Multiple primary inputs run independently
- **WHEN** several eligible primary items occupy separate pedestals
- **THEN** each item has its own remaining time, progress, power consumption, and completion state

#### Scenario: Inputs remain during processing
- **WHEN** a job is waiting, paused, or actively smelting
- **THEN** its primary and bound auxiliary items remain on their pedestals

#### Scenario: Completion consumes the locked inputs
- **WHEN** a job reaches its completion threshold with all locked inputs still present
- **THEN** the primary item and every bound auxiliary item are consumed exactly once before the result is emitted

#### Scenario: No recipe means idle
- **WHEN** a pedestal contains an item with no supported runtime recipe and no special forge rule
- **THEN** the item remains untouched and no job is created

### Requirement: Gensokyo special refining rules
The forge SHALL provide these special rules, with each rule taking priority over generic runtime recipes:

- `gensokyou:cinnabar` or `gensokyou:rough_cinnabar_ore` plus `gensokyou:spirit_charcoal` ×1 → `gensokyou:refined_cinnabar` ×1 base result.
- `gensokyou:spirit_iron_ore` or `gensokyou:rough_spirit_iron_ore` plus `gensokyou:spirit_charcoal` ×1 → `gensokyou:spirit_iron` ×1 base result.
- `gensokyou:star_silver_ore` or `gensokyou:rough_star_silver_ore` plus `gensokyou:spirit_charcoal` ×2 → `gensokyou:star_silver` ×1 base result.

The special rules SHALL consume the listed charcoal only on successful completion. `gensokyou:oni_stone`, `spirit_charcoal` alone, and unlisted items SHALL NOT receive a special forge rule.

#### Scenario: Cinnabar and rough cinnabar
- **WHEN** either cinnabar block item or rough cinnabar is paired with one spirit charcoal
- **THEN** one special job can start and produces refined cinnabar after the normal forge duration

#### Scenario: Spirit iron and rough spirit iron
- **WHEN** either spirit iron ore block item or rough spirit iron is paired with one spirit charcoal
- **THEN** one special job can start and produces spirit iron after the normal forge duration

#### Scenario: Star silver and rough star silver
- **WHEN** either star silver ore block item or rough star silver is paired with two spirit charcoals
- **THEN** one special job can start and produces star silver after the normal forge duration

#### Scenario: Special rule wins over generic recipe
- **WHEN** a supported generic recipe also matches one of the special inputs
- **THEN** the forge uses the charcoal-bound special rule and does not start a generic single-input job

#### Scenario: Missing charcoal keeps a special primary waiting
- **WHEN** a special primary is present without enough unclaimed spirit charcoal
- **THEN** the primary remains on its pedestal and no special job starts

### Requirement: Block-ore result multiplier
A job's declared result SHALL be multiplied by two only when its primary input is a `BlockItem` whose block is a member of the Forge/NeoForge ore block tag and whose item is a member of the corresponding ore item tag. Rough items, non-ore block items, and ordinary non-block items SHALL NOT receive this multiplier. The multiplier SHALL apply after the selected recipe's result count is determined, and the result's item, components, and other stack data SHALL be preserved.

#### Scenario: Vanilla ore block doubles
- **WHEN** an eligible recipe consumes a vanilla ore block item and declares a result count of one
- **THEN** the forge emits two result items

#### Scenario: Existing result count is multiplied
- **WHEN** an eligible ore-block recipe declares a result count greater than one
- **THEN** the forge emits twice that declared count

#### Scenario: Rough ore does not double
- **WHEN** a special or generic job consumes `rough_cinnabar_ore`, `rough_spirit_iron_ore`, or `rough_star_silver_ore`
- **THEN** the declared result count is emitted without the block-ore multiplier

#### Scenario: Non-ore block does not double
- **WHEN** a recipe consumes a block item that is not in the Forge/NeoForge ore tags
- **THEN** the declared result count is emitted unchanged

### Requirement: Level scaling and spirit power
For a structure level `L`, the forge SHALL use the following configurable base formulas: duration `8 / 2^L` seconds, consumption `200 × 4^L` spirit power per second per primary job, cache capacity `40000 × 4^L`, and routed input rate `4000 × 4^L` spirit power per second. The current pattern exposes levels 0 through 2. Consumption SHALL be paid from the core's own spirit cache; route input SHALL be able to fill that cache through the declared input-rate endpoint.

#### Scenario: Level zero scaling
- **WHEN** a level-zero forge runs one primary job
- **THEN** its duration is eight seconds, its consumption is 200 spirit power per second, its capacity is 40000, and its routed input rate is 4000 per second

#### Scenario: Level one scaling
- **WHEN** a level-one forge runs one primary job
- **THEN** its duration is four seconds, its consumption is 800 spirit power per second, its capacity is 160000, and its routed input rate is 16000 per second

#### Scenario: Level two scaling
- **WHEN** a level-two forge runs one primary job
- **THEN** its duration is two seconds, its consumption is 3200 spirit power per second, its capacity is 640000, and its routed input rate is 64000 per second

#### Scenario: Power shortage pauses rather than cancels
- **WHEN** an enabled job cannot pay its current per-tick spirit cost
- **THEN** the job pauses with its inputs and progress intact, and resumes when sufficient cache is available

### Requirement: First-come job scheduling
Jobs SHALL be ordered by the time their primary input is first detected. Earlier jobs SHALL have first claim on unbound auxiliary ingredients and available per-tick power. A bound auxiliary item MUST NOT be assigned to more than one job. When a job is cancelled, its auxiliary bindings SHALL be released and SHALL become available to later waiting jobs.

#### Scenario: Limited charcoal is allocated first-come
- **WHEN** five eligible spirit iron primaries and four unbound charcoal pedestals are present
- **THEN** four jobs start, each using one distinct charcoal, and one primary remains waiting

#### Scenario: Auxiliary bindings are exclusive
- **WHEN** two waiting special jobs compete for one remaining charcoal
- **THEN** only the earlier job may bind it and the later job remains waiting

#### Scenario: Released charcoal can be reused
- **WHEN** an earlier job is cancelled and its bound charcoal remains on the pedestal
- **THEN** the next waiting job in queue order may claim that charcoal

### Requirement: Input replacement and cancellation
A running or paused job SHALL validate its locked primary and auxiliary stacks on every server tick. If any required stack is empty, changed to another item, or otherwise no longer matches the locked input, that job SHALL be cancelled on the next tick. Cancellation SHALL NOT refund spirit power already consumed and SHALL NOT emit a result. The forge SHALL treat the stop button as a pause operation; stopping SHALL preserve job state and inputs so a later start can reconcile and resume valid jobs.

#### Scenario: Removing the primary cancels its job
- **WHEN** a player or automation removes the primary item during processing
- **THEN** that job stops progressing before the next advancement and emits no result

#### Scenario: Removing charcoal cancels the special job
- **WHEN** a bound spirit charcoal pedestal is emptied during a special job
- **THEN** that special job is cancelled and its primary remains on the pedestal

#### Scenario: Replacing an input is not accepted
- **WHEN** a locked pedestal is filled with a different item or component state
- **THEN** the old job is cancelled rather than silently applying progress to the replacement

#### Scenario: Stop and restart preserves valid work
- **WHEN** the player stops and later restarts the forge with unchanged inputs
- **THEN** valid locked jobs resume from their saved progress and no input is consumed merely for stopping

### Requirement: Persistent job state and lifecycle migration
Job state SHALL be stored in versioned data owned by the core block entity, keyed by absolute pedestal `BlockPos` rather than by an index in the current sorted pedestal list. State SHALL include the locked recipe identity and result, primary and auxiliary positions, expected input identities, elapsed progress, locked level, and first-come ordering data. World reload SHALL restore valid state. A level change within the same pattern SHALL preserve surviving jobs and apply new scaling only to newly started jobs. Structure loss SHALL clear forge job state; a recipe reload that removes or changes a locked recipe SHALL cancel the affected job.

#### Scenario: Reload restores progress
- **WHEN** the server saves and reloads while a forge job is active
- **THEN** the same primary, auxiliary bindings, locked recipe, and remaining progress are restored

#### Scenario: Pattern upgrade does not shift progress
- **WHEN** the structure expands from level 0 to level 1 and the sorted pedestal list changes
- **THEN** surviving jobs remain attached to their original physical pedestals

#### Scenario: Structure loss clears jobs
- **WHEN** a component block is removed and the forge no longer matches its pattern
- **THEN** forge job state is cleared while pedestal items remain governed by normal pedestal persistence

#### Scenario: Locked recipe invalidation cancels safely
- **WHEN** a datapack reload removes or changes a recipe used by an active job
- **THEN** that job is cancelled without consuming its inputs or emitting its old result

### Requirement: Result delivery
A completed job SHALL emit its result through the existing ritual output path near the core, using the configured output radius and default pickup delay. Results MUST NOT be written to a pedestal or core inventory. Result stacks that exceed the item's maximum stack size SHALL be split into multiple item entities without changing the total count.

#### Scenario: Product drops beside the core
- **WHEN** a job completes
- **THEN** its result appears as an item entity within the configured horizontal output radius around the core

#### Scenario: Large result is split
- **WHEN** an ore multiplier or recipe result exceeds the result item's maximum stack size
- **THEN** all result items are emitted across valid item entities and none are discarded

### Requirement: Forge information lines
The forge behavior SHALL publish information lines through the existing `InfoLine` channel. Active jobs SHALL show the primary item icon, a short item label, progress, and remaining time. Special jobs waiting for charcoal SHALL show the missing auxiliary requirement. The GUI SHALL remain scrollable when the number of jobs exceeds the visible information area, and detailed totals SHALL be placed in compact text or tooltips rather than widening the panel.

#### Scenario: Active job shows remaining time
- **WHEN** a primary job is running
- **THEN** its information line identifies the primary item and shows its current remaining duration and progress

#### Scenario: Waiting special job shows shortage
- **WHEN** a special primary is waiting for unbound charcoal
- **THEN** its information line identifies the primary and the number of charcoal items still needed

#### Scenario: Many jobs remain browsable
- **WHEN** the forge has more active jobs than fit in the information box
- **THEN** the player can scroll through every active and waiting job without a client-side pattern-specific screen

### Requirement: Vanilla furnace exclusivity
The mod SHALL NOT register Gensokyo material outputs as vanilla `smelting`, `blasting`, or `smoking` recipes. Forge ore tags SHALL be used only for classification and SHALL NOT implicitly create a vanilla furnace recipe. Special Gensokyo rules SHALL remain forge-owned and SHALL NOT be added to the existing ritual recipe system or any vanilla cooking recipe directory.

#### Scenario: Gensokyo items do not enter vanilla furnaces
- **WHEN** a player places supported Gensokyo block or rough ore items in a vanilla furnace, blast furnace, or smoker
- **THEN** the mod provides no vanilla recipe that produces the Gensokyo refined result

#### Scenario: Ore tags do not create recipes
- **WHEN** supported block ore items are added to Forge/NeoForge ore tags
- **THEN** the tags classify doubling eligibility but do not register a vanilla cooking recipe
