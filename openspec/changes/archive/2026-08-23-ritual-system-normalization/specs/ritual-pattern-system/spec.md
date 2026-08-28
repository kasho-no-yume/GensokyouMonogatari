## ADDED Requirements

### Requirement: 声明式结构定义
仪式结构 SHALL 以 JSON 定义于 `data/gensokyou/rituals/`：palette 谓词（精确方块/#标签/_ignore/air）、字符画分层切片、anchorKey 锚点；经资源重载监听器加载，`/reload` 热生效。

#### Scenario: 热加载
- **WHEN** 新增或修改 rituals JSON 后执行 /reload
- **THEN** 新结构定义立即可用于匹配，无需重启

### Requirement: 多级识别
匹配器 SHALL 对同一文件的 levels 自顶向下增量匹配并返回最高可达等级；识别 SHALL 枚举 4 旋转 × 镜像共 8 种朝向。

#### Scenario: 最高等级命中
- **WHEN** 场地同时满足二级结构的全部格子要求
- **THEN** 匹配结果 level=2 而非 1

#### Scenario: 朝向无关
- **WHEN** 结构整体旋转 90° 搭建
- **THEN** 匹配仍然成功

### Requirement: 键位坐标输出
匹配结果 SHALL 携带每个非 _ignore 字符的世界坐标列表（按字符分组），供仪式逻辑定位祭品台等部件。

#### Scenario: 定位祭品台
- **WHEN** 结构含 P 字符的仪式被激活
- **THEN** 匹配结果可给出所有 P 格的世界坐标

### Requirement: 采集命令
SHALL 提供开发命令 `/gs_ritual_capture <名称> <半径> <高度>`（权限≥2）：以玩家脚下为锚扫描区域，自动生成字符画 JSON 骨架输出至日志并回显提示。

#### Scenario: 采集产出
- **WHEN** 在手工搭建的结构中心执行采集命令
- **THEN** 日志输出含 palette 与 slices 的完整 JSON 骨架
