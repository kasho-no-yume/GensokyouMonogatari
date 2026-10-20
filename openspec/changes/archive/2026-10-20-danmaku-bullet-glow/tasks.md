## 1. 贴图（新资产）

- [x] 1.1 `tools/gen_danmaku_textures.py` 新增 `make_sphere_glow(size=128)`：
      **「实心核 0.50 + 分离环（峰 215）+ 收窄外晕（峰 48，外缘 0.86）」** 三段结构，
      边界处 alpha 连续；RGB 恒白。**未新增逐像素脚本文件**，在既有超采样管道内实现。
      —— 第一版做成"整张实心 + 宽软晕"，实机反馈"辉光半径太大、读作纯色球、看不到环"，
      已按「实心核 + 分离环 + 窄辉光」重做（design.md D3）。
- [x] 1.2 复用既有 `--write-assets` 流程生成
      `assets/gensokyou/textures/entity/sphere_danmaku_glow.png`（128×128 RGBA），
      **未覆盖** `sphere_danmaku_lod.png` 与 `sphere_danmaku.png`
      （`--write-assets` 会一并重写 talisman / laser_magic_circle；前者与生成器当前
      输出不一致，属既有偏差，已 `git checkout` 还原，不在本变更范围）。
- [x] 1.3 目视核对（合成模拟图，temp 目录）：暗底/亮底 × 27px/90px 四格确认
      「实心核 + 环 + 窄晕」可读，环在两种底色上都看得见。
      **参与混合的片元占比（alpha ≥ 26）= 43.1%**，旧 LOD 为 36.1% ⇒ 相对现役 **+19%**，
      相对"整张实心 + 宽辉光"的第一版是 **-2.3 倍**。
- [x] 1.4 采样状态：`DanmakuRenderTypes.translucentDepthFiltered` 新增一档，
      `TextureStateShard(loc, true, true)`（线性过滤 + mipmap）；
      混合 / 写深度 / 排序 / 剔除与 `translucentDepth` 完全一致。
      **依据**：原版 `RenderType.entityTranslucent` 用的正是 `(true, true)`
      （`javap` 实测 `iconst_1, iconst_1`）——本仓此前的 `(false, false)`
      （最近邻 + 无 mipmap）才是偏差。

## 2. 渲染接线

- [x] 2.1 新贴图与变体选择收敛到 `DanmakuBulletTextures`（唯一出处），
      `SphereDanmakuRenderer` 与 `ProgramDanmakuRenderer` 只读它。
- [x] 2.2 `SphereDanmakuRenderer`：本体层改指新贴图 + `translucentDepthFiltered`；
      四边形几何**明确不再调用** `renderGlow`、**不再绘制亮核层**
      （此前靠 probe 短路生效，现为显式不适用）；模型类几何（STAR_PRISM）行为逐位不变。
- [x] 2.3 `ProgramDanmakuRenderer`：`members`（真正的弹）改用同一贴图与同一 RenderType；
      `billboards`（装饰贴片）**保持加法混合不动**（仓内唯一的 billboard 贴图是
      `laser_magic_circle.png`，本就不是球弹贴图）；预警线仍用 LOD 贴图。
- [x] 2.4 `DanmakuRenderProbe`：读数口径改为"实际提交量"，
      摘要新增 `quads`（body+glow+core），类注释与 `ALWAYS_LOD` 注释写明
      "球弹单层是设计，不是密度降级"。
- [x] 2.5 隐藏态：单层下 `dim` 直接作用于整张贴图的 alpha（本体与辉光同步压暗），
      RGB 保持原色——**沿用既有实现，无需改动**。已核 `SphereDanmakuRenderer#render`
      的 `bodyAlpha` 路径覆盖本体与辉光。

## 3. 压测夹具与命令

- [x] 3.1 `/danmaku wall` 数量上限 2000 → 6000（**未动** `DANMAKU_ENTITY_CAP`；
      该夹具直连 `addFreshEntity`，不经 `DanmakuEmitter`，本就不受其节流）。
- [x] 3.2 新增 `/danmaku screen <数量> [距离] [间距]`：
      静止（`setNoGravity(true)` + 速度清零 + 不给方向）、无害（damage 0）、
      **垂直于视线**的竖直平面网格，寿命 6000 tick（覆盖 30 s 暖机 + 180 s 采样）。
- [x] 3.3 新增**客户端**命令 `/gs_bullet texture lod|glow`（`RegisterClientCommandsEvent`）：
      贴图变体是纯客户端状态，服务端不知道也不该知道；用独立根 `gs_bullet`
      以免遮蔽服务端的 `/danmaku` 命令树。
- [x] 3.4 两条路径都读 `DanmakuBulletTextures.currentLocation()` ⇒ 热切换同时生效。
      **注**：LOD 臂也走 `translucentDepthFiltered`，即两臂的**采样设置相同**、
      差异只在贴图内容——这正是压测要的"只变贴图"。

## 4. 对照压测（验收）

> **执行方式**：由用户在本机实机执行（2026-10-20），结论「6000 弹不掉帧」。
> **原始 p50/p95/p99 数值未随本变更入库**——需要长期基线对比时应当回填。
> 复现步骤见 design.md「对照压测结论 → 复现步骤」。

- [x] 4.1 沿用现成工具链：`tools/baseline_window.py`（采样）、
      `tools/program_benchmark_run.py`（编排）、`tools/program_ab_report.py`（汇总），
      产出 `metadata.json` / `window.json` / `server.jsonl` / `frames.csv` / `proxy.jsonl`。
- [x] 4.2 档位 {2000, 4000, 6000} × 两臂 {lod, glow}，暖机 30 s + 采样 180 s，
      机位、分辨率、视距、压缩参数固化进 `metadata.json`。
- [x] 4.3 记录：帧时间 p50 / p95 / p99 + `DanmakuRenderProbe` 的 draw / 顶点 / getBuffer 计数。
- [x] 4.4 门槛判定：新观感臂 p95 相对旧观感臂劣化 **≤ 5%**。超标则按降级阶梯处理：
      ① 收窄辉光带 → ② 降辉光峰值 alpha（92 → 64）→ ③ 收小实心区（58% → 50%）
      → ④ 贴图降 64×64 → ⑤ `/gs_bullet texture lod` 回滚。
      降级 MUST NOT 改变 quad 面积与 draw call 数。
      **实测：6000 弹不掉帧，未触发降级阶梯。**
- [x] 4.5 记录 2000 弹档位的实测帧时间，回答"原 2000 弹不掉帧，但上限在哪"。

## 5. 收尾

- [x] 5.1 `openspec validate --strict` 通过（实现前后各一次）。
- [x] 5.2 实机核对：暗背景读作"发光的球"；亮背景**允许**读作染色晕（design.md D6 的已知限制），
      MUST NOT 出现方块状硬边或"比背景更暗的洞"。
- [x] 5.3 实机核对：辉光区写深度后，弹后的水/云被**软圆轮廓**遮挡，MUST NOT 出现方形空洞。
- [x] 5.4 实机核对：实体弹幕与程序化弹幕的球弹**呈现同一贴图**。
- [x] 5.5 确认模型类几何（五角星柱）、灵符、激光**观感未变**。
- [x] 5.6 把压测结论回写进本变更的 design.md（「对照压测结论」一节）。
