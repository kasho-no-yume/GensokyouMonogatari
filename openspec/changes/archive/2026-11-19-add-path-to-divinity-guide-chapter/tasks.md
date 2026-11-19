## 1. 进度 advancement 骨架

- [x] 1.1 新建 `data/gensokyou/advancement/guide/path/` 目录，创建 `step_01_done.json` … `step_12_done.json`，全部无 `display`，按步骤顺序设 `parent` 父子链
- [x] 1.2 按 spec 表格配置八步原版触发器：step_01/02/03/05/06/08 用 `inventory_changed`（step_02 复用 `achievement/ritual_core` 的判定物品），step_09 复用 `achievement/big_fairy` 的 `player_killed_entity`，step_11 用 `player_killed_entity` 指向八云蓝残影实体，step_12 复用 `guide/gensokyo_unlock` 的维度条件
- [x] 1.3 step_04/07/10 用 `minecraft:impossible` criterion（供 Java 按 id 授予），criterion 名固定为 `done` 并在 Java 侧同一常量引用
- [x] 1.4 step_05/06 的"任一材料/精炼矿"在 advancement 的 `items` 条件里列举物品 id 做 OR（不新建 tag）
- [x] 1.5 用 `openspec validate` 与现有 advancement JSON 校验工具确认 12 个文件可被加载、无裸 id

## 2. 第七章节与 12 个进度条目

- [x] 2.1 新建 `assets/gensokyou/patchouli_books/gensokyou_book/en_us/categories/path.json`，name/description 走 lang 键，图标用 `gensokyou:guide_book`
- [x] 2.2 新建 12 个 `entries/path_step_NN.json`，`category` 为 `gensokyou:path`，`sortnum` 1–12，图标取对应步骤代表物品
- [x] 2.3 每个条目（第 1 步除外）设 `advancement` 指向 `step_{N-1}_done`，全部设 `turnin` 指向 `step_N_done`，MUST NOT 设 `secret`
- [x] 2.4 每个条目正文页至少一页 `patchouli:text`；第 4 步附迦具土炎祭的 `patchouli:spotlight`/链接页，第 8 步链接到千机核心、等级核、麟弹、方术装配台、增幅核条目
- [x] 2.5 跨章链接用 `$(l:gensokyou:...)` 指向已有仪式/物品/BOSS 条目，确认目标 id 存在

## 3. 文案（只维护 zh_cn）

- [x] 3.1 在 `assets/gensokyou/lang/zh_cn.json` 新增第七章节 name/description 键
- [x] 3.2 撰写 12 条正文键：故事开篇、含蓄暗示、每条结尾留向下一步的钩子；不出现 id/字段名/路径/工程占位语
- [x] 3.3 物品名统一为游戏内译名（「弹幕方术装配台」「弹幕核：麟弹」「等级核」「增幅核」「千机核心」），不使用「武器核」「方术台」等简称
- [x] 3.4 第 6 步措辞收敛为"任一精炼矿"，并暗示更深矿物需等材料带信物
- [x] 3.5 文案只用 `$(br)`/`$(br2)` 换行，不使用 `\n`；确认无裸 key

## 4. Java 埋点与旧档兜底

- [x] 4.1 新建服务端进度授予工具类（与 `GuideTierProgress` 同包或新建 `GuidePathProgress`），提供 `awardStep(ServerPlayer, int)`，内部取 advancement holder、`holder == null` 时安全跳过（照 `GuideTierProgress` 模式）
- [x] 4.2 在迦具土炎祭搭建成功路径埋点调用 `awardStep(player, 4)`；并补巡检附近已成型核心，覆盖手工摆放（实机发现仅挂构建器路径会漏）
- [x] 4.3 在神恩成功路径按实际达成的 `temperLevel` 调用 `awardStep(player, 7)` / `awardStep(player, 10)`
- [x] 4.4 新增 `PlayerTickEvent.Post` 巡检（每 20 tick 或登录时），按玩家当前状态补授缺失的 step_01/02/03/05/06/08 等 inventory 类 criterion，覆盖旧存档
- [x] 4.5 确认 advancing 授予在服务端、客户端零加载；按仓库约定用 `tools/gradle_task.ps1 compileJava`/`build` 验收，不裸跑 gradlew

## 5. 规范、工具与验证

- [x] 5.1 更新 `openspec/specs/guide-book/spec.md`（归档时由本 change 的 delta 自动合并），确认"六大章节"措辞与 `secret` 例外的 spec 断言一致
- [x] 5.2 检查 `tools/book_preview.py`、`tools/audit_boss_book_entries.py`、lang 审计脚本对第七章节与非 secret 锁条目的容忍，必要时更新
- [x] 5.3 开发客户端实测三态：初始锁定可见 → 当前步骤 `(?)` 置顶 → 完成后消失；确认第 2 步能正常开放（用户实机确认通过）
- [x] 5.4 实测"提前完成补标记"：先合成千机核心，再完成第 7 步，确认第 8 步解锁瞬间直接显示已完成（用户实机确认通过）
- [x] 5.5 用 `openspec validate add-path-to-divinity-guide-chapter` 校验本 change，确认 apply-ready
