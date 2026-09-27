## Context

眼形几何的 1× 基准（`SukimaPortalRenderer:52-66`）：

| 常量 | 值 | 含义 |
|---|---|---|
| `HALF_W` | 0.5 | 透镜半宽（总宽 1 格） |
| `UPPER_LID` | 0.85 | 上盖相对中线高度 |
| `LOWER_LID` | 1.15 | 下弧相对中线高度（负向） |
| `CENTER_Y` | 1.15 | 眼中线世界高度（下弧最低点贴方块底，总高 2 格） |
| `V_TIP` | 0.425 | 描边贴图（`sukima.png`，128×256）中眼尖所在的行比例 |

内景是**程序化几何**（`renderVoidInterior` + `STRIPS = 16` + `sqrtHalf(t) = √(1−t²)` 剖面），`up(t) = up·√(1−t²)`、`lo(t) = −lo·√(1−t²)`：因此形状是**尖端在左右两侧（x = ±halfW 处高度为 0）、中线处满高**的竖立杏仁。

眼睑是**单张描边贴图整铺**（`sukima.png`，1:2 竖向），沿 `V_TIP` 切上下两片、各自沿中线方向压扁（`renderEyelids:286-319`）。

现状的 `s` 同时驱动 `up`、`lo`（纵向缩放），`halfW` 恒定。`design.md` D5.8 记录了当初为何这么选："开眼是中间的虚空由一条横缝纵向裂开，而非整群眼形由小长大"——**本变更推翻该决策**，因为它与这只竖立杏仁的实际长轴矛盾。

## Goals / Non-Goals

**Goals:**

- 以**竖直长轴**为对称轴向左右两侧张开，闭眼态为一条 2 格高的竖直细缝。
- 无过冲回弹。
- 眼睑与内景在任意开合进度下**不可能脱钩**。

**Non-Goals:**

- 不改 `sukima_portal` 着色器（POSITION-only + 裁剪空间投影采样，横向缩放自动覆盖）。
- 不改眼形贴图本身（`sukima.png` 不重新生成）。
- 不改 10° 倾角与 billboard 姿态。
- 不做 3D 铰接开合。

## Decisions

### D1 — 内景：只缩横向

```
halfW = HALF_W * scale * s        ← 唯一随 s 变的量
up    = UPPER_LID * scale         ← 恒定
lo    = LOWER_LID * scale         ← 恒定
```

`s → 0` 时 `halfW → 0`，形状退化为一条**贯穿全高 2 格、零宽度**的竖缝；`s = 1` 时恢复为原杏仁。`sqrtHalf` 剖面原样保留（它本来就是 x 的函数）。`STRIPS = 16` 条带的横坐标随之压缩，条带数与 16px 描边贴图精度仍匹配。

### D2 — 眼睑：沿 U 切左右两片

```
左片：x ∈ [−W, 0]，取贴图 u ∈ [0, 0.5]
右片：x ∈ [0, +W]，取贴图 u ∈ [0.5, 1]
其中 W = HALF_W * scale * s
两片纵向均占全高：y ∈ [−LOWER_LID*scale, +UPPER_LID*scale]
纵向锚点：v = V_TIP ↔ y = 0（眼中线）
```

纵向不能简单按 `[−H, +H]` 对称居中，否则贴图里的眼中线（`v = 0.425`）会被映射到几何的 `v = 0.5`，与内景的零高度点（`y = 0`）错开 0.05 格。**必须**用 `半高 = (up+lo)/2·scale` + `y 平移 = (up−lo)/2·scale` 的方式把纵向锚在 `V_TIP` 上——这正是现有 `renderEyelids` 对上下两片所用的手法，照搬即可。

每片调用一次 `SukimaPortalQuads.drawUvRange(halfW = W/2, halfH = (up+lo)/2·scale, u0, v0=0, u1, v1=1)`，并先做 `x 平移 = ∓W/2`。`SukimaPortalQuads` 签名与语义不变（它本就为"取贴图某一段"而设计）。

### D3 — 结构保证而非常量对齐

延续 D5.10 立下的做法：让 `lidPieceHalfW(extent, scale)` **委托**给 `voidHalfW(extent, scale)` 并取半，使 2:1 关系成为结构上不可能违反的事实。纵向因内景与眼睑共用 `UPPER_LID`/`LOWER_LID` 同一组常量、且都不再随 `s` 变化，天然相等，无需再套一层委托。

**旧的两个纵向辅助（`voidUpHalf` / `lidUpQuadHalf`）因此失去存在意义**，MUST 被删除而非留作死代码——它们的存在本身就在宣告"纵向随 s 变化"这条已废止的语义。

### D4 — 不做 3D 铰接

"双开门"若理解为绕竖直长轴的真 3D 旋转，则：整只眼（含内景）作为一个刚体绕长轴转 θ，其正面投影宽度恰为 `cos θ`——与直接做 `halfW × cos θ` **在视觉上完全等价**，即 3D 版本零增益。

更糟的是它会引入真实的耦合风险：`sukima_portal` 着色器是**裁剪空间投影采样**（效果锚定屏幕而非几何表面），一旦几何绕轴旋转，投影锚点与眼框的对应关系就要重新验证；而 D5.10 记录的"内景溢出眼纲 / 眼纲比内景宽 → 读作开闭轴歪了"正是这类脱钩的典型症状，已经栽过一次。

结论：**2D 横向位移**。"双开门"的观感由"两片从中间向两侧分开、中间留一条竖缝"这一形态本身提供，不需要 3D。

### D5 — 去掉的过冲

`easeOutBack` 及其 `SUKIMA_PORTAL_LID_TRAVEL` 配置键一并退役（该键只用于缩放过冲幅度）。曲线改为 `easeOutCubic`：起步快、末段稳，无过冲，终值精确 1.0。

`easeOutCubic` 目前已是 `SukimaPortalRenderer` 的一个**零调用**公开方法（`easeOutBack` 旁的残留），本变更直接启用它并删除 `easeOutBack`。

**已知副作用**：`lensExtent` 曾在测试中允许返回 > 1，`renderVoidInterior` 里的 `Math.min(1.0F, Math.max(0.0F, extent))` 钳位随之变为恒等，可保留（防御性）或删除。保留成本为零。

## Risks / Trade-offs

- **两个既有单测必须重写**，它们当前锁死的正是本变更要推翻的纵向语义：`BarrierOfferingSlotTest:256-269`（`voidUpHalf == 2 × lidUpQuadHalf`）与 `:275-287`（过冲峰值 < 1.35）。重写时 MUST 保持"结构保证"的精神：新断言锁的是 `lidPieceHalfW == 0.5 × voidHalfW` 这一委托关系，以及曲线单调、终值恰为 1、`travel` 参数已不再存在。
- **配置键退役是破坏性的**：已在旧配置文件里写了 `sukimaPortalLidTravel` 的玩家，升级后会看到该键被 Forge 记为未知项。无数据风险（本键不参与任何持久化数据）。
- **`V_TIP` 的语义从"切分位置"变成"纵向锚点"**，两处用途仍在同一处计算，务必不要因为不再用于切分就顺手删掉。
- 闭眼态（`s → 0`）下内景退化为零宽度的退化 quad（`halfW ≤ 1e-4` 时提前返回），眼睑两片退化为零宽度的两片。现有 `renderEyelids` / `renderVoidInterior` 的 `s <= 1.0E-4F` 早返回分支**继续有效**，无需新增。
