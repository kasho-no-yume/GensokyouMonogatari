## 1. 分阶门槛改世界进度语义（D1）

- [x] 1.1 新增隐形 advancement `data/gensokyou/advancement/guide/gensokyo_unlock.json`（`changed_dimension → gensokyou:gensokyo`，无 display）
- [x] 1.2 `GuideTierProgress` 收敛：只授予/回收 `guide/tier_4`/`tier_5`（temperLevel 过渡），移除 1..3 的处理
- [x] 1.3 `gen_ritual_multiblock.py` gate 映射改造：level 1→`nether_unlock`、2→`end_unlock`、3→`gensokyo_unlock`、4/5→`tier_4`/`tier_5`、0→无

## 2. 渲染组件去材料 + 自动换行（D3）

- [x] 2.1 `RitualTierComponent` 删除「搭建材料」段（`renderMaterials` 及其调用/常量）
- [x] 2.2 `RitualTierComponent` 参数行与供品行改用 `Font.split(Component, 116)` 自动换行，按实际行高推进 y
- [x] 2.3 `RitualPageComponent` 配方卡头部/产物名改用 `Font.split` 自动换行
- [x] 2.4 视需要设置 `book.json` 的 `text_overflow_mode`（暂倾向不设，用 Patchouli 默认）
- [x] 2.5 清理无用 lang 键：`gensokyou.book.ritual.materials*`、`gensokyou.book.ritual.consume.*` 若无引用一并核对

## 3. 生成器改造（D8）

- [x] 3.1 `gen_ritual_multiblock.py` 增加 `--level-gate` 映射与 `--sortnum`/`--secret`/条目级门槛参数
- [x] 3.2 支持多张 `--text-page`（故事+引言已在上游拆好），条目输出带 sortnum/secret/advancement
- [x] 3.3 用改造后生成器批量重生成 16 个仪式条目

## 4. 条目拆分与补全（D2 / D4 / D7）

- [x] 4.1 改写 `rituals_basics.json`：只留入门正文（p1/p2），去掉八百万神恩全部页
- [x] 4.2 新建 `kami_no_megumi_circle` 独立条目（5 阶结构 + 5 参数页，无配方页）
- [x] 4.3 删除 `rituals_nether.json`、`rituals_end.json`
- [x] 4.4 新建其余 15 个仪式条目：`bafang_guiyuan_circle`、`haniyasu_circle`、`kagutsuchi_flame_circle`、`kaya_no_hime_circle`、`kukunochi_circle`、`nichirin_circle`、`oyamatsumi_circle`、`resonance_relay`、`sair_energy_circle`、`shujou_yoroku_circle`、`tsukikage_circle`、`watatsumi_circle`、`wujinzang_circle`、`yumewatari_circle`、`zaohua_circle`
- [x] 4.5 排序：仪式入门 `sortnum=0`，其余 1..16（与列表顺序一致）
- [x] 4.6 条目级门槛：`secret:true` + 最低结构阶门槛（min 0 → 无）
- [x] 4.7 配方页：`zaohua_circle` 不补（显式极多/开放式）；`kami_no_megumi_circle` 10 条→补；其余无配方不适用
- [x] 4.8 占位仪式（`barrier_break_circle`/`summon_circle`）不建条目；待用户补全清单后复核
- [x] 4.9 生产/献祭仪式产出页（`gensokyou:loot_page` + `RitualLootComponent`）：4 工具仪式按材质各 6 页、绵津见按等级 3 页，物品图标网格 + 悬停概率；概率由生成器按 `ritual_loot` 权重归一内联（同 JEI 口径）

## 5. 文案与 lang（D5 / D6）

- [x] 5.1 为 16 个仪式各撰写「故事 + 简短引言」（含蓄、不点破；生成期拆 1~2 张 text 页）
- [x] 5.2 `lang/zh_cn.json` 新增条目名与正文键；删除 `rituals_nether`/`rituals_end` 键；**不动 `en_us.json`**
- [x] 5.3 各条目 icon 选取（代表性物品清单提交用户过目）
- [x] 5.4 `python tools/lang_audit.py` 零缺失（zh_cn 口径）

## 6. skill 修订（D5）

- [x] 6.1 `ritual-code-dev/SKILL.md` 新增「指导书补充规范」章：故事/引言口吻、分阶展示、换行/分页纪律、配方页取舍（>12 或显式指认→不补）、占位仪式不补章、拿不准问用户、阶级门槛表 0-5
- [x] 6.2 checklist 增加「补充指导书」步骤（生成器命令 + 文案 + lang + 排序/门槛）
- [x] 6.3 §8 仪式状态表按真实实现重写：17 行为全注册；工具献祭族/昼夜发电机族为基类子类；标注占位 `barrier_break_circle`/`summon_circle`；`barrier_break` 无 pattern

## 7. spec 更新

- [x] 7.1 `openspec/specs/guide-book/spec.md`：修改「进度解锁机制」（分阶门槛=世界进度语义）
- [x] 7.2 修改「仪式章节配方卡与结构展示」（去材料、自动换行、配方页取舍）
- [x] 7.3 新增「仪式条目内容规范」需求

## 8. 验收

- [x] 8.1 `openspec validate complete-ritual-book-entries --strict` 通过
- [x] 8.2 `gradlew compileJava` 通过（组件改动）
- [x] 8.3 游戏内回归：入门 + 16 条目；0 阶仪式无条件可见、1/2/3 阶按下界/末地/幻想乡解锁；分阶页不超框不裁切；`/reload` 后仍正确
- [x] 8.4 占位仪式不出现在书中（待用户确认完整清单）
