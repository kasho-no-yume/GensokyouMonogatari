## 1. 客户端祭品台偏移的读取通路

- [x] 1.1 在 `ClientRitualData` 侧新增只读辅助：按 `patternId` + `tier` 取出该层中调色板谓词为 `TAG gensokyou:ritual_pedestals` 的方块偏移列表，返回顺序须与服务器 `RitualPedestals.positions()` 一致（规范序 y,z,x）。
- [x] 1.2 校验顺序一致性：为「客户端推导顺序 == `RitualPedestals.positions()` 顺序」补一条离线单测（可用 `RitualPatternLoader.parseForEdit` 重建 pattern 后比对偏移序列，参照 `BarrierOfferingSlotTest` 的离线做法）。
- [x] 1.3 列表为空（缓存未就绪 / 尚未成型）时返回空列表而非抛异常，调用方据此整段跳过绘制。

## 2. 焦点核

- [x] 2.1 `GensokyouConfig` 新增 `ritualFx` 组内的焦点核键：半径、相对核心顶面高度、亮度下限/上限、呼吸幅度、呼吸周期。
- [x] 2.2 新增（或复用）一个**常规 alpha 混合 + 自发光**的 RenderType 承载不透明球；MUST NOT 用 `SpiritOrbRenderTypes.ORB`。
- [x] 2.3 在 `renderOrb` 中于 `core.getY() + 2.5`、水平 `(0.5, ·, 0.5)` 绘制焦点核：半径固定、CPU 侧呼吸缩放、顶点色写绿族、光照 `FULL_BRIGHT`、alpha 由「有效台数 / 总台数」插值乘以下限/上限。
- [x] 2.4 有效台数为 0 时不绘制焦点核。
- [x] 2.5 ~~焦点核贴图来源：优先复用现有实心/柔边球类贴图~~ → **实现期改判：不需要新贴图，也不复用旧贴图。** 焦点核是**程序化着色**：新增 `spirit_core` 着色器（沿用 `spirit_orb` 的单位球顶点格式），密度曲线为「体内恒为 `Density`（不透明）+ fresnel 只叠加边缘高光」，配常规 **alpha 混合**的 `SpiritOrbRenderTypes.CORE`。理由：加法混合下"不透明"不可能实现；而 `fx/*` 与 `item/*` 里没有居中的实心球贴图，`spirit_mist` 是为平铺噪声准备的（接缝可见）。零新贴图、零新几何。

## 3. 逐台激光

- [x] 3.1 新增 `WeakHashMap<corePos, float[]>` 的逐台包络缓存（结构参照 `boltEnvelopes`），长度随当前阶台数变化时重建并保留已有分量；渐变长度用 `FX_RAMP_TICKS`。
- [x] 3.2 合格判定：`corePos` 处的 `RitualPedestalBlockEntity.held`，判据 `instanceof SpiritCoreItem && core.tier() <= state.tier()`，注释中互相指认 `BafangGuiyuanBehavior:231-233`。
- [x] 3.3 每台一条青白激光（`#C8F0FF` 拟值，提为具名常量），自台面中心上方约 1.1 格连到焦点核中心；复用 `bolt_core.png` / `bolt_glow.png`，零新贴图。
- [x] 3.4 **按 RenderType 分趟提交**：先取光晕缓冲写完全部光晕段，再取亮芯缓冲写完全部亮芯段。禁止先把多个 consumer 取出再交叉写（`MultiBufferSource` 别名规则，参照 `RitualCoreRenderer:266-268`）。

## 4. 灵气场改造

- [x] 4.1 `spirit_orb.fsh`：fresnel 指数 2.2 → 约 1.2；`shell` 增加低密度内填充项（如 `0.16 + ...`），使体内非全透亦非实心。
- [x] 4.2 `RitualCoreRenderer#renderOrb` 的顶点 alpha 220 → 更低（与焦点核共存时必须退到背景），提为具名常量。
- [x] 4.3 `FX_ORB_*` 现有半径/悬高键**不改数值**；仅调 shader 与 alpha，实机看过再决定是否动尺寸。

## 5. 验证

- [ ] 5.1 2/3/4/5 阶各建一座：台数分别为 4/8/16/24，激光条数随之变化且坐标与台位一致。
- [ ] 5.2 放核 / 取核 / 换非灵力核 / 放超阶核，四种操作下激光与亮度的响应正确且无瞬切。
- [ ] 5.3 升级仪式阶级：台数与激光集合即时变化，客户端无报错、无残留旧台激光。
- [ ] 5.4 24 台全满：确认无 BufferSource 崩溃（"Not building!"）、帧率可接受。
- [ ] 5.5 灵气场：从 10/20/40 格距离观察，外缘无硬轮廓；体内非全透。
- [ ] 5.6 用网络抓包或 `ClientRitualData` 计数确认本变更未引入任何新包。

## 6. 收尾

- [x] 6.1 复查 `RitualCoreRenderer#getRenderBoundingBox` 在 `KIND_BAFANG` 下的 16 格半径足以覆盖焦点核与最外侧台位（最外 |x| = 12），无需改动则明确记录。

---

## 7. 归档时的验证状态（2026-09-27）

**自动化验证**：`./tools/gradle_task.ps1 test` 306 项全绿（含 `BafangPedestalOffsetsTest` 8 条
客户端台位推导一致性断言），`openspec validate bafang-pedestal-beams --strict` 通过。

**实机验证：已完成。** 用户在归档时明确告知观感验收已完成。
§5 的 6 项（阶级台数与激光条数、四种核心操作响应、升级即时变化、24 台满载无
`BufferSource "Not building!"` 崩溃、灵气场外缘无硬轮廓、零新增网络包）均属**实机观感验收**，
无遗留代码工作。
