# 幻想建筑块制作与预览

## 在游戏里看样件

在仓库根目录启动开发客户端，进入启用了命令的测试世界。专服玩家需要拥有管理员权限。

```powershell
.\tools\gradle_task.ps1 runClient -NoWait
```

```mcfunction
/gs_landmark_sample install
/gs_landmark_sample visit
```

`install` 把 328 格样件放入独立的 `gensokyou:landmark_sample_v1` 维度，`visit` 把你传到门外。样件原点是 `(12,64,4)`。面向入口看外墙的 `(14,66,5)`、`(15,66,5)`、`(16,66,5)`，应依次看到灰色、浅红、深红，最后两格跨过区块边界。离开视距后回来，或退出世界再进入，可以复核颜色。

## 验证禁放手感

切换到生存模式，在室内拿着一格方块尝试放置。动作栏应显示“此处不能放置方块”，画面不应闪现方块，手持数量也不应变化。再测试床的一半落入保护区、在区外正常放块，以及手持方块右击门。

## 改图纸和配色

在 [`tools/export_landmark_sample.py`](../tools/export_landmark_sample.py) 的 `sample_cells()` 里编辑样件。每格使用相对原点的坐标、材质与形状、一个 `0xRRGGBB` 色值和可选的朝向等方块属性。传 `None` 保留原始灰度。给一面墙统一配色时，可以让该面所有格引用同一个 Python 颜色常量。

```python
cell(3, 2, 1, "wood", 0xCB6548)
cell(4, 2, 1, "wood", 0x873927)
cell(2, 2, 1, "wood", None)
```

灰模建筑共 19 种形状（含人间之里的木/砖半砖、楼梯、障子、暖帘等），另有无需染色的 `landmark_packed_earth` 完整体地面，逐项见[幻想建筑块一览](landmark-building-blocks.md)。灰模中文显示“幻想建筑块”，完整体地面显示“夯实村土”；调试时用 F3 看注册 ID 和方块属性。门的上下两半各占一格，通常为两格指定相同 RGB。

```powershell
.\tools\gradle_task.ps1 createMinecraftArtifacts
python tools/export_landmark_sample.py
python tools/export_landmark_sample.py --check
```

导出器用 Minecraft 1.21.1 开发资源生成灰模模型，同时生成 `data/gensokyou/structure/landmark_sample_v1.nbt` 和 `assets/gensokyou/landmark/landmark_sample_v1.bin`。这两个路径均位于 `src/main/resources/` 下。改完后重启开发客户端或重建部署包，再安装样件。配色从 Mod 自带资源读取。布局删格时用新测试世界预览，因为重复安装只覆盖新模板实际包含的位置。若要改原点、尺寸或部署另一栋建筑，需要同步定义对应的模板、配色和保护范围。目前提供的是固定样件入口。

## 调试褪色与缺块

在样件维度的测试世界里运行以下命令，并观察 `(15,66,5)`。

```mcfunction
/setblock 15 66 5 minecraft:air
/setblock 15 66 5 gensokyou:landmark_brick
/setblock 15 66 5 gensokyou:landmark_wood
```

依次应看到空洞、灰砖、恢复浅红的木板。颜色与坐标及预期方块 ID 绑定。同一个 ID 换回原坐标会恢复原配色。门的开关等状态变化保留颜色。配色文件缺失、格式错误或损坏时，在客户端日志搜索 `Could not load landmark sample colors`，样件会以灰度显示。

## 博丽神社外围地标

主神社保持独立模板，外围第一批由三块卫星地标组成：门前小集、北侧客殿院、东侧钟楼与练习场。图纸在 `tools/export_hakurei_outer_grounds.py`，产物为三份结构 NBT 和三份按幻想乡世界坐标保存的 GLC2 配色。导出器会按原版与模组 blockstate 资源校验所有方块 ID，并在门前参道行走高度设置障碍检查。

```powershell
python tools/export_hakurei_outer_grounds.py
python tools/export_hakurei_outer_grounds.py --check
```

在幻想乡维度可用以下管理员命令走查；命令会确保主神社和外围地标已放置：

```mcfunction
/gs_hakurei_preview visit_gensokyo front
/gs_hakurei_preview visit_gensokyo north
/gs_hakurei_preview visit_gensokyo east
```

三块地标分别占用 `(2976,78,96) 46×18×60`、`(3040,82,62) 72×26×31`、`(3118,88,100) 48×32×92`。修改尺寸或世界原点时，需要同步 `HakureiOuterGrounds`、`GensokyoLayout` 的平地和净空范围，以及[建筑块一览](landmark-building-blocks.md#配色资源与生效范围)的配色表。

## 配色文件与性能

夯实村土不走以下配色流程，完整颜色来自模型贴图，可直接作为人间之里的 Worldgen 表层；不为地面生成 `.bin`。人间之里建筑模板使用 GLC3 局部坐标配色并复用实例，固定旧博丽样件使用下述 GLC2。当前村庄放置、高度与旧世界兼容规则见[现行设计](../openspec/changes/rework-human-village/design.md)。

文件用 GZIP 压缩。`GLC2` 保存 RGB 色板、预期方块 ID 表和有染色数据的区块段。每个 `16×16×16` 段解压为 4 KiB 色号和 4 KiB 方块编号。色号 0 表示原灰度，单份资源支持 255 个显式 RGB。区块网格构建时按坐标直接查数组，解压发生在客户端加载染色资源时。连续的同色面与空格通常能被 GZIP 压得很小。大建筑可根据实际占用段数评估内存，再决定是否需要稀疏存储。

自动验证命令与实机验收项目见样件的[验收记录](../openspec/changes/archive/2026-11-24-prototype-tinted-landmarks/verification.md)。
