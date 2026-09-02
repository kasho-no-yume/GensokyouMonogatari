# Delta: sukima-portal-rendering

## REMOVED Requirements

### Requirement: 隙间传送门为眼形棱壳 + 末地门式静态内景

**Reason**: 美术方向改为正式隙间素材（眼睛虚空图）；封闭棱壳几何（约 90 行）与末地门渲染类型耦合，侧壁不利于复用；末地门着色器无法呈现自定义贴图素材。
**Migration**: 由新需求「隙间传送门为眼形窗（billboard 框 + 末地门虚空内景）」替代——保留末地门渲染类型于内景（其屏幕投影锚定效果即「窗」质感的来源），改为 0 厚度 + 眼形几何 + billboard 朝向；传送机制、方块不可见性需求不变。

## ADDED Requirements

### Requirement: 隙间传送门为眼形窗（billboard 框 + 末地门虚空内景）
隙间方块（SukimaBlock）SHALL 挂接一个 BlockEntity，其视觉由 BlockEntityRenderer 渲染为**眼形窗**：整体 **billboard——每帧正对玩家相机**（相机四元数 + Y 轴 180° 翻转），并绕视线轴倾斜 10°。框与内景 SHALL 分离呈现：
- **内景**：SHALL 为眼形透镜面几何（竖条带扇面，尖端在中线、上盖 0.85/下弧 1.15/半宽 0.5，总高 2 格、居中于方块纵跨 y 0..2），以**自定义核心着色器** `gensokyou:sukima_portal`（克隆原版末地门机理：裁剪空间投影采样 + 分层视差漂移）渲染**自有眼睛虚空贴图**（双采样槽位同贴图，无原版星空）——呈现「锚定的层叠虚空」；内景 SHALL NOT 以整幅静态贴图方式呈现（目检否决：糊在框上像静态图），亦 SHALL NOT 使用原版星空（用户指定内容为自有眼睛纹理）。背景 SHALL 呈偏黑黑红色调、层叠眼密度 SHALL 显著低于原版参数（黑红调色板、6 层、层缩放 2.35——目检定稿，调参点集中于着色器文件）。0 厚度：仅前后两个透镜面，SHALL NOT 有侧壁。
- **眼睑框**：16×32 描边贴图（`sukima.png`）cutout 单层 quad 覆盖于内景前方（billboard 局部 +z 偏移 0.001 防 z-fighting）。
内景末地门效果 SHALL 全亮度呈现（着色器固有，不随环境光衰减）。绘制参数 SHALL 收敛为单一可替换点。

#### Scenario: 一直正对玩家
- **WHEN** 玩家从任意方向接近并观察传送门
- **THEN** 眼形窗整体每帧正对玩家（billboard），呈 10° 倾角；无侧壁、0 厚度

#### Scenario: 框与内景分离
- **WHEN** 玩家转动视角或绕传送门移动
- **THEN** 眼睑框随 billboard 正对玩家，而内景呈末地门式锚定虚空（屏幕投影 + 分层视差），不与框糊成一张静态图

#### Scenario: 0 厚度可穿行
- **WHEN** 玩家走进传送门平面
- **THEN** 视觉上穿过隙间平面，无厚度遮挡、无碰撞体（传送逻辑由既有机制处理）

#### Scenario: 描边与内景对齐
- **WHEN** 渲染传送门
- **THEN** 眼睑描边 quad 与眼形内景轮廓对齐，无 z-fighting 闪烁

#### Scenario: 全亮度呈现
- **WHEN** 传送门处于黑暗环境（洞穴、夜晚）
- **THEN** 内景虚空以末地门固有亮度呈现，不随环境光变暗

### Requirement: billboard 绘制工具可复用
billboard quad 绘制（几何、UV、贴图、光照、透明度）SHALL 收敛为静态工具类，参数化（半宽/半高、RenderType 或贴图、UV 滚动相位、染色、alpha、光照）；隙间 BER 的描边层 SHALL 经该工具绘制，工具内 SHALL NOT 出现 SukimaBlockEntity 专属依赖；未来传送门类技能的实体渲染器 SHALL 能以不同参数（含朝向变换与 UV 滚动相位）直接复用同一工具完成绘制。

#### Scenario: 工具被 BER 消费
- **WHEN** 渲染隙间方块
- **THEN** 描边层绘制路径经过共享工具类，工具类不引用 SukimaBlockEntity / SukimaPortalRenderer 类型

#### Scenario: 技能复用入口
- **WHEN** 未来技能需要在世界中绘制临时传送门（任意尺寸、贴图、朝向或滚动）
- **THEN** 经 EntityRenderer 接线后仅以不同参数调用该工具即可，无需新增几何代码
