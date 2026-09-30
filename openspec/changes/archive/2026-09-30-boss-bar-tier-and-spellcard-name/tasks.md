## 1. 阶级声明（D1/D2/D15）

- [x] 1.1 `TouhouBoss` 加 `default int bossTier() { return 1; }` 与 `default OptionalInt activeCardIndex() { return OptionalInt.empty(); }`——**必须带缺省实现**，否则 `FlandreEntity`（`extends Monster`）编译期被绑死到它并不成立的「血量比⇒选阶段」机制上
- [x] 1.2 `TrackRunner` 加 `currentIndex()` 字段（跟着 `selectCard` 走，不用 `indexOf`——`SpellCard` 是 record，结构化 `equals` 会让两张全同的卡指错下标）；`AbstractTouhouBoss` 覆写 `activeCardIndex()` 并透传，`runner == null` 时返回空。⚠️ `bossTier()` **不在** `AbstractTouhouBoss` 加覆写点：接口方法是 `public`，加 `protected` 同名方法会编译失败
- [x] 1.3 占位阶级分配：`BigFairy=1` / `Kuzumono=1` / `KitsuneBi=1` / `NomenMask=2` / `FlandreEntity=5`，每处加注释标明**占位待定**（理由见 design D3：四只召唤 BOSS 全是低阶百鬼夜行物；`FlandreEntity` 目前根本召不出来——`SummonBossEffects.REGISTRY` 只注册 4 只）
- [x] 1.4 `bossTier()` 越界回退到 1 —— `TouhouBossBarRenderer#clampTier`（客户端取 frame 索引前夹取，实体侧不抛异常）

## 2. 符卡名改 lang 键（D9/D14）

- [x] 2.1 `SpellCard` 记录 `String name` → `Component name`
- [x] 2.2 `BossCards` 14 处构造改 `card("<boss_id>", <序号>)` 辅助方法（键名按 boss_id + 卡序，MUST NOT 从中文名推导——中文名改过，键名跟着改会作废已有语言条目）
- [x] 2.3 `lang/zh_cn.json` + `lang/en_us.json` 各补 14 个键。⚠️ 原中文名从 `git show HEAD` 恢复，**不是**终端里看到的那几个（`散華/旋風/落華`、`経糸/結界/硬化`、`乱焔/泡/散`、`拍/連拍/乱拍/急拍/無終`）
- [x] 2.4 `tools/lang_audit.py` 加 `spellcard` 前缀 + **新增 `CARD_KEY_RE` 展开 `BossCards#card("id", n)` 调用**。⚠️ 只加前缀不够：这些键在 Java 里是拼接的，字面量正则一条都看不到，只会把前缀列进「人工过目」= 不校验
- [x] 2.5 三个 `name()` 消费点改 `.getString()`：`DanmakuPreview:188`、`DanmakuTestCommands:194`、`TrackRunner.describe:324`
- [x] 2.5b ⚠️ **计划漏掉的第 4 个消费点**：`BigFairyEntity#movementLocked` 用字面量比 `card.name()`。已改按 `currentIndex() == 0` 判定——**原比较恒不成立**（符卡表里没有「花符[弹幕花环]」这个名字），锁位行为从未生效。名字是显示层的东西，按名字判行为会在改 lang 键时静默改掉逻辑
- [x] 2.5c 4 个测试文件的 `new SpellCard("…")` → `Component.literal(…)`，诊断串里的 `card.name()` → `.getString()`
- [x] 2.6 验证：删掉 `spellcard.gensokyou.nomen_mask.3` → audit 报 `MISSING (1) boss spell-card keys not in lang` 且退出 1；恢复后退出 0

## 3. 符卡名同步（D6/D7/D8）

