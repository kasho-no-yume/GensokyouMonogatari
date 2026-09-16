# Ritual Presentation Polish — Tasks

> 全部任务的实机复核已由用户确认通过（取下/吞料/造化扣料后台面立即清空；三类物品静置贴台；造化飞行无外闪；火毯收束）。

## 1. 探针定位（D1）— 以模型感知方案取代，无需探针

- [x] 1.1 以源码判定取代临时 overlay：反编译确认原版 FIXED 渲染链为
  `handleCameraTransforms(套 display.fixed) → translate(-0.5,-0.5,-0.5) → 画 [0,1]³ 几何`
- [x] 1.2 结论：2D 生成物静置本就贴台；BlockItem 因多计 `block/block` 的 fixed `scale 0.5` 浮高 ~0.1375；
  自定义 3D 由自身 FIXED 平移/缩放决定 → 统一改为按实测包围盒对齐，不再依赖经验常数
- [x] 1.3 未引入调试 commit，无需回滚

## 2. 静置渲染修正（D1）

- [x] 2.1 新增 `client/renderer/ItemFixedBounds`：实测物品在 FIXED 上下文下的包围盒（含 display.fixed 平移/缩放与归中，按 Item 缓存，自定义渲染器回落单位立方体）
- [x] 2.2 祭品台：静置底缘 = `SURFACE_Y + REST_GAP + SCALE*maxZ`；激活立起底缘 = `SURFACE_Y + ACTIVE_GAP - SCALE*minY`；删除旧 `REST_Y`/`FLOAT_Y`/`BLOCK_HALF_HEIGHT` 经验常数
- [x] 2.3 复核 2D/3D 物品静置底缘离隙 ≤0.05、激活立姿底缘不切台面、两态过渡无跳变（用户实机确认）
- [x] 2.4 复核光照采样（台面上方一格）在姿态修正后仍取到正确格位（用户实机确认）

## 3. 激活态去持久化（D2）

- [x] 3.1 `RitualPedestalBlockEntity`：`saveAdditional/loadAdditional` 移除 `TAG_RITUAL_ACTIVE` 读写（旧档多余键静默忽略）
  - 回归修复：去 `RitualActive` 后清空态 update tag 变空，NeoForge `onDataPacket` 默认跳过空 tag ⇒ 客户端残留渲染。
    已覆写 `onDataPacket` 无条件 `loadWithComponents`（用户实机确认修复）。
- [x] 3.2 `RitualCoreBlockEntity`：成型瞬间按当前 `enabled` 向台位补广播（`setPedestalsActive`）；启动/停止/失效路径沿用既有广播，已全覆盖
- [x] 3.3 单测/纯逻辑验证：重扫广播幂等——由 `setRituallyActive` 的 `if (ritualActive != active)` 守卫保证无多余 `setChanged`/包；BE 构造依赖注册表，暂不引入单测

## 4. 姿态状态清零 + 自转相位（D4）

- [x] 4.1 `ANIM` 值改为 `Pose{progress,lastTime,spinDeg}`；`held` 为空时移除该 pos 条目
- [x] 4.2 自转改为相位累积 `spinDeg += SPIN_PER_TICK * eased * dt`；删除 `(float) time * SPIN * eased` 写法
- [x] 4.3 复核：仪式吞料清空台面后放入新物品从静置态起算、无暴旋；正常激活起旋 / 停用停旋连续（用户实机确认）
- [x] 4.4 复核：区块卸载重载后状态从当前 BE 现状重建（无陈旧相位）（用户实机确认）

## 5. 造化飞行位置客户端独占（D5）

- [x] 5.1 `ZaohuaFlightItem` 覆写 `lerpTo(x,y,z,yRot,xRot,steps)` 为空操作，客户端曲线为唯一位置来源
- [x] 5.2 复核：飞行全程无「往外闪现一帧」（覆盖 >60 tick 的飞行以命中周期位置包）；区块往返重载后位置正确续算（用户实机确认）
- [x] 5.3 复核：异常终止（拆核/超时）就地掉落仍按曲线解算当前位置，防吞件不回归

## 6. 迦具土贴地烈火场（D6）

- [x] 6.1 `RitualFxLayout.fireBed(...)` 替换 `flamePillars(...)`：黄金角盘状采样 + 台位点 + 火心，半径硬钳 ≤ structureRadius
- [x] 6.2 `RitualCoreRenderer.renderFlame` 改绘贴地火舌 + 地面脉动辉光（`emitGroundGlow`）+ 火心；非燃烧态零呈现、启停包络保留
- [x] 6.3 客户端本地余烬/火星粒子（`addParticle`），保留低频 LARGE_SMOKE 服务端点缀；无新增服务端粒子包
- [x] 6.4 `GensokyouConfig`：`fxFlame*` → `fxFire*`（密度/半径比/火舌高宽/辉光/脉动/滚动）
- [x] 6.5 贴图：gen_tex 产出 `fire_bed.png`、`fire_tongue.png`；删除旧 `flame_column.png` 并从 `ritual_fx.py` 摘除
- [x] 6.6 重写 `RitualFxLayoutTest`：确定性 / 全部点半径 ≤ structureRadius / 台位缺失仍铺开 / 半径缺失回落 / 边缘系数
- [x] 6.7 实机复核：火舌不出结构范围、远观为贴地火毯而非炎柱群、阶级差异明显、停等即灭、换批无断档（用户实机确认）

## 7. 回归

- [x] 7.1 三类物品静置/激活/过渡全场景实机复验（对应 `ritual-pedestal` 四场景 + 状态清零场景）（用户实机确认）
- [x] 7.2 仪式消耗/取出物品路径不受渲染改动影响（迦具土吞料、空手取回）（用户实机确认）
- [x] 7.3 造化完整合成演出实机复验（对应 `zaohua-crafting` 飞行演出场景）（用户实机确认）
- [x] 7.4 `idea_build_project` 零错误 + `runServer` 启动无异常 + 相关单测通过（`gradlew compileJava`、`gradlew test` 通过；游戏实机运行正常）
