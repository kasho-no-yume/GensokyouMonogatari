## Why

球弹与灵符的外发光层与本体层**落在同一个平面上**，且两层都写深度。共面几何的逐顶点深度不是逐位相同（顶点位置不同 → 投影后差 1~2 ULP），经 24 位深度缓冲量化后随机跨格，输的那层被整体丢弃——该像素就少了一层加法贡献，表现为**间歇性黑色条纹**，且范围正好落在贴图 `alpha ≥ 0.1` 的区域（因为 `rendertype_entity_translucent_emissive` 有 `if (color.a < 0.1) discard;`），即观感上的「中心和高光部分」。

球弹还叠加了**第二个**同症状的成因，且它与提交顺序有关：外发光与亮核使用**同一个 RenderType 实例**（`DanmakuRenderTypes` 按贴图缓存），而 `BufferSource.getBuffer` 对 `canConsolidateConsecutiveGeometry()` 为真的类型（QUADS 模式恒真）会**复用同一个 BufferBuilder**——于是两者进入同一批 `MeshData`。而 `additiveSolid` 的 `sortOnUpload` 为 `true`，`BufferSource#endBatch` 在绘制前会调用 `MeshData#sortQuads` **按质心到世界原点的距离重排四边形索引**（透视通道下 `GameRenderer` 设为 `DISTANCE_TO_ORIGIN`）。也就是说"外发光先画、亮核后画"这个提交顺序**并不成立**，而两者一旦顺序颠倒，靠后的那一层会被靠前那层写入的深度整片拒掉——同样是"少一层贡献"的暗条。

## What Changes

- 球弹三层沿 billboard 局部 **+Z 依次偏移** `0 / +0.004 / +0.008` 格，使三层不再共面。
- 球弹**亮核层改用不写深度的 `additiveGlow`**（外发光仍用写深度的 `additiveSolid`）。这既让三层分处三个批次（`getBuffer` 换类型即结算上一批），彻底消除批内重排的隐患；也使"谁先画"不再影响可见性——不写深度的层永远不会被别的层拒掉。亮核位于外发光之内（0.55× ⊂ 1.35×，同贴图），其原本的深度写入被外发光与本体完全覆盖，去掉**无任何遮挡语义损失**。
- 灵符本体（局部 y=0 平面）与外发光（1.35×，同平面）沿局部 **+Y** 偏移 `0 / +0.004` 格。两层本就分属不同 RenderType、不同批次，顺序确定。
- 偏移量 SHALL 远小于一个屏幕像素（5 格距离、1080p 下 1px ≈ 0.0065 格），MUST NOT 产生可见的层间错位。
- 保留既有深度语义：本体与外发光**继续写深度**（水/云等后画半透明地形仍被正确遮挡），两者的 RenderType 选择一律不变。
- 抽出共享的层偏移常量到 `AbstractDanmakuRenderer`，并把"法向"抽成可覆写方法，使"多层共面 MUST NOT 发生"成为一处可读的约定而非各子类各自记忆。

**不做**：不把本体或外发光改成"不写深度"。`danmaku-sphere` / `danmaku-talisman` 现有条款明文要求二者写深度（这是为修"水盖住弹幕"而刻意加的），改掉会退回旧 bug。

**明确接受的瑕疵**：灵符从**正下方**观察时外发光被纸背拒掉（纸面 `noCull` 双面可见，而偏移只有一个方向）。光晕 alpha 仅 110，实际不可见；已在 tasks 中登记，以免日后被当成新 bug。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `danmaku-sphere`: 「Sphere visual glow effect」新增"三层 MUST NOT 共面、各层须沿视线法向分层偏移"的条款，并据此重写「Three-layer rendering」场景。
- `danmaku-talisman`: 「Talisman body and glow occlusion, full brightness」新增"本体与外发光 MUST NOT 共面"的条款。

## Impact

- `SphereDanmakuRenderer` / `TalismanDanmakuRenderer` / `AbstractDanmakuRenderer`。
- `DanmakuRenderTypes` 不改（三种 RenderType 的写掩码、混合、排序全部保持）。
- 回归验证：近距离 / 中距离 / 远距离、静止 / 移动镜头、水面与云层相邻、灵符俯冲与仰角各若干组合。
