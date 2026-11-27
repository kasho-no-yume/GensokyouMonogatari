# danmaku-cone Specification

## Purpose
TBD - created by archiving change rework-danmaku-weapon-orb. Update Purpose after archive.
## Requirements
### Requirement: 鳞形箭头弹幕的生成与建模
系统 SHALL 提供实体 ID 为 `cone_danmaku` 的鳞形弹幕，参照原作的拱形双翼、白色内纹和中央亮瓣建模：鳞片 SHALL 沿局部 X-Z 平面展开，前尖沿 +Z 指向飞行方向，后缘保留双翼与中央鳞瓣之间的分叉。横向宽度约为 0.30 格、纵向长度约为 0.34 格、最大厚度约为 0.07 格。白色内纹 SHALL 由本体顶点色提供，外缘保留实体色相与收敛的辉光，MUST NOT 改成带杆尾的箭矢或金属锥体。该实体 SHALL 继承既有弹幕实体的伤害、命中消亡与同步机制，MUST NOT 引入与 `AbstractDanmakuProjectile` 并行的第二套弹幕基类。

轮廓与色带参考：[原作鳞弹 sprite](https://maribelhearn.com/assets/games/faq/bullets/arrowhead.png)。原始 sprite 仅用于对照，不作为发布资源复制；三维模型与高清中性材质由项目维护。

箭头弹体 SHALL 使用 3D 几何渲染，MUST NOT 使用朝向相机的 billboard 四边形——弹体具有明确朝向，billboard 会使其失去立体感与方向可读性。

鳞弹 SHALL 使用低面数模型：最多五个纵向截面，每个鳞瓣横向只分两条色带，尾端以单面封闭；本体每遍最多提交 70 个四边形。曲面平滑度 MUST NOT 依赖密集横纵细分，主要保留轮廓、分叉与白芯。

#### Scenario: 生成鳞形弹幕
- **WHEN** 鳞形弹幕以伤害值生成
- **THEN** 生成一枚前尖朝前、后缘分叉的薄型鳞片弹幕实体，命中实体或方块时消亡并结算伤害

#### Scenario: 几何为三维
- **WHEN** 玩家从侧面观察飞行中的鳞形弹幕
- **THEN** 弹体呈现可见的截面厚度与立体轮廓，MUST NOT 始终正对相机

### Requirement: 尖端恒朝速度方向
鳞形弹幕的尖端 SHALL 沿自身速度方向朝前：飞行方向改变时，弹体朝向 MUST 跟随，并在渲染帧间插值。静止（速度为零）时朝向不作要求，SHALL 保持最后一次的有效朝向。

朝向 MUST 由既有弹幕基类的"由速度写朝向"机制承载（`updateRotationFromVelocity`），MUST NOT 另建一套姿态同步逻辑。

#### Scenario: 尖端跟随速度
- **WHEN** 鳞形弹幕的速度方向改变
- **THEN** 立体弹头的尖端朝向新的速度方向，MUST NOT 仍朝向模型下方

#### Scenario: 静止时朝向任意
- **WHEN** 鳞形弹幕的速度为零
- **THEN** 弹体保持最后一次有效朝向，MUST NOT 出现朝向跳变或模型翻转

### Requirement: 判定箱与视觉尺寸解耦
鳞形弹幕的判定箱 SHALL 与其视觉尺寸**解耦**：视觉宽度约为 0.30 格，判定宽度 SHALL 略大于视觉宽度（约 0.32 格），使弹体仍可被可靠命中。碰撞缩放 MUST 复用弹幕视觉档案既有的“视觉缩放与碰撞缩放分离”机制，MUST NOT 让判定箱与视觉宽度共用同一个参数。

#### Scenario: 细弹可被命中
- **WHEN** 玩家以鳞形弹幕射击一个横向移动的目标
- **THEN** 弹体掠过时能可靠命中目标，MUST NOT 出现"看着擦过却没伤害"

#### Scenario: 换模型不破判定
- **WHEN** 锥形弹幕的几何或视觉尺寸被调整
- **THEN** 判定箱尺寸不受影响，判定与视觉的解耦关系保持成立

### Requirement: 锥形弹幕的存续约束
锥形弹幕的存续 SHALL 同时受存活时间与运行距离约束，取先到者消亡。该约束 SHALL 通过武器核的 `travelDistance` 维度表达（见 `danmaku-weapon` 的「弹道有效距离的第二约束」），锥形弹幕自身 SHALL NOT 新增一套存续规则。

锥形弹幕 SHALL NOT 产生尾迹子弹——10 发/秒的射速下尾迹会使同时在飞的弹幕实体数翻倍。

#### Scenario: 运行距离先到
- **WHEN** 鳞形弹幕朝天空飞行并达到其运行距离上限
- **THEN** 弹丸消亡，MUST NOT 继续飞行满存活时间

#### Scenario: 不产生尾迹
- **WHEN** 锥形弹幕飞行
- **THEN** 不生成尾迹子弹，弹幕实体数与发射数一一对应

### Requirement: 近相机淡出
锥形弹幕的渲染 SHALL 按**到相机的距离**淡出：距离小于 1.0 格时完全不可见，1.0~2.6 格之间平滑过渡到完全可见。

该要求的存在是因为：武器弹丸生成在**眼睛**位置（即相机位置），弹丸第一帧就贴在镜头上；鳞形弹幕的正面外接尺寸约 0.30 格，再叠一层加法外发光会放大近距离视觉干扰，而麟弹是 10 发/秒（**10 Hz 频闪**）。

该淡出 SHALL 为**纯渲染行为**：判定、轨迹、生成点 MUST NOT 改变，故对瞄准与命中零影响。第三人称相机在射手身后数格，天然不触发。

#### Scenario: 贴脸发射不糊屏
- **WHEN** 玩家从第一人称视角正前方连续发射鳞形弹幕
- **THEN** 弹丸在离开相机 1.0 格内不可见，不出现整屏白闪或高频闪白

#### Scenario: 淡出不影响命中
- **WHEN** 鳞形弹幕在淡出区间内命中目标
- **THEN** 命中判定与伤害照常发生，MUST NOT 因淡出而漏判

#### Scenario: 远处弹体不淡出
- **WHEN** 鳞形弹幕飞到离相机 2.6 格以外
- **THEN** 完全按本体色与外发光强度显示，不受淡出影响
