## Why

普通球状弹幕的视觉参数目前**全部是编译期常量**：

```java
// SphereDanmakuRenderer.java
private static final float CORE_SCALE  = 0.55F;
private static final int   CORE_ALPHA  = 235;
private static final float CORE_OFFSET = AbstractDanmakuRenderer.GLOW_OFFSET * 2.0F;

// AbstractDanmakuRenderer.java
protected static final float GLOW_SCALE = 1.35F;
protected static final int   GLOW_ALPHA = 110;
private static final ResourceLocation TEXTURE = ...;   // SphereDanmakuRenderer 里写死
```

于是三条需求都做不到：换贴图、换模型、调核心/发光半径。

更深的问题是**视觉尺寸与碰撞尺寸被焊在一起**：

```java
// SphereDanmaku.java:141-158
public void setSize(float size) {
    this.entityData.set(DATA_SIZE, size);
    this.refreshDimensions();          // 碰撞箱跟着走
}
@Override public EntityDimensions getDimensions(Pose pose) {
    float size = Math.max(0.05F, getSize());
    return EntityDimensions.scalable(size, size);
}
```

这个「碰撞箱=视觉直径」的不变量是 `boss-dev-design` §10 第 7 条踩坑后修好的（此前 `DATA_SIZE` 只用于渲染、实体尺寸写死 0.4×0.4，导致 BOSS 的大慢球看起来穿过玩家却没伤害）。**一旦把球弹换成模型，模型的视觉尺寸不等于四边形的尺寸，这个刚修好的不变量立刻被打破。** 所以换贴图/模型 MUST NOT 只做渲染层替换，MUST 同时把两个尺寸解耦。

## What Changes

**① 引入 `DanmakuVisualProfile`（common code 静态表）**

```java
record DanmakuVisualProfile(
    ResourceLocation texture,
    float visualScale,    // 视觉相对 getSize()
    float hitboxScale,    // 碰撞相对 getSize()  ← 与 visualScale 解耦
    float coreScale,  int  coreAlpha,   // 现 0.55 / 235；coreAlpha=0 即关闭亮核层
    float glowScale,  int  glowAlpha,   // 现 1.35 / 110
    DanmakuGeometry geometry,           // QUAD | STAR_PRISM
    ColorMode colorMode,                // FIXED | CYCLE_HUE | PULSE
    int  colorCycleTicks,
    int  hiddenAlpha,                   // 隐藏态 alpha；默认 127
    float tumbleAmpDeg, int tumblePeriodTicks   // 现 0/0；STAR_PRISM 用
) {}
```

- 表 MUST 放在 common code：`getDimensions` 两端都要跑（客户端的 `getHitResultOnMoveVector` 与 `checkStationaryEntityHit` 都用 AABB），放客户端注册表服务端读不到。
- **每颗弹只同步一个 int（profileId）**，不传贴图/尺寸/alpha。
- 资源包换贴图 MUST NOT 影响玩法（只有外观差异）。

**② 隐藏态（相位隐藏）**

需求：缓慢飞行的高密度弹幕墙以 2 秒为间隔间歇变为隐藏态；隐藏期 alpha 50%、**不与任何实体碰撞**、**方块碰撞仍生效**、**不引起弹幕销毁**。

- 碰撞侧：`canHitEntity` 在隐藏期返回 false。则 `getHitResultOnMoveVector` 返回 `MISS` → 不进 `onHit` → 不 discard；方块分支不受影响。**无需额外状态机。**
- 隐藏态的 alpha 取自视觉档案的 `hiddenAlpha` 字段（MUST NOT 硬编码 50%），默认档案取 50%。
- **隐藏态是唯一免除实体碰撞的运动状态。** 悬停（定住）SHALL 明确保持判伤，走 `checkStationaryEntityHit` 的碰撞箱相交判定。溜め维持其独立语义（埋设期不接触判伤，仅进入触发半径时结算），MUST NOT 因「其它状态均判伤」而改为接触判伤。
- 渲染侧：**MUST 切换 `RenderType` 而非只乘 alpha，且 MUST 同时切换本体层与发光层。** 球弹三层里有两个写深度：本体层是 `translucentDepth`（alpha 混合 + **写深度**），发光层是 `additiveSolid`（加法混合 + **写深度**）。50% alpha 照样写深度，会把身后的弹挖出方洞——高密度弹幕墙隐藏时将呈现为一片黑方块。隐藏态时两层 MUST 改用不写深度的既有类型（`translucent` 与 `additiveGlow`）。亮核层本就不写深度（`additiveGlow`），无需改。
- 周期性 MUST 由 `tickCount` 推导，MUST NOT 由服务端翻转同步位：
  ```
  hidden(t) = ((t + phaseOffset) mod period) < duty · period
  ```
  服务端翻位 = 每弹每秒 2 个包 × 200 颗 = 400 包/秒，恰好毁掉本架构存在的理由。周期/占空比/相位三项对全批相同，只需一份。
