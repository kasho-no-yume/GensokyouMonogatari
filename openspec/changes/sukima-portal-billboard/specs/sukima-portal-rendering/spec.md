# Delta: sukima-portal-rendering

## REMOVED Requirements

### Requirement: 隙间传送门为眼形棱壳 + 末地门式静态内景

**Reason**: 美术方向改为正式隙间素材（眼睛虚空图）；末地门着色器星空无法呈现素材，且封闭棱壳几何（约 90 行）与末地门渲染类型耦合，不利于未来传送门类技能复用。
**Migration**: 由新需求「隙间传送门为 0 厚度眼形 billboard」替代；传送机制、方块不可见性需求不变。

## ADDED Requirements

### Requirement: 隙间传送门为 0 厚度眼形 billboard
隙间方块（SukimaBlock）SHALL 挂接一个 BlockEntity，其视觉由 BlockEntityRenderer 渲染为 **0 厚度 billboard 传送门**：宽 1 格 × 高 2 格的单层 quad（保持眼形比例，居中于方块位置并可向上延伸显示），每帧朝向玩家相机。主 quad SHALL 使用隙间虚空贴图（`textures/entity/sukima_portal.png`，RGBA），该贴图 SHALL 由源素材 `sukimatexture.png` 经离线脚本烘焙产出（降采样至 256² + 眼形剪影 alpha 遮罩）；眼形遮罩为硬性要求——隙间视觉 SHALL 呈眼形。贴图 UV SHALL 随游戏时间缓慢滚动（REPEAT wrap），呈现虚空流动感。贴图渲染 SHALL 为全亮度（不随环境光衰减）。眼睑描边贴图（16×32，`sukima.png`）SHALL 作为第二层 quad 覆盖于主 quad 前方（偏移约 0.001 防 z-fighting）。贴图与绘制参数 SHALL 收敛为单一可替换点。

#### Scenario: 任意视角可见
- **WHEN** 玩家从任意方向接近并观察传送门
- **THEN** 看到眼形隙间虚空贴图的传送门平面，平面始终正对玩家（billboard）；背面观察呈镜像贴图（眼形左右对称，可接受）

#### Scenario: 0 厚度可穿行
- **WHEN** 玩家走进传送门平面
- **THEN** 视觉上穿过隙间平面，无厚度遮挡、无碰撞体（传送逻辑由既有机制处理）

#### Scenario: 虚空缓慢流动
- **WHEN** 玩家持续观察传送门
- **THEN** 贴图 UV 以缓慢速度循环滚动；UV 相位由游戏时间驱动，与帧率无关

#### Scenario: 描边与主贴图对齐
- **WHEN** 渲染传送门
- **THEN** 眼睑描边 quad 与主 quad 轮廓对齐，无 z-fighting 闪烁

#### Scenario: 全亮度呈现
- **WHEN** 传送门处于黑暗环境（洞穴、夜晚）
- **THEN** 隙间虚空贴图以全亮度呈现，不随环境光变暗

### Requirement: billboard 绘制工具可复用
billboard quad 绘制（几何、UV、贴图、光照、透明度）SHALL 收敛为静态工具类，参数化（半宽/半高、贴图、UV 滚动相位、染色、alpha、光照）；隙间 BER SHALL 经该工具绘制，工具内 SHALL NOT 出现 SukimaBlockEntity 专属依赖；未来传送门类技能的实体渲染器 SHALL 能以不同参数直接复用同一工具完成绘制。

#### Scenario: 工具被 BER 消费
- **WHEN** 渲染隙间方块
- **THEN** 绘制路径经过共享工具类，工具类不引用 SukimaBlockEntity / SukimaPortalRenderer 类型

#### Scenario: 技能复用入口
- **WHEN** 未来技能需要在世界中绘制临时传送门（任意尺寸、贴图）
- **THEN** 经 EntityRenderer 接线后仅以不同参数调用该工具即可，无需新增几何代码
