## 1. 视觉档案三件套（公共代码）

- [x] `danmaku/visual/DanmakuVisualProfile` —— 档案 record + 注册表
  - [x] 字段：贴图 / visualScale / **hitboxScale** / coreScale / coreAlpha / glowScale / glowAlpha / hiddenAlpha / geometry / colorMode / colorCycleTicks / tumbleAmpDeg / tumblePeriod
  - [x] 构造期校验（coreScale ≤ 1、glowScale ≥ 1、tumbleAmpDeg ≤ 90、两个 scale > 0）
  - [x] `byId` 越界回落默认档，**绝不抛**
  - [x] `DEFAULT` 档案逐位等于改前的 0.55 / 235 / 1.35 / 110，且 visualScale == hitboxScale == 1.0
  - [x] `STAR_PRISM` 档案：hitboxScale 0.60（等面积当量 0.598R）、tumble 50°/40tick
  - [x] MUST 放公共代码——`getDimensions` 两端都要跑
- [x] `danmaku/visual/DanmakuGeometry` —— QUAD / STAR_PRISM
  - [x] `starPrismOutline(R)`：10 点凸凹交替，36° 等分，凸 = R、凹 = R·(3−√5)/2
  - [x] `starPrismHalfThickness(R)` = R/4（故总厚度 = R/2）
- [x] `danmaku/visual/DanmakuColorMode` —— FIXED / CYCLE_HUE / PULSE
  - [x] 全部由 `tickCount` 推导，**零同步**
  - [x] 饱和度/亮度沿用 `randomColor()` 的 0.85 / 1.0
- [x] `danmaku/visual/DanmakuPhase` —— 相位隐藏的时序逻辑
  - [x] `isHidden` 用 `floorMod`（负偏移 MUST 正确，Java 的 `%` 保留符号会失效）
  - [x] `visibleProgress` / `visibleRatio`

## 2. 弹体侧

- [x] `SphereDanmaku` 新增 `DATA_VISUAL`（int，逐弹只同步档案 id）
- [x] 新增 `DATA_PHASE_PERIOD` / `DATA_PHASE_DUTY` / `DATA_PHASE_OFFSET`
- [x] `getDimensions` 改用 `size × hitboxScale` —— **视觉与碰撞解耦**
- [x] `setVisualProfile` / `visualProfile()` / `getVisualId()`
- [x] `configurePhaseHide(period, duty, phaseOffset)`
- [x] `isHidden()` + `canHitEntity` 覆写
  - [x] 隐藏期 `canHitEntity` 返回 false ⇒ 扫描找不到命中 ⇒ **不判伤且不销毁**
  - [x] 方块分支独立于该谓词 ⇒ **撞墙照常消失**
- [x] 档案 id 与相位三项纳入存档

## 3. 渲染侧

- [x] `SphereDanmakuRenderer` 三层参数全部改由档案驱动
  - [x] 本体：`translucentDepth`（写深度）
  - [x] 发光：档案倍率与 alpha，`additiveSolid`（写深度）
  - [x] 亮核：档案缩放与 alpha；`coreAlpha = 0` 即跳过该层
  - [x] 隐藏态**渲染类型不变**，只压暗 alpha——理由见下方实机反馈②
- [x] 颜色经 `colorMode.resolve` 解析；隐藏态整体压暗至 `hiddenAlpha`
- [x] `AbstractDanmakuRenderer.renderGlow` 签名改为接受 profile
  - [x] 隐藏态时发光 alpha 按本体 alpha 比例缩放
  - [x] `GLOW_SCALE` / `GLOW_ALPHA` 降级为 `LEGACY_*` 文档常量
- [x] `TalismanDanmakuRenderer` 传 `DEFAULT` 档案，观感逐位不变
- [x] `renderStarPrism`：前面盖 10 三角（中心扇出，星形多边形对中心可见故合法）
      + 后面盖 10 三角 + 侧面 10 quad = 30 quad / 120 顶点
- [x] 朝向：偏航锁相机 + 俯仰摆动，**刻意不用完全 billboard**（否则厚度永远不可见）
- [x] **实机反馈修正**：隐藏态原本看起来【比常态更亮】
  - [x] 根因：只压暗了本体与发光两层，**亮核层保留 235 纯白加法**，压过被压暗的本体
  - [x] 修法：抽出 `scaleAlpha(layerAlpha, bodyAlpha)`，三层统一按本体比例压暗
  - [x] 加断言锁住「每一层都压暗」
