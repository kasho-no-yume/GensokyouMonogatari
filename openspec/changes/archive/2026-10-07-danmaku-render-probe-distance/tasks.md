# 任务清单

> 剖针先行：剖针不出来的优化就是猜。剖针的结果写进proposal 的「实测」段。

## 0. 剖针

- [x] 0.1 在 `AbstractDanmakuRenderer.render()`（及各子类的本体/辉光/亮核段）
      push Minecraft Profiler 切片 `gensokyou_danmaku_body|glow|core`。
      验收：F3+P 饼图里可见对应切片。
- [x] 0.2 `/danmaku layers <body|glow|core|all>`：按层开/关。
      验收：关 glow/core 后画面立刻对应层消失；恢复时重画。
- [x] 0.3 每帧计数（各层 draw 数 / 顶点数 / getBuffer 数）并入 `/gs_boss danmaku`
      诊断行。
      验收：墙 800 满屏时读数 ≈ 3×800（三层），与 `/danmaku layers` 对照。
- [x] 0.4 已由 /danmaku layers 与 renderProbe 行实测得穿：glow/core 全关后
      fps 回到顺滑，但 PDF/饼图分布未变 ⇒ 热点在 GPU 填率，不是 CPU 切片。
      无需再录 JFR 验证。

## 1. 渲染距离对齐视据

- [x] 1.1 `AbstractDanmakuProjectile` 覆写 `shouldRenderAtSqrDistance(double)`：
      `Minecraft.getInstance().options.renderDistance().get() * 16` 半径。
- [x] 1.2 `LaserDanmaku` 并入：`max(maxLength + 64, 视据半径)`。
- [x] 1.3 `ModEntityTypes`：全部弹幕 `clientTrackingRange` 改为 32 区块；
      其余实体保持不变。`DANMAKU_UPDATE_INTERVAL` 不变。
      验收：视距 8/12/16/32 对照实测弹幕出现/消失边界；非弹幕实体边界不变。
- [x] 1.4 文档化新的上游帽子：弹幕可追踪距离 512 格；`OrbitYinYangOrb` 仍 8 区块。

## 2. 优化决策（剖针后）

- [x] 2.1 不按距离 LOD；性能路径切换到「全程 LOD 贴图」，由
      `danmaku-dense-render-lod` 承担。
- [x] 2.2 不做：body-only 已测试流畅，批数不再是瓶颈。
- [x] 2.3 不做：微分配优化，延后到真实渲染期间再看热点。

## 3. 验证

- [x] 3.1 `.\tools\gradle_task.ps1 compileJava` 通过。
- [x] 3.2 已用 `renderProbe` 与 `/danmaku layers` 实测在-world：三层通常
      `body=800 glow=800 core=800`，glow+core 关断后 fps 立即恢复，Server thread
      仍低。
- [x] 3.3 跑全部既有测试，零改动。

## 依赖关系

- §0 的 0.4 已用 renderProbe 结果替代；§2 已做决策并出于 `danmaku-dense-render-lod`。
- §1 不依赖 §0——它是确定性改正；§2 的优化目标靠剖针+实测选定 `danmaku-dense-render-lod`。
