# ritual-pattern-system Specification

## Purpose
仪式结构的声明式定义（稀疏偏移 + 对称构造性约定）、匹配器与游戏内采集工具（构造仗 / 采集命令）。
## Requirements
### Requirement: 声明式结构定义
仪式结构 SHALL 以 JSON 定义于 `data/gensokyou/rituals/`：palette 谓词（精确方块/#标签）、按层级组织的方块偏移表（每项为相对锚点的稀疏偏移，仅存对称规范四分之一，加载期展开为全量）、全文件唯一的 anchorKey；经资源重载监听器加载，`/reload` 热生效。

#### Scenario: 热加载
- **WHEN** 新增或修改 rituals JSON 后执行 /reload
- **THEN** 新结构定义立即可用于匹配，无需重启

#### Scenario: 四分之一展开
- **WHEN** 某层级仅声明轴上位 (0,0,1) 与对角位 (1,0,1) 两个方块条目
- **THEN** 加载后该层级展开为核心四周 4 个与四角 4 个共 8 个方块位置

### Requirement: 多级识别
匹配器 SHALL 对同一文件的 levels 自顶向下增量匹配并返回最高可达等级；锚点 SHALL 全文件唯一定位；朝向枚举 SHALL 仅含 4 种旋转——镜像变换因中心对称约定而移除。

#### Scenario: 最高等级命中
- **WHEN** 场地同时满足二级结构的全部格子要求
- **THEN** 匹配结果 level=2 而非 1

#### Scenario: 朝向无关
- **WHEN** 结构整体旋转 90° 搭建
- **THEN** 匹配仍然成功

### Requirement: 键位坐标输出
匹配结果 SHALL 携带每个字符键的世界坐标列表（按规范序：层自下而上、z 自北向南、x 自西向东），供仪式逻辑定位祭品台等部件；该顺序 SHALL 在结构整体旋转时保持不变。

#### Scenario: 定位祭品台
- **WHEN** 结构含 P 字符的仪式被激活
- **THEN** 匹配结果可给出所有 P 格的世界坐标

### Requirement: 采集命令
SHALL 提供开发命令 `/gs_ritual_capture <名称> <半径> <高度>`（权限≥2）：以玩家脚下为锚扫描区域，自动生成稀疏偏移格式 JSON 骨架输出至日志并回显提示；产出 SHALL 随附对称归并与锚点唯一性校验结果回显。

#### Scenario: 采集产出
- **WHEN** 在手工搭建的结构中心执行采集命令
- **THEN** 日志输出含 palette 与 blocks 的完整 JSON 骨架

#### Scenario: 采集校验提示
- **WHEN** 采集区域存在对称类冲突或核心数量异常
- **THEN** 回显明确指出违规项，便于修正后再入库

### Requirement: 加载期构造性校验
对称约定 SHALL 由存储格式构造性保证（四分之一展开）；loader SHALL 拒绝无法通过展开的文件：展开后出现重复格冲突、anchorKey 缺失/重复/不在原点，均 SHALL 拒载并在日志报明文件名与原因；合规文件 /reload 热生效不受影响。

#### Scenario: 冲突拒载
- **WHEN** 某 JSON 展开后同一偏移被两个不同字符声明
- **THEN** 该文件被拒绝加载，日志指出冲突位置

#### Scenario: 锚点异常拒载
- **WHEN** 某 JSON 的 anchorKey 字符缺失或出现在非原点偏移上
- **THEN** 该文件被拒绝加载，日志说明原因

#### Scenario: 合规热载
- **WHEN** 修改合规 JSON 后执行 /reload
- **THEN** 新定义立即生效，无告警

### Requirement: 构造仗框选采集
SHALL 提供仪式构造仗物品：对手持玩家，左键方块设定第一角点、右键方块设定第二角点，两点确立后 SHALL 将该 AABB 内全部方格捕获为稀疏偏移格式 rituals JSON 骨架（区域内恰一个祭仪核心定为锚点原点），经对称类归并校验后输出至日志且向玩家回显结果；潜行右键 SHALL 清除当前选择。

#### Scenario: 框选捕获
- **WHEN** 玩家用构造仗先后左键与右键两个方块
- **THEN** 日志输出覆盖该 AABB 全部必需方块的完整 rituals JSON 骨架

#### Scenario: 归并冲突回显
- **WHEN** 框选区域内存在对称位置字符不一致
- **THEN** 回显明确的冲突原因，仍输出骨架供修正参考

#### Scenario: 选择重置
- **WHEN** 玩家已设一角点后潜行右键
- **THEN** 清除选择，下次点击重新开始框定

### Requirement: 品阶方块与仪式等级推导
仪式石与祭品台 SHALL 按品阶 0-5 以独立方块存在（`ritual_stone_0..5`、`ritual_pedestal_0..5`），并 SHALL 分别以方块标签（`#gensokyou:ritual_stones`、`#gensokyou:ritual_pedestals`）纳入全部品阶；palette 谓词 SHALL 同时支持两种写法——标签（该位任意品阶）与精确方块名（该位严格指定品阶），无需新谓词语法。匹配成功后 SHALL 从匹配结果推导**仪式等级**：取结构内全部仪式石/祭品台方块品阶的最大值；推导 SHALL 不依赖匹配器遍历逻辑（后处理即可）。

#### Scenario: 标签通配任意品阶
- **WHEN** 某 palette 键声明 `#gensokyou:ritual_stones` 且该位放置 3 级仪式石
- **THEN** 结构匹配成功

#### Scenario: 精确指定品阶
- **WHEN** 某 palette 键声明 `gensokyou:ritual_stone_2` 且该位放置 1 级仪式石
- **THEN** 结构匹配失败；放置 2 级仪式石则成功

#### Scenario: 等级取最高
- **WHEN** 匹配结构内同时存在 0 级、1 级仪式石与 2 级祭品台
- **THEN** 该次匹配推导的仪式等级为 2

#### Scenario: 现有仪式行为不变
- **WHEN** 以任意品阶方块按现有 8 个仪式 JSON 搭建结构
- **THEN** 全部照常成型（当前仪式无品阶门槛，等效 ≥0）

