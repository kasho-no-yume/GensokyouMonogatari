# Ritual Pedestal Rest Render Fix — Tasks

## 1. 探针定位（D1）

- [ ] 1.1 临时调试：渲染器叠加绘制 eased 值文字（dev-only，最后回滚此 commit）
- [ ] 1.2 实机 3×2 探针矩阵（2D 物品 / 方块类物品 / 3D 自定义模型 × 静置/激活），记录各类物品静置底缘离台距离，锁定嫌疑：BlockItem 角点枢轴失配 / 模型自带 fixed 平移 / 其他
- [ ] 1.3 回滚调试 commit

## 2. 静置渲染修正

- [ ] 2.1 通用防御：读取物品模型 FIXED 变换 translation 并在抬升计算中自减（命中嫌疑 2 时为主修）
- [ ] 2.2 BlockItem 静置补偿改"旋转后归一"：放弃 `BLOCK_HALF_HEIGHT` 预抬，XP(90°)+scale 后 translate(-0.5, +0.5, 0)（命中嫌疑 1 时为主修）
- [ ] 2.3 复核 2D/3D 物品静置底缘离隙 ≤0.05、激活立姿底缘不切台面、两态过渡无跳变
- [ ] 2.4 复核光照采样（台面上方一格）在姿态修正后仍取到正确格位

## 3. 激活态去持久化（D2）

- [ ] 3.1 `RitualPedestalBlockEntity`：`saveAdditional/loadAdditional` 移除 `TAG_RITUAL_ACTIVE` 读写（旧档多余键静默忽略）
- [ ] 3.2 `RitualCoreBlockEntity`：结构重扫/加载重新成型处按当前 `enabled` 向台位补广播（沿用 `setPedestalsActive`），确认启动/停止/失效路径全覆盖
- [ ] 3.3 单测/纯逻辑验证：重扫广播幂等（重复调用不产生多余 setChanged/包）

## 4. 回归

- [ ] 4.1 三类物品静置/激活/过渡全场景实机复验（对应 spec 四场景）
- [ ] 4.2 仪式消耗/取出物品路径不受渲染改动影响（迦具土吞料、空手取回）
- [ ] 4.3 `idea_build_project` 零错误 + `runServer` 启动无异常