- [x] 3.1 `network/SpellCardNamePayload.java`：`record(int entityId, int cardIndex)`，`Type` = `Gensokyou.id("spellcard_name")`，`StreamCodec.composite(ByteBufCodecs.VAR_INT × 2)`
- [x] 3.2 `ModNetworking` 注册 `playToClient` → `ClientPayloadHandler::handleSpellCardName`
- [x] 3.3 `client/ClientSpellCardNames.java`：`Map<Integer,Integer>`（entityId → cardIndex）+ `put/remove/get/clear`
- [x] 3.4 `AbstractTouhouBoss#syncCard` 切卡分支内 `ModNetworking.broadcastSpellCardName(this, index)`。受众用 `PacketDistributor.sendToPlayersTrackingEntity`——与血条受众本就同一批人
- [x] 3.5 `ModNetworking#onStartTrackingBoss` 补发当前下标（挂在 `StartTracking`，与既有弹幕年龄包同一事件）
- [x] 3.6 `TouhouBossBarRenderer#onEntityLeave`（`EntityLeaveLevelEvent`）清 `ClientSpellCardNames` + `GHOSTS`
- [x] 3.7 渲染时 `spellCardLine(boss)`：下标 → `TouhouBoss#spellCardName(i)` → 缺键过滤。⚠️ 在**行高决策之前**定，顺序反了会「留空位却不画」
- [x] 3.8 `TouhouBoss#spellCardName(int)` 读侧接口（`default` 返回 empty）+ `AbstractTouhouBoss` 按 `spellCards()` 实现

## 4. 血条贴图与绘制（D4/D5/D13）

- [x] 4.1 `tools/textures/boss_bar.py`：`frame_1..5` 28×14 + `segment` 16×4。⚠️ 贴图做成 28×14 **模板**而非 182×14——182 宽里那 166px 中段拉伸后被整段替换，等于白画
- [x] 4.2 预览 → 目检（阶 1 一个点 / 阶 5 五个点 + 双侧边）→ `--write-assets` 落 `textures/gui/boss_bar/`
- [x] 4.3 `TouhouBossBarRenderer#nineSlice`：端头 12px 原样、中段拉伸至 `TALISMAN_BAR_WIDTH`（120~400）
- [x] 4.4 绘制序 **条身先、边框后**（`draw` 里先画三段条身，再 `nineSlice` 边框）
- [x] 4.5 `#tintedBlit`：手搓 `POSITION_TEX_COLOR` 四边形，tint ← `TierPalette.rgb(tier)`。⚠️ 原版独立纹理 blit 走 `POSITION_TEX`（**无颜色属性**），`setColor` 设的 `ColorModulator` uniform 不被 `core::position_tex` 读——两者相乘等于不染色
- [x] 4.6 `#framePresent(tier)` 探 classpath（问 `TextureManager` 无效：缺文件时它造 `SimpleTexture` 占位，类型与已加载的**一样**，从外面区分不了）→ 缺则 `#drawLegacy` 回落 fill + config 色。`TALISMAN_BAR_BODY_HEIGHT` 保留并仅服务于该路径
- [x] 4.7 5 阶差异沿用 `ritual_blocks.py` 递进语言：符首 1→3 行、符尾 1→2 行、侧边 1→2 列、阶徽点 1→5 个。**共用同一透明窗口**（行 2~9）——窗口随阶变会让条身高成为阶的函数，堆叠预算与截断判定全要分叉

## 5. 符卡行布局（D10/D11/D12）

- [x] 5.1 符卡行画在边框下方，右对齐至 `x + width - seal - 2`（朱印原位不动）= 「落款 + 钤印」
- [x] 5.2 行高 `max(config, 实际所需)`。⚠️ 实测所需 = 30（边框 14 + 撕边 4 + 余 2 + 符卡行 10），**不是** design 里估的 38——因为改用了 14px 边框模板 + 固定 8px 窗口。config 默认 28 → 30
- [x] 5.3 无符卡名 ⇒ 不绘制、`rowHeightNoCard()` 回落
- [x] 5.4 缺 lang 键 ⇒ `spellCardLine` 返回 empty ⇒ 走 5.3。判据 = `getString()` 仍以 `spellcard.gensokyou.` 开头
- [x] 5.5 config 注释已写明它是**下限**；`TALISMAN_BAR_BODY_HEIGHT` 注释已写明它只服务降级路径
- [x] 5.6 宽度余量核对：182px ÷ 9px（CJK）≈ 19 字，扣朱印仍 18 字；现有最长 4 字。不设省略号

## 6. 验证

