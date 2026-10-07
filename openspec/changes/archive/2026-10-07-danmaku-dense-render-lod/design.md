# Design

## 上下文

- `DanmakuRenderProbe` 已能统计当前帧 bodyDraws; 800×3 层 = 9600 verts / 2400 getBuffer，实测
  body-only ≡ 三层时间中的 1/3。
- Double additive 未排序（sortOnUpload=false）后单体负载仍不可控，只能靠减遍数。
- 最终对照：glow+core 全保留会卡，全关没好看也不卡。密度切换必须在「同层次观」下运行。

## 决策

1. 不做 GPU instancing：INSTANCED_QUADS 能省 CPU 提交，但 fragment/过绘的主因仍在。
2. 不去掉整层丢失核心构图——用同一张 PNG 只是内容烧好，可接受的视觉减幅换来一张只做 body 的单通。
3. 最终版决策：**不再使用 LOD**，统一走单层 LOD texture：避免光晕/核高加和性能压力；原有三层渲染代码仍保留但改不知不觉关断，所有 glow/core 效果层碰到 probe.effective*  直接不画。
4. 探针不改动：`denseMode` 写入 summary 后直接走 gs_boss 读数。
5. 渲染器贴图：Profile 已带 `texture`,密集版可先做 DEFAULT 和 STAR_PRISM 各一份；其余 profile 可共用。
6. 验证优异后可永久替换：body/glow/core 三层删，剩单 pass 贴图，因其三遍叠加本来近似
   一个环/实心纹，渲染质量只比贴图略下。已永久开启：sphere 单通。STAR_PRISM 保留原体，以免球 LOD 纹理错贴。

## 顺序

1. 生成 `sphere_danmaku_lod.png`；
2. `DanmakuRenderProbe` 加 hysteresis 计数;
3. `Sphere/Talisman/Laser` 渲染器在 body 层选择纹理；
4. 验收：dense 时 probe 行 body=800 glow=0 core=0, 人工确认可读（见环和中心）。