- spec MUST 写死：相位隐藏是**纯视觉 + 服务端命中掩码**，MUST NOT 引入任何双端必须一致的判定。

**③ 五角星柱模型（验证件）**

为使「换模型」路径可实测，提供一个程序化五角星柱作为第一个 `DanmakuGeometry.STAR_PRISM`：

```
外接圆半径 R = 视觉半径
内半径   r = R·(3−√5)/2 ≈ 0.38197R        正五角星内外比
总厚度   T = R/2（半厚 h = R/4）            需求指定
10 轮廓点，间隔 36°，凸/凹交替，θ_k = 90° − 72°k
前面盖 10 三角（从中心扇出——星形多边形对中心可见，扇形三角化合法）
后面盖 10 三角（绕序反向）
侧面 10 四边形
共 30 quad = 120 顶点/层
```

**朝向：偏航锁相机 + 俯仰摆动，MUST NOT 完全 billboard。**

```
yaw   = 相机偏航                                   轮廓恒可读
pitch = tumbleAmpDeg · sin(2π·tickCount/tumblePeriod)   摆到极值时厚度成为可见信息
```

完全 billboard 会让厚度 0.5R 永远不可见，该模型即失去存在意义。摆动幅度取 ~50° 而非 180°：转到 90° 时五角星投影退化成一条线，玩家会读成「弹消失了」。摆动式（硬币旋转）而非整周翻滚即为此。

**变色**：三种模式全部由 `tickCount` 推导，零同步。`FIXED` 用 `entity.getColor()`（兼容既有色盘轨道）；`CYCLE_HUE` / `PULSE` 用 `HSBtoRGB`，饱和度/亮度沿用 `SphereDanmaku.randomColor()` 的 0.85/1.0 保持同一套色彩语言。

**碰撞箱 `hitboxScale`**：这不是一个能「估」出来的数。两个判据给出不同答案——

```
等面积当量   五角星面积 = 1.1225 R²，外接圆 = πR² = 3.1416 R²
             面积比 0.357  ⇒  等效半径 0.598 R  ⇒  hitboxScale ≈ 0.60

方向性范围   正对外顶点方向：弹的实体延伸到 1.00 R
             正对凹角方向：只延伸到 0.382 R
             真实值随角度在 0.38~1.00 R 之间变化，恒定碰撞箱只能取区间内一点
```

等面积当量（0.60）会漏判「贴着凹角边缘」——对得很近却没打中；取上界（1.0）会复现「看着没碰到却掉血」。**这应当是设计判断而非公式导出**，且 SHOULD 可调以便实机校手感。故 spec 只写死「碰撞缩放 MUST 小于视觉缩放」，不钉死数值。

**成本方向与球弹相反**：球弹 4 顶点/层但覆盖满方框 100%；星柱 120 顶点/层但覆盖星形 ~40%。顶点重、填充轻——对密集弹幕墙有利。

**④ profile 是唯一样式入口**

新增弹幕样式 MUST 通过新增 `DanmakuVisualProfile` 实现，MUST NOT 扩展 `AbstractDanmakuRenderer` 的编译期常量。整套渲染 MUST 收敛到既有 `renderShape(entity, poseStack, consumer, r, g, b, a, light)` 契约——五角星柱即该契约下的一个新方法，换色/换 alpha/发光层/亮核层/深度处理全部白拿。

## Impact

- **改动面**：`SphereDanmaku`（+`DATA_VISUAL` int accessor）、`SphereDanmakuRenderer`（改为按 profile 分派几何与分层参数）、新增 `DanmakuVisualProfile` / `DanmakuGeometry` / `DanmakuColorMode` 三个 common code 类型、可能新增 `StarPrismDanmakuRenderer` 或在球渲染器内分派。
- **依赖**：隐藏态依赖 `danmaku-budget-and-sync-correctness` 修好 `lerpTo`——否则隐藏态的相位与视觉位置会带着 150ms 滞后，读起来是错的。
- **兼容**：默认 profile 的 `visualScale = hitboxScale = 1.0`、`coreScale = 0.55`、`glowScale = 1.35`，与现值逐位相同 → 现有四只 BOSS 的符卡表无需改动，观感 MUST NOT 有任何变化。
- **风险**：
  - `hitboxScale` 一旦可调，「碰撞=视觉」的不变量就从「恒等」变成「默认等于」。MUST 保留默认 profile 下二者相等的断言测试。
  - 隐藏态的 `RenderType` 切换会改变批次归属：球弹三层当前分属三个 `RenderType`（`SphereDanmakuRenderer.java:64-73` 的注释解释了为什么亮核必须用不写深度的类型）。隐藏态只用两层，MUST 保持「同批至多一层写深度且是最远的一层」这条约束。

## Open Questions

1. 「半径」取相对倍率还是绝对格数？倾向倍率（东方的发光本就随弹走），但若大慢球的光晕过糊，是否需要加一条绝对上限？
2. `STAR_PRISM` 的俯仰摆动默认周期 40 tick 是否合适，还是要按弹径分档（弹越大摆得越慢更稳）？