- [x] 6.1 `gradlew build` 全绿（须走 `.\tools\gradle_task.ps1`）—— ✅ `:test` 实跑通过（7 actionable / 2 executed）
- [x] 6.2 `python tools/lang_audit.py` 退出 0 + 2.6 的故意缺键反证退出 1
- [x] 6.3 `openspec validate --strict` 通过 —— ✅ `Change is valid`
- [ ] 6.4 **部分实测**：大妖精（1 阶）确认出绿色条身 + 边框 + 符卡行（截图）。**未测**：三只同阶一致性、3 阶 vs 5 阶对照（阶 3/4 阶无 BOSS 可召唤）
- [ ] 6.5 **部分实测**：`/gs_boss spawn` 新召唤的 BOSS 符卡行立即出现（截图确认 `花符[弹幕花环]`）。**未测**：`StartTracking` 补发路径（玩家在 BOSS 已入阶段后才靠近）
- [ ] 6.6 未实测（需两只 BOSS 同场）
- [ ] 6.7 未实测（需 4 只以上同场）
- [ ] 6.8 未实测（需删掉一张 `frame_N` 后重启）
- [ ] 6.9 未实测（需切 `en_us` 语言）

## 7. 后续（不在本变更范围）

- [ ] 7.1 正式美术到位后整体替换 6 张占位贴图。规格已冻结（尺寸 / 九宫格切片 / 绘制序 / 染色层），替换时 Java 侧零改动
- [ ] 7.2 正式阶级表：随寝宫 BOSS / `phase-d-high-tier-content` 落地后重定。届时 `frame_3`/`frame_4` 大概率要重画（当前 5 阶只有 1~2 阶在用，是基础设施预付而非内容反映）
- [ ] 7.3 14 张符卡的正式英文译名
- [ ] 7.4 `bossTier()` 接入 `MonsterStatBudget.forTier(band, bossTier(), referenceTier())` 与 `BOSS_SECONDS_T1..T5`——**两者目前是死代码/死配置**，属另一条线，本变更刻意不碰

## 8. 血条判别修复（原咒符条从未生效）

> **根因**：`ServerBossEvent` 的三参构造是 `super(Mth.createInsecureUUID(), …)` —— 血条带的是
> **血条自己随机生成的 UUID**，与实体 UUID 无关。原版 `ClientboundBossEventPacket` 从服务端
> 只送 `(barUUID, name, color, overlay, progress)`，**没有任何字段能把血条连回实体**。
> 于是 `TouhouBossBarRenderer` 断言的「`getId()` 就是实体 UUID」不成立，判别恒为 false，
> 咒符条**一次都没生效过**，一直在画原版条。归档 spec「作用范围判别」那条 requirement
> 的前提本身是错的，已随之 MODIFIED。

- [x] 8.1 新增 `entity/TouhouBossBar`：自持一个 `BossEvent(entityUUID, …)`（`BossEvent` 是抽象类但 `(UUID, …)` 构造 public，取一个空具体子类），自己发 add/remove/progress/name 四种包。照抄原版语义：**值变化才发包**、受众自己记、逐玩家单发
- [x] 8.2 `AbstractTouhouBoss` / `FlandreEntity` 的 `bossBar` 字段换成 `TouhouBossBar`，构造传 `this.getUUID()`；受众管理仍走既有的 `startSeenByPlayer` / `stopSeenByPlayer`（那正是 `StartTracking` 的落点，与 3.5 的补发同一次）
- [x] 8.3 清掉两个文件里失效的 `ServerBossEvent` import
- [x] 8.4 `touhou-boss-bar` 的「作用范围判别」整条 MODIFIED：写明 MUST NOT 用 `ServerBossEvent`、血条 id == 实体 UUID、值变化才发包；补 3 个场景（血条标识即实体标识 / 显示名相同不串味 / 进度不变不发包）
- [x] 8.5 加一次性诊断日志 `[boss-bar] frame_N.png found|MISSING` —— 降级路径画出来的是**原来的朱红条**，与「没生效」在画面上完全无法区分，没这行日志只能靠猜

## 9. 贴图窗口对齐（自查发现）

> 贴图侧让 `head`/`tail` 厚度逐阶变化，**这直接移动了透明窗口**（t1 落在 1~12、t5 落在 3~11），
> 而 Java 侧按固定行区间 (2,9) 画条身 —— **每一阶都对不上**。

- [x] 9.1 `boss_bar.py` 窗口固定 `WIN_TOP=2` / `WIN_BOTTOM=9`；`head`/`tail` 改成**逐行**给，不再用横向平铺的 pattern 串
- [x] 9.2 边距**不许出现透明像素**（`F`/`D` 横向交替会让顶边渲染成虚线）—— `_frame` 里加断言 `r[0] != '.'`，破洞在生成期就报错，不留到屏幕上
- [x] 9.3 一次性校验脚本比对「贴图实际窗口」与「Java 常量」，5 阶全 OK 才落 assets
