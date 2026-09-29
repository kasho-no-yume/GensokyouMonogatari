## 1. Behaviour 抽象（纯数据，公共包内不引用实体）

- [x] `danmaku/track/Behaviour` = `Motion` × `Split` × `Visibility`
- [x] `Motion.Kind`：NONE / CURVE / HOVER / MINE / GROUND_HUG
  - [x] 曲射轴以 (yaw, pitch) 两角度表达——沿用 `configureCurve` 的既有打包约定
  - [x] `effective()` 供 lint 判参数有效性
- [x] `Split.at(tick, count)`，与运动正交（悬停弹也可分裂 = 原需求⑤）
- [x] `Visibility.phaseHide(period, duty, phaseOffset)`——把 change 2 做好的隐藏态接进符卡表
- [x] `withMotion` / `withSplit` / `withVisibility` 便于只改一项
- [x] 记录**可判定的边界规则**：改变「怎么动/怎么显/怎么死」→ 行为；
      改变「能命中什么/如何判定命中」→ 独立弹种

## 2. 几何瘦身与改名

- [x] `Shape.Params` 14 字段 → **8 字段**（7 个行为参数移出：hover/mine/curve×3/split×2）
  - [x] 14 → 11 copy-wither（新增 `rise`）
- [x] `Shape` 项名去行为化——**名字里编码行为本身就是耦合的体现**
  - [x] `HOVER_BURST` → `RADIAL_BURST`（几何只是「一圈等角径向」）
  - [x] `CURVE_RING` → `RING`（环就是环，曲射是行为）
  - [x] `MINE_RING` → `SCATTER_STATIC`（几何只负责「放在哪」）
  - [x] `GAP_SPLIT` → `GAP_FAN`（「补位」指几何排布，与行为分裂无关）
  - [x] `GROUND_BAND` + `DOME` → **合并为 `SHELL`**：二者几何完全相同、只差 Y 系数
        （0.15 贴地 / 1.0 穹顶），差别由 `Params.riseFactor` 承载
- [x] `Geometry` 只产几何，MUST NOT 依据形状改写运动

## 3. 节拍承载行为

- [x] `Track.Beat` 增加 `Behaviour behaviour` 字段
- [x] `Track.Builder.at(...)` 增加带行为的重载；旧签名保留（等价于 `Behaviour.NONE`）
- [x] `Track.Beat.behaviourIndex()`——行为轴的量化档位
- [x] `DanmakuEmitter` 的 `switch (shape)` **删除**，改为按**行为种类**分派
- [x] `TrackRunner` 传递 `beat.behaviour()`

## 4. 补上一处既存的 spec/impl 缺口

- [x] `VisualIdentity` 加 `behaviourIndex` 轴
  - [x] 起因：spec 的四轴是「色相/速度/尺寸/**行为**」，而实现只有
        colorStep/speedStep/sizeStep/shapeIndex——**行为维度从未参与断言**，
        且「形状」不在 spec 的四轴里
  - [x] 形状轴与行为轴均由首个拍**自动导出**，符卡作者仍只需声明三档

## 5. TrackLint 行为判据（与几何判据分开）

- [x] `lintBehaviour`：运动参数有效性 / 溜め MUST 配静止散布几何 / 分裂发数 ≥ 2 / 贴地配零速无意义
- [x] **R4 时间维度**：可见段 ≥ 20 tick、隐藏段 ≤ 60 tick
  - [x] 起因：R1/R2/R3 全是几何判据，而「高密度弹幕墙周期变暗」在几何上就是
        「一个 count=8 的平面」——几何判据对它**一个字都说不出来**

## 6. 迁移 BossCards（四只 BOSS，14 条轨道，逐条保语义）

- [x] 鬼蛛「地滑帯」：`SHELL(rise=0.15)` + `Motion.groundHug()`
- [x] 鬼蛛「定幕」/ 狐火「浮泡」/ 傩「定幕」：`RADIAL_BURST` + `Motion.hover(n)`
- [x] 鬼蛛「包囲」：`SHELL(rise=1.0)`（原 DOME，纯几何无行为）
- [x] 鬼蛛「潜溜」/ 傩「地溜」：`SCATTER_STATIC` + `Motion.mine(r)`
- [x] 鬼蛛「補発」：`GAP_FAN` + `Split.at(18,4)`
- [x] 狐火「縦曲」/「横曲」：**同一几何 `RING` + 不同 `Motion.curve()`**
      ← 解耦的直接体现：改前是 `CURVE_RING` 一个形状，靠内部参数区分两条轨
- [x] 狐火「破裂環」：`RING_FACING` + `Split.at(24,3)`
- [x] 傩「地連」：`SHELL(rise=0.15)` + `Motion.groundHug()`

## 7. 测试

- [x] `BehaviourDecouplingTest`（11 例）
  - [x] `Shape.Params` 纯几何（反射断言无行为字段 + 字段数恰为 8）
  - [x] 4 几何 × 5 行为 = 20 种组合各自独立成立
  - [x] 三种行为彼此可组合（悬停 + 分裂 + 相位隐藏 = 原需求⑤）
  - [x] 溜め配移动几何被拒 / 配静止散布通过 / 零角速度曲射被拒
  - [x] R4：可见段过短被拒 / 隐藏段过长被拒 / 均衡参数通过 / 短隐藏段通过
  - [x] 行为轴参与视觉独占判定
- [x] 既有测试全过（`BossCardLintTest` / `SpellCardThresholdTest` / `TrackRepeatTimingTest`）
      —— 这是迁移**保住语义**的回归护栏

## 8. 验证

- [x] `gradlew build` 通过（400 测试）
- [x] `tools/lang_audit.py` 通过
- [x] `tools/validate_ritual_pattern.py` 全部通过
- [x] `openspec validate danmaku-behaviour-decoupling --strict` 通过

## 过程中修正的两处自己的错误

- `lintBehaviour` 起初写成从字符串 `where` 里反解形状名（`shapeIsStaticHere(where)`）——
  丑且脆弱。改为直接传 `Shape` 进去
- 两个测试前提写错（都是**测试**错、**代码**对）：
  - `FAN` 的 `guaranteesGap` 为 false，`count=8` + `SELF_AXIS` 会**正确地**触发 R2
  - `period=400, duty=0.9` ⇒ 隐藏段仅 40 tick，本来就**没超** `MAX_BLIND_TICKS=60`

## 遗留

- [x] **实机确认**：14 条轨道的观感与移动手感 MUST 与改前一致。本变更**没有任何新的
      玩家可见图案**，只是同一批图案换一种写法表达，故任何观感差异都属回归
      —— 玩家在游戏内逐轨跑过 `/danmaku card`，未发现回归
## 已知缺口与延期项（**不属于本变更的未完成工作**，留给后续 proposal）

机制已就绪、按设计留待后续的项。**刻意不勾**——勾上等于声称已做，而它们确实没做。

- [ ] 相位隐藏现在能配进符卡表了，但**四只 BOSS 尚未有一张符卡用它**——
      弹幕墙（需求 3）要等实际设计符卡时才落地
- [ ] `Shape` 项名变更后，`VisualIdentity.shapeIndex` 的 ordinal 值随之改变。
      该值只用于「两轨标识全同」的相等判定，故无语义影响；但若将来有存档/配置引用
      这些 ordinal，须一并迁移
