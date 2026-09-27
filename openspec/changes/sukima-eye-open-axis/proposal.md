## Why

隙间门的开合动画开错了轴。

现状是**竖向开合**：内景的 `up`/`lo` 随开合系数 `s` 缩放而横向恒为满宽，眼睑贴图沿 `V_TIP` 切成上下两片各自压扁。于是闭眼态是一条**1 格宽的水平细缝**，开眼是"从上往下裂开"。但这只眼的实际形态是 1 格宽 × 2 格高、尖端在左右两侧的**竖立杏仁**——它的**长轴是竖直的那条**。用户要的是以长轴为对称轴、向**两侧**张开，也就是：闭眼态是一条 **2 格高的竖直细缝**，开眼是"从中间横向裂开"。

同时，现用 `easeOutBack` 的过冲回弹被判定观感很差，本变更一并去掉。

## What Changes

- **开合轴翻转**：内景的 `halfW` 改为乘开合系数 `s`，`up`/`lo`（0.85 / 1.15 格）**恒定不缩放**。闭眼（`s=0`）为一条贯穿全高的竖直细缝。
- **眼睑切分轴翻转**：描边贴图改沿 `U=0.5` 切成**左右两片**，各占半个杏仁（各自的外侧尖端在 `x=±W`，内侧的直切口边在 `x=0`），两片沿水平方向向中线收拢 / 复原。两片的纵向跨度恒为全高（上盖 0.85、下弧 1.15），纵向锚点仍为眼中线。
- **去���冲**：开合曲线由 `easeOutBack` 改为 `easeOutCubic`，**无过冲**，终值精确为 1.0。
- **结构上不可能脱钩**：单片眼睑的半宽由内景半宽**委托**取半（`lidPieceHalfW = 0.5 × voidHalfW`），延续既有"2:1 关系由结构保证而非两处常量同时正确"的做法；纵向因两侧共用同一组常量而天然相等。
- 单测改写：`voidUpHalf == 2 × lidUpQuadHalf` 的纵向恒等式改为委托式的横向关系；"峰值 < 1.35" 的过冲断言改为"单调、终值恰为 1"。

**不做**：不做 3D 铰接旋转（见 design D4）。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `sukima-portal-rendering`: 「隙间门开合动画」条款的对称轴由"眼中线的上下分离"改为"沿长轴（竖直）的左右分离"，并移除过冲回弹要求。

## Impact

- `SukimaPortalRenderer`：`openProgress01` / `lensExtent` / `voidUpHalf` / `lidUpQuadHalf` / `renderVoidInterior` / `renderEyelids` 与 `easeOutBack`。
- `SukimaPortalQuads`：`drawUvRange` 已有 UV 子区间能力，**签名与行为不变**（调用方改传 u 区间即可）。
- `GensokyouConfig`：`ritualFx.sukimaPortalLidTravel`（过冲幅度）随过冲一起去掉；`sukimaPortalOpenTicks` 语义与数值不变。
- `BarrierOfferingSlotTest`：两条锁死纵向缩放的断言重写。
- `docs/barrier-shatter-fx-postmortem.md` 无需改动（本变更为正向修复）。
