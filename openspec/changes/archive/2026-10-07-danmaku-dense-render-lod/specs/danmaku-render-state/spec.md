## ADDED Requirements

### Requirement: 密集模式下弹幕渲染必须用单通解析

当屏上弹数超过密度门槛时，球/灵符渲染器 SHALL 发射 body 单通（不带
glow/core 附加层），body 颜色的贴图 SHALL 由「full 渲染[ADD]」与核心素材源
共用，保证环/中心填充是纹理本身的可读证迹。

#### Scenario: 密集时切 swap

- **WHEN** 每帧屏上弹数 >= 密度门槛
- **THEN** 渲染器 SHALL 跳过 glow/core 层，body 贴图 SHALL 为密集版；
  屏上弹数低于门槛回落至 full 三层。

#### Scenario: 密集仍可读

- **WHEN** LOD texture 被选用
- **THEN** 文件里同时携带 halo 圈和中心填充（同一纹理），MUST NOT 关掉
  glow/core 而只留白斑。
