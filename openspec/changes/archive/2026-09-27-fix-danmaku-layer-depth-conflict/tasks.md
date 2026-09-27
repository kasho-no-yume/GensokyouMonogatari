## 0. 先确认机理（已完成，避免照抄错误方案）

- [x] 0.1 从 `minecraft_1.21.1_client.jar` 确认 `rendertype_entity_translucent_emissive.fsh` 确有 `if (color.a < 0.1) discard;` —— 故只有贴图 alpha ≥ 0.1 的区域参与深度竞争，症状范围正是"中心与高光"。
- [x] 0.2 确认第二个成因：`MultiBufferSource.BufferSource#getBuffer` 对 `canConsolidateConsecutiveGeometry()`（QUADS 模式恒为 `!connectedPrimitives` = true）**复用同一 BufferBuilder**；外发光与亮核同属一个 `additiveSolid(SphereTexture)` 实例 → 同一批 `MeshData`；而 `additiveSolid` 的 `sortOnUpload` 为 true → `BufferSource#endBatch` 调 `MeshData#sortQuads`，**按质心到世界原点的距离重排索引**（透视通道下 `GameRenderer` 设为 `VertexSorting.DISTANCE_TO_ORIGIN`）。故"外发光先画、亮核后画"这一提交顺序**不成立**。
- [x] 0.3 确认 `getBuffer(不同 RenderType)` 会立即 `endBatch` 上一批：故"换类型"是获得确定顺序的唯一手段。
- [x] 0.4 确认 billboard 局部 +Z 朝相机：与 `SukimaPortalRenderer:74` 已实机生效的 `OUTLINE_OFFSET = 0.001` 正偏移同一条变换链（`cameraOrientation()` + Y 180°）。

## 1. 层偏移落地

- [x] 1.1 `AbstractDanmakuRenderer`：新增常量 `GLOW_OFFSET = 0.004F`（**public/protected，供子类派生亮核偏移**）与 `offsetGlow(PoseStack)`（默认沿 +Z，非 billboard 的弹幕覆写为 +Y）；`renderGlow` 在 `pushPose` 后先 `offsetGlow` 再 `scale`，使偏移量以父级坐标下的绝对格数生效、不被 `GLOW_SCALE` 缩放。
- [x] 1.2 注释写清三件事：① 为什么等比放大不改变所在平面；② 为什么共面 + 写深度 = 1~2 ULP 量化跨格 → 暗条；③ 为什么偏移量要远小于一像素又远大于 ULP 噪声。
- [x] 1.3 `SphereDanmakuRenderer`：`CORE_OFFSET = 2 × GLOW_OFFSET`；亮核在 `pushPose` 后先 `translate(0, 0, CORE_OFFSET)` 再 `scale`。
- [x] 1.4 `SphereDanmakuRenderer`：**亮核的 RenderType 由 `glowRenderType()`（`additiveSolid`，写深度）改为 `DanmakuRenderTypes.additiveGlow`（不写深度）**。理由：① 三层分处三个批次，消除批内重排；② 不写深度的层永远不会被别的层拒掉，可见性不再依赖提交顺序；③ 亮核 0.55× 严格包含在外发光 1.35× 之内（同贴图），其深度写入被完全覆盖，去掉无遮挡语义损失。
- [x] 1.5 `TalismanDanmakuRenderer`：覆写 `offsetGlow` 为 `translate(0, GLOW_OFFSET, 0)`。外发光仍是 `additiveSolid`，本体仍是 `translucentDepth`，两者 RenderType 不同 → 批次不同 → 顺序确定。

## 2. 方向与量级的实机确认

- [ ] 2.1 符号若反，症状是**外发光整体消失**（被本体写入的深度拒掉）而非条纹——立刻翻转符号，不要继续调量级。
- [ ] 2.2 确认 0.004 格在 5 格距离、1080p 下确为亚像素；必要时下调但 MUST NOT 低于 0.002。

## 3. 已知接受的瑕疵

- [x] 3.1 记录（不改）：灵符从**正下方**观察时外发光被纸背拒掉（外发光沿局部 +Y 偏移，而纸面 `noCull` 双面可见，正下方的视线看到的是纸的背面）。光晕 alpha 仅 110，实际不可见。**若日后有人报"灵符仰视时没有光晕"，答案在此，不是新 bug。**

## 4. 回归验证

- [ ] 4.1 球弹：静止观察、移动镜头扫过、远距离（30+ 格）、大量重叠、贴水面飞行、贴云下飞行、洞穴黑暗环境。全部无条纹、无闪烁、无整层消失。
- [ ] 4.2 球弹水遮挡回归：本体与外发光仍不被后画的水/云覆盖，且未出现方形深度洞。
- [ ] 4.3 灵符：平飞、俯冲、仰角、旋转过程中观察；无条纹。
- [ ] 4.4 飞刀（无发光层）与激光弹幕行为不变，回归通过。
- [ ] 4.5 抓帧或逐帧观察确认三层**同时**可见（外发光未被亮核拒掉），即批内重排隐患已消除。

---

## 5. 归档时的验证状态（2026-09-27）

**自动化验证**：`./tools/gradle_task.ps1 test` 306 项全绿，
`openspec validate fix-danmaku-layer-depth-conflict --strict` 通过。

**实机验证：已完成。** 用户在归档时明确告知观感验收已完成。
§2（偏移符号方向、0.004 格在 5 格距离 1080p 下为亚像素）与 §4（球弹六种观察条件、
水遮挡回归、灵符四种姿态、飞刀与激光回归、三层同时可见）均属**实机观感验收**，
无遗留代码工作。§3 记录的已知瑕疵（灵符正下方仰视无光晕）为**有意接受、不修**。
