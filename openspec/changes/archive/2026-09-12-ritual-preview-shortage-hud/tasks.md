## 1. 缺口聚合逻辑

- [x] 1.1 在 `client/RitualPreviewMaterialHud.java` 实现纯函数：输入 `Classification` + `pattern` + `tier` → 按 `resolveState` 聚合 pending 与 conflicts 的目标方块需求（跳过 null 解析），返回 Block→需求数表；airConflicts 不计
- [x] 1.2 缺口计算与排序：`max(0, 需求 − inventory.countItem)`，仅保留 >0；三级比较（缺口降序 → 需求降序 → 注册名）取前 3

## 2. HUD 绘制与门控

- [x] 2.1 挂 `RenderGuiLayerEvent.Post` 过滤 `VanillaGuiLayers.CROSSHAIR`；每帧自算（不从预览渲染器缓存取数）
- [x] 2.2 门控五判：预览态在场且维度匹配、主/副手持 `RitualBuilderItem`、`!hasInfiniteMaterials()`、`!options.hideGui`、图案可解析；任一不满足直接 return
- [x] 2.3 右缘竖直居中逐行右对齐绘制：`GuiGraphics.renderItem` 图标（18px）+ `drawString` "名称 ×N"（带阴影，数量红色）；行距 18（16px 图标需 18 槽位避免重叠）
- [x] 2.4 lang 键 `gui.gensokyou.builder.hud_shortage`（zh_cn/en_us）作数量格式化

## 3. 验证

- [x] 3.1 `gradlew compileJava` 通过（client 非独立源集，仅包名）
- [x] 3.2 客户端实测：缺料预览显示 top3 且排序正确；摆放/拆除方块缺口当帧联动；材料集齐整块消失；切换手持当帧隐藏；创造模式不显示；F1 隐藏；升级预览仅计差量格；红幽灵格计入、AIR 红框不计入
- [x] 3.3 专用服务端 `runServer` 启动至 `Done (`；唯一 ERROR 为 Forgematica mixin 引 LocalPlayer 的既有环境噪声，栈中无 gensokyou 类（客户端类未被服务端加载）
