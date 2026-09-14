# Tasks: resonance-relay-render-perf

> **基线注记（2026-09-14，来自 ritual-ui-behavior-fixes）**：本变更实施时以已合入的
> `ritual-ui-behavior-fixes` 为基线——`ResonanceRelayBehavior.serverTick` 尾部原有的
> `ageTicks%20 → sendRitualInfoToViewers` 自推已删除（收编至 `RitualCoreBlockEntity.serverTick`
> 统一 1Hz 心跳，含停机态）；`nextLinkState`（入→出→无 循环）与 `tipOf`（实测吞吐行）已重写；
> BE 新增 `routedInTotal/routedOutTotal`。task 1.3 的渲染态推送与 task 4.x 粒子删除按新文件现状执行。

## 1. 渲染态同步（服务端）

- [ ] 1.1 `RitualCoreBlockEntity` 增运行时渲染态字段：`enabled`、结构 minY/maxY、解析后的链接数组（按规范序：inLinks 再 outLinks，各含目标 BlockPos + in/out 方向）、`movingMask`(long)；并定义"渲染态版本值"用于变化比较
- [ ] 1.2 覆写 `getUpdateTag`（写入紧凑渲染态子 tag）与 `getUpdatePacket`（`ClientboundBlockEntityDataPacket.create(this)`）；客户端 `loadAdditional`/读取端解析该 tag
- [ ] 1.3 变化即推：链接增删、启停、结构包围盒变化、`movingMask` 变化时 `sendBlockUpdated(pos, state, state, BlockEntityUpdateType.BLOCK_UPDATE)`；稳态无变化不发
- [ ] 1.4 未 enabled / 结构失效时下发清零态（0 链接、mask=0）
- [ ] 1.5 mask 位宽：当前 ≤40 链接用 `long`；断言配额*2 ≤ 64，超限记录"改 long[]"并给出防御分支

## 2. 客户端渲染器

- [ ] 2.1 新增 `client/renderer/RitualCoreRenderer implements BlockEntityRenderer<RitualCoreBlockEntity>`，`GensokyouClient` 注册
- [ ] 2.2 螺旋：据 enabled+包围盒+本地 `gameTime` 推进相位绘制，参数对齐现服务端（间隔 {8,6,4,2}、量随阶级、环绕纵轴）
- [ ] 2.3 光束：仅对 `movingMask` 置位且链接目标仍解析的通道绘制"塔顶→目标"线；出绿/入青蓝；按距离抽稀 + 单束/全局点数上限
- [ ] 2.4 客户端 BE 只读渲染态、不参与服务端逻辑；距离/视锥剔除交给 BER 机制

## 3. 路由端点速率 per-period memo

- [ ] 3.1 `routeTick` 开头为本周期涉及端点各解析一次 `spiritOut/InRatePerSecond` 建缓存
- [ ] 3.2 `needyEndpoints` 与配对预算改用缓存；每结算周期重建；结果与逐次重扫逐位一致
- [ ] 3.3 单测：memo 结果 == 逐次重扫结果（同端点集合）

## 4. 删除服务端粒子 + 搬运位掩码

- [ ] 4.1 删除 `ResonanceRelayBehavior.emitSpiral/emitBeam` 与全部 `level.sendParticles`
- [ ] 4.2 每周期结算时按链接序生成 `movingMask`（本周期该通道实搬>0）；与上次比较，变化则置渲染态脏并推送
- [ ] 4.3 `serverTick` 精简为：周期边界结算 + mask 维护 + 1Hz GUI 推送（不再发粒子）

## 5. 测试与验证

- [ ] 5.1 单测：渲染态序列化/反序列化往返一致；mask 与链接列表索引对应；链接变更时 mask 重置
- [ ] 5.2 单测：memo（3.3）
- [ ] 5.3 按 project.md §8a 重定向构建 `cmd /c "gradlew.bat build --console=plain > build_out.txt 2>&1"`，修复编译/测试错误
- [ ] 5.4 实机观感验收：螺旋随阶级变高、光束仅实搬通道亮、双向配色、停机即灭；用网络抓包/日志确认稳态无逐 tick 粒子包

## 6. 回写

- [ ] 6.1 经验回写 skill（`neoforge-1211-dev`）："服务端粒子=网络包；仪式表现优先客户端 BER + 仅变化同步最小渲染态（仿 Mekanism currentScale）"红线
