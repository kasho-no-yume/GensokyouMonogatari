## 1. 曲线

- [x] 1.1 `SukimaPortalRenderer`：新增/启用无过冲的开合曲线（复用既有的零调用 `easeOutCubic`），删除 `easeOutBack` 及其 `travel` 参数。
- [x] 1.2 `lensExtent` 改为单参数（`t`），或直接内联为对 `easeOutCubic` 的调用。
- [x] 1.3 `GensokyouConfig`：删除 `SUKIMA_PORTAL_LID_TRAVEL` 键（`ritualFx.sukimaPortalLidTravel`，默认 0.35）。确认无其他消费者。
- [x] 1.4 `SukimaPortalRenderer` 删除 `voidUpHalf` / `lidUpQuadHalf`（纵向辅助已无存在意义，保留即等于宣告已废止的语义）。

## 2. 内景

- [x] 2.1 `renderVoidInterior`：`halfW` 改为 `HALF_W * scale * s`；`up` / `lo` 去掉 `* s`。
- [x] 2.2 确认 `sqrtHalf` 剖面、`STRIPS = 16`、正/背双面绕序、`voidPortal` 渲染类型与 POSITION-only 顶点写出全部不变。
- [x] 2.3 确认 `s → 0` 时命中既有 `halfW <= 1e-4` 早返回，不产生退化几何。

## 3. 眼睑

- [x] 3.1 `renderEyelids` 切分轴由 V 改为 U：以 `U = 0.5` 切左右两片，各取 `u ∈ [0, 0.5]` / `[0.5, 1]`，`v ∈ [0, 1]` 全幅。
- [x] 3.2 每片的横向几何：半宽 `W/2`（`W = HALF_W * scale * s`）+ `x` 平移 `∓W/2`；纵向半高 `(up+lo)/2 * scale` + `y` 平移 `(up−lo)/2 * scale`，确保 `v = V_TIP` 恰好落在 `y = 0`。
- [x] 3.3 `V_TIP` 的注释更新为"眼中线在贴图中的行比例（纵向锚点）"，避免后人因其不再用于切分而删除。
- [x] 3.4 现有 `z = OUTLINE_OFFSET (0.001)` 防 z-fighting 的正偏移**方向不变**（`+Z` 朝相机已实机生效），并确认本变更未把 z 偏移与新的 x 平移混在同一处。

## 4. 结构保证

- [x] 4.1 新增 `voidHalfW(extent, scale)` 与 `lidPieceHalfW(extent, scale)`，后者**委托**前者取半；两处调用点均改用它们。
- [x] 4.2 确认纵向不再需要任何派生关系（内景与眼睑共用 `UPPER_LID`/`LOWER_LID`）。

## 5. 单测重写

- [x] 5.1 重写 `BarrierOfferingSlotTest:256-269`：由「`voidUpHalf == 2 × lidUpQuadHalf`」改为「`lidPieceHalfW == 0.5 × voidHalfW`」在多组 `t ∈ [0,1]` 与多组 `scale` 下的委托关系断言。
- [x] 5.2 重写 `BarrierOfferingSlotTest:275-287`：由「过冲峰值 < 1.35」改为「曲线单调不减、终值精确为 1.0、全程最大值恰为 1.0」。
- [x] 5.3 补一条：任意 `t` 下纵向跨度（`up` + `lo`）恒等于 `scale × 2.0`，不随 `t` 变化。
- [x] 5.4 注意 `BarrierOfferingSlotTest` 内不得读配置（未加载会抛 "Cannot get config value before config is loaded"），故被测函数保持纯函数形态。

## 6. 验证

- [ ] 6.1 默认尺寸（scale 1.0）：闭眼为 2 格高竖缝；半开为竖立杏仁；全开与变更前逐项一致。
- [ ] 6.2 放大尺寸（scale 2.0）：同上，10° 倾角仍在，眼形不与最近的四根界柱相交。
- [ ] 6.3 睁眼与闭眼全过程：两片确实向左右分离，纵向跨度全程不变；无过冲、无回弹。
- [ ] 6.4 迟到/重进世界的玩家读到的开合进度正确（`openProgress01` 仍取自 `closingTicks` 的既有逻辑未被破坏）。
- [ ] 6.5 内景虚空不溢出眼纲、也不小于眼纲（任意中间进度抽查 5 帧）。

---

## 7. 归档时的验证状态（2026-09-27）

**自动化验证**：`./tools/gradle_task.ps1 test` 306 项全绿（含重写后的
`BarrierOfferingSlotTest`：两片眼睑与内景虚空的委托关系、开合曲线单调不减且终值精确为 1.0、
纵向跨度恒为 `scale × 2.0`），`openspec validate sukima-eye-open-axis --strict` 通过。

**实机验证：已完成。** 用户在归档时明确告知观感验收已完成。
§6 的 5 项（scale 1.0 / 2.0 的眼形、睁闭全过程无过冲回弹、迟到与重进世界的开合进度、
内景虚空与眼纲的贴合）均属**实机观感验收**，无遗留代码工作。