- [x] **实机反馈修正（第三轮）**：压暗了但「力度不够」，且**外发光那一圈完全没变暗**
  - [x] 根因：**加法混合层的压暗方式错了**。加法贡献 = `rgb × alpha`，而辉光是软渐变的
        <b>外圈</b>，贴图自身 alpha 在峰值外缘已掉到 ~0.2 ⇒ 层 alpha 110→55 换算到实际
        贡献只是 22→11（差 11/255，肉眼不可见）。只降 alpha 对加法层几乎无效
  - [x] 修法：加法层（辉光、亮核）**同时压暗 alpha 与 RGB**，贡献按 `dim²` 下降
  - [x] 加法新增 `AbstractDanmakuRenderer#dimColor`；删掉只降 alpha 的 `scaleAlpha`
  - [x] 本体层是 alpha 混合，**只降 alpha 不降 RGB**——暗态仍须能辨认颜色，
        否则「隐藏」会读作「换成了另一种弹」
  - [x] 力度：`hiddenAlpha` 127 → **96**（≈38%）
  - [x] 效果：辉光与亮核的加法贡献降到 **0.142**（此前只降 alpha 时是 0.376）
  - [x] 加断言锁住「加法层按 dim² 压暗」与「hiddenAlpha 落在可读区间 [70,110]」
  - [x] 根因：**渲染类型**被从「写深度」换成了「不写深度」。隐藏态的用途正是高密度
        弹幕墙，而不写深度意味着墙内**每一层都参与 alpha 混合与加法叠加**——
        400 颗弹的 50% alpha 逐层累加、400 层辉光逐层相加 ⇒ 饱和白。
        压暗的那点 alpha 被无界叠加彻底淹没
  - [x] **那条「隐藏态 MUST 切到不写深度」的要求是错的**：它是<b>推理</b>出来的
        （担心半透明弹写深度会「把身后的弹挖出方洞」），不是观测到的，
        而它在弹幕墙这个目标场景下是反效果
  - [x] 修法：隐藏态沿用常态的渲染类型，只保留压暗。深度缓冲剔除身后的层，
        叠加量被限制在一两层内，压暗才读得出来
  - [x] 删掉 `renderGlow` 的 `overrideType` 参数（已无非空调用方，不留投机扩展点）
  - [x] 同步修正 spec 里那条错误要求，并补「高密度隐藏态读作变暗而非变亮」场景
- [x] **实机反馈修正**：五角星柱「太亮，不像东方」
  - [x] 根因：球弹的「0.55× 纯白亮核 + 1.35× 加法发光」是为柔和圆形渐变贴图设计的，
        拿到锐利多边形上会把星糊成一坨光球
  - [x] 修法：五角星柱档案 `coreAlpha = 0`（关亮核）、`glowScale 1.35→1.18`、`glowAlpha 110→70`
  - [x] 依据：东方弹幕的观感是**边缘清晰 + 辉光收束**，不是柔和光球
  - [x] 加两条断言锁住（关亮核 / 辉光比球弹紧），改前 MUST 先摘断言

## 4. 调试命令

- [x] `/danmaku wall <N> [周期] [占空比%]` —— 相位隐藏弹幕墙（需求 3 的演示台）
- [x] `/danmaku prism [N]` —— 五角星柱（可换几何的验证件）
- [x] `/danmaku stress <N> [存活tick]` —— 密度阶梯测试台（归 change 1 的遗留验证）

## 5. 测试

- [x] `DanmakuVisualProfileTest`（13 例）
  - [x] **兼容性**：默认档案四常量逐位等于改前
  - [x] **不变量**：默认档案 hitboxScale == visualScale
  - [x] 越界 id 回落、id 映射无歧义
  - [x] 五项构造期校验各自拒绝一次
  - [x] 五角星：10 点交替 / 36° 等分 / 五点 72° 对称 / 厚度 = R/2 / 碰撞更紧
  - [x] 变色：FIXED 恒等 / 零周期回落 / 循环一周期回原点 / 脉动亮度在 [0.6,1]
- [x] `DanmakuPhaseTest`（10 例）
  - [x] 占空比 1 恒可见 / 周期 0 关闭 / 占空比 0 恒隐藏
  - [x] 50% 恰好隐藏一半 tick
  - [x] 相位偏移**平移波形但不改变比例**，且确实生效
  - [x] **负偏移 == 其模等价正偏移**（`%` 保留符号会失效）
  - [x] 可见段 ≥ 20 tick（lint 的时间维度判据的判据本身）

## 6. 验证

- [x] `gradlew build` 通过（384 测试）
- [x] `tools/lang_audit.py` 通过
- [x] `openspec validate danmaku-visual-profile --strict` 通过

## 遗留

- [x] **观感必须实机确认**——本变更的可自动化部分只覆盖几何与时序，**画面本身无法自动验证**：
      - 五角星柱的厚度是否真的看得见、摆动幅度 50° 是否合适
      - 隐藏态的半透明是否读得出、身后弹是否被挖出方洞
      - 默认档案下四只 BOSS 的观感 MUST 与改前无差异
      —— 玩家实机验收通过，`hiddenAlpha = 96` 定档
## 已知缺口与延期项（**不属于本变更的未完成工作**，留给后续 proposal）

机制已就绪、按设计留待后续的项。**刻意不勾**——勾上等于声称已做，而它们确实没做。

- [ ] 五角星柱的贴图复用 `sphere_danmaku.png`（一张径向渐变）。它铺在一个非凸多边形
      三角形面上，渐变会失真；正式做该造型需要一张专门贴图
- [ ] 档案目前只有 2 份。`DanmakuColorMode` 的 CYCLE_HUE / PULSE 尚无档案在用，
      即两者只有单测覆盖、无实机画面
- [x] 相位隐藏尚未接入 `Shape` / `Track`（那属 `danmaku-behaviour-decoupling`）。
      目前只能经调试命令使用，符卡表里配不出来
      —— 已由 `danmaku-behaviour-decoupling` 解决：相位编入 `Beat.Visibility`，符卡表可配
