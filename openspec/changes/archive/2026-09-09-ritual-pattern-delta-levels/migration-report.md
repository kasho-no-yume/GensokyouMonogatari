# ritual-pattern-delta-levels 迁移比对与全量校验留档

生成命令：`python tools/validate_ritual_pattern.py --convert-v4`（转换 + 写前/写后累积等价断言），随后自动执行全量 v5 校验。

```

== gensokyou:barrier_break_circle  v4→v5 迁移比对（逐级累积展开 / 季度条目数）
   level 1: 展开 17→17 格 条目 5→5  [C×1, P×4, S×12]
   合计: 展开 17→17 格，条目 5→5

== gensokyou:capacitor_circle  v4→v5 迁移比对（逐级累积展开 / 季度条目数）
   level 1: 展开 5→5 格 条目 2→2  [C×1, S×4]
   合计: 展开 5→5 格，条目 2→2

== gensokyou:generator_circle  v4→v5 迁移比对（逐级累积展开 / 季度条目数）
   level 1: 展开 477→477 格 条目 120→120  [1×24, C×1, X×452]
   level 2: 展开 1133→1133 格 条目 284→164  [1×24, 2×32, A×12, C×1, D×44, E×8, J×16, P×4, U×184, X×808]
   level 3: 展开 1217→1217 格 条目 305→21  [1×24, 2×32, 3×28, A×16, C×1, D×44, E×8, J×16, P×4, Q×8, U×212, W×16, X×808]
   level 4: 展开 1425→1425 格 条目 357→52  [1×24, 2×32, 3×28, 4×92, A×16, B×4, C×1, D×44, E×8, J×32, K×84, L×8, P×4, Q×8, R×4, U×212, W×16, X×808]
   level 5: 展开 1645→1645 格 条目 412→55  [1×24, 2×32, 3×28, 4×92, 5×60, A×20, B×8, C×1, D×44, E×8, H×36, J×44, K×84, L×12, O×68, P×4, Q×8, R×4, T×24, U×212, W×16, X×808, Y×8]
   合计: 展开 5897→5897 格，条目 1478→412

== gensokyou:processing_circle  v4→v5 迁移比对（逐级累积展开 / 季度条目数）
   level 1: 展开 13→13 格 条目 4→4  [C×1, P×4, S×8]
   合计: 展开 13→13 格，条目 4→4

== gensokyou:relay_circle  v4→v5 迁移比对（逐级累积展开 / 季度条目数）
   level 1: 展开 5→5 格 条目 2→2  [C×1, P×4]
   合计: 展开 5→5 格，条目 2→2

== gensokyou:summon_circle  v4→v5 迁移比对（逐级累积展开 / 季度条目数）
   level 1: 展开 9→9 格 条目 3→3  [C×1, S×8]
   合计: 展开 9→9 格，条目 3→3

== gensokyou:tempering_circle  v4→v5 迁移比对（逐级累积展开 / 季度条目数）
   level 1: 展开 17→17 格 条目 5→5  [C×1, P×4, S×12]
   合计: 展开 17→17 格，条目 5→5

迁移完成: 转换 7 个文件，跳过 0 个（已是 v5）

---- 全量校验（迁移后） ----

WARN: gensokyou:barrier_break_circle: key S (#gensokyou:ritual_stones) 首次出现于 level 1，但品阶下限仅 0
WARN: gensokyou:barrier_break_circle: key P (#gensokyou:ritual_pedestals) 首次出现于 level 1，但品阶下限仅 0
WARN: gensokyou:capacitor_circle: key S (#gensokyou:ritual_stones) 首次出现于 level 1，但品阶下限仅 0
WARN: gensokyou:processing_circle: key S (#gensokyou:ritual_stones) 首次出现于 level 1，但品阶下限仅 0
WARN: gensokyou:processing_circle: key P (#gensokyou:ritual_pedestals) 首次出现于 level 1，但品阶下限仅 0
WARN: gensokyou:relay_circle: key P (#gensokyou:ritual_pedestals) 首次出现于 level 1，但品阶下限仅 0
WARN: gensokyou:summon_circle: key S (#gensokyou:ritual_stones) 首次出现于 level 1，但品阶下限仅 0
WARN: gensokyou:tempering_circle: key S (#gensokyou:ritual_stones) 首次出现于 level 1，但品阶下限仅 0
WARN: gensokyou:tempering_circle: key P (#gensokyou:ritual_pedestals) 首次出现于 level 1，但品阶下限仅 0

== gensokyou:barrier_break_circle  (尝试优先级权重=17)
   level 1: 17 格  [C×1, P×4, S×12]

== gensokyou:capacitor_circle  (尝试优先级权重=5)
   level 1: 5 格  [C×1, S×4]

== gensokyou:generator_circle  (尝试优先级权重=5897)
   level 1: 477 格  [1×24, C×1, X×452]
   level 2: 1133 格  [1×24, 2×32, A×12, C×1, D×44, E×8, J×16, P×4, U×184, X×808]
   level 3: 1217 格  [1×24, 2×32, 3×28, A×16, C×1, D×44, E×8, J×16, P×4, Q×8, U×212, W×16, X×808]
   level 4: 1425 格  [1×24, 2×32, 3×28, 4×92, A×16, B×4, C×1, D×44, E×8, J×32, K×84, L×8, P×4, Q×8, R×4, U×212, W×16, X×808]
   level 5: 1645 格  [1×24, 2×32, 3×28, 4×92, 5×60, A×20, B×8, C×1, D×44, E×8, H×36, J×44, K×84, L×12, O×68, P×4, Q×8, R×4, T×24, U×212, W×16, X×808, Y×8]

== gensokyou:processing_circle  (尝试优先级权重=13)
   level 1: 13 格  [C×1, P×4, S×8]

== gensokyou:relay_circle  (尝试优先级权重=5)
   level 1: 5 格  [C×1, P×4]

== gensokyou:summon_circle  (尝试优先级权重=9)
   level 1: 9 格  [C×1, S×8]

== gensokyou:tempering_circle  (尝试优先级权重=17)
   level 1: 17 格  [C×1, P×4, S×12]

全部 pattern 校验通过
```
