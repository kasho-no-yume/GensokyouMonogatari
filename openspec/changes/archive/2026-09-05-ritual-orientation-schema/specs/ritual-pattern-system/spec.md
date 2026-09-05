# ritual-pattern-system Delta

## ADDED Requirements

### Requirement: 格位可选朝向常量
仪式方块条目 SHALL 支持可选第 5 位 `o`：单一扁平枚举的朝向常量（id 1-26：NORTH/EAST/SOUTH/WEST、`*_TOP` 上半楼梯、UP/DOWN、R0-R15 十六段旋转），书写接受 int id 或等价字符串名（不区分大小写）。每个常量自带 `BlockState` 属性需求与读写规则（探测顺序 `HORIZONTAL_FACING`→`FACING`→`AXIS`，加 `HALF`/`ROTATION_16`；R 系罗盘约定 R0=北顺时针，1.21.1 段值即北 0/东 4/南 8/西 12，恒等映射）。缺省（无第 5 位）时该格行为 SHALL 与 schema v3 逐位一致。四分之一展开时，每个派生格位的朝向常量 SHALL 按位置变换的同一复合（mirrorX / rot90 / 其组合）经全局 26 元置换表同步变换，使全量图案（位置+朝向）保持 4 旋转不变。匹配时，带朝向的格位 SHALL 以"常量-状态满足关系"校验：方块属性存在且值相容（轴属性双向等价：AXIS=Z 同时满足 NORTH 与 SOUTH；带 HALF 的方块 1-4 要求下半、5-8 要求上半）；匹配器旋转尝试 r 时期望常量 SHALL 先经 rot90^r 变换。

#### Scenario: 朝中心楼梯环
- **WHEN** 图案在轴位 `["T",0,0,2,1]`（核心正南，NORTH）声明朝中心的楼梯，加载展开
- **THEN** 四派生格朝向分别为 北/南/西/东，全部指向核心；按任意 90° 旋转搭建的实际结构均可匹配

#### Scenario: 朝向不符不匹配
- **WHEN** 图案某格要求 `5`（north_top），实际放置了朝北下半楼梯
- **THEN** 该格谓词不满足，结构不匹配

#### Scenario: 双解析等价
- **WHEN** 同一格分别写作 `["T",0,0,2,5]` 与 `["T",0,0,2,"north_top"]`
- **THEN** 加载结果完全一致

#### Scenario: 无朝向字段零回归
- **WHEN** 加载并匹配不含第 5 位的图案条目（含既有 7 个仪式）
- **THEN** 行为与 schema v3 完全一致

#### Scenario: 楼梯上下半独立
- **WHEN** 某格声明 `6`（east_top），镜像展开到对侧
- **THEN** 对侧格为 `8`（west_top）（TOP 分量不随水平镜像改变）

#### Scenario: 轴块双向等价
- **WHEN** 图案某格要求 `1`（NORTH），实际放置 `AXIS=z` 的原木
- **THEN** 该格匹配成功（南北向原木沿 Z 轴即满足）；放置 `AXIS=x` 原木则不匹配

## MODIFIED Requirements

### Requirement: 声明式结构定义
仪式结构 SHALL 以 JSON 定义于 `data/gensokyou/rituals/`：palette 谓词（精确方块/#标签）、按层级组织的方块偏移表——每项为**位置式数组** `["key",x,y,z,o?]`（相对锚点的稀疏偏移，仅存对称规范四分之一，加载期展开为全量；第 5 位为可选朝向常量）、全文件唯一的 anchorKey；经资源重载监听器加载，`/reload` 热生效。对象式条目（v3 格式）SHALL NOT 再被接受。

#### Scenario: 热加载
- **WHEN** 新增或修改 rituals JSON 后执行 /reload
- **THEN** 新结构定义立即可用于匹配，无需重启

#### Scenario: 四分之一展开
- **WHEN** 某层级仅声明轴位 `["S",0,0,1]` 与对角位 `["S",1,0,1]` 两个方块条目
- **THEN** 加载后该层级展开为核心四周 4 个与四角 4 个共 8 个方块位置

### Requirement: 加载期构造性校验
对称约定 SHALL 由存储格式构造性保证（四分之一展开）；loader SHALL 拒绝无法通过展开的文件：展开后出现重复格冲突、anchorKey 缺失/重复/不在原点，均 SHALL 拒载并在日志报明文件名与原因；带朝向的条目另 SHALL 满足：锚点格不带朝向、AIR/IGNORE 谓词格不带朝向、EXACT 方块满足所声明常量的属性需求、`o` 值可解析（id ∈ 1-26 或名在词表内）、条目为合法形状的位置式数组，违者拒载并报明格位与原因；合规文件 /reload 热生效不受影响。

#### Scenario: 冲突拒载
- **WHEN** 某 JSON 展开后同一偏移被两个不同字符声明
- **THEN** 该文件被拒绝加载，日志指出冲突位置

#### Scenario: 锚点异常拒载
- **WHEN** 某 JSON 的 anchorKey 字符缺失或出现在非原点偏移上
- **THEN** 该文件被拒绝加载，日志说明原因

#### Scenario: 非法朝向拒载
- **WHEN** 某条目第 5 位为 `99`（超出常量表）
- **THEN** 该文件被拒绝加载，日志指出格位与非法值

#### Scenario: 墙类声明朝向拒载
- **WHEN** 某 EXACT 条目指向 `minecraft:stone_wall` 并声明任意朝向常量
- **THEN** 该文件被拒绝加载，日志指出该方块不满足常量的属性需求

#### Scenario: 旧对象格式拒载
- **WHEN** 某条目仍写作 v3 对象 `{"key":"S","x":0,"y":0,"z":1}`
- **THEN** 该文件被拒绝加载，日志指出条目须为数组格式

#### Scenario: 合规热载
- **WHEN** 修改合规 JSON 后执行 /reload
- **THEN** 新定义立即生效，无告警

### Requirement: 采集命令
SHALL 提供开发命令 `/gs_ritual_capture <名称> <半径> <高度>`（权限≥2）：以玩家脚下为锚扫描区域，自动生成稀疏偏移格式 JSON 骨架输出至日志并回显提示；产出 SHALL 随附对称归并与锚点唯一性校验结果回显；具备朝向属性（`HORIZONTAL_FACING`/`FACING`/`HALF`/`HORIZONTAL_ROTATION`）的方块 SHALL 反推为朝向常量输出在骨架条目第 5 位（int），且对称类归并 SHALL 将朝向一致性纳入违规判定。

#### Scenario: 采集产出
- **WHEN** 在手工搭建的结构中心执行采集命令
- **THEN** 日志输出含 palette 与 blocks 的完整 JSON 骨架

#### Scenario: 采集校验提示
- **WHEN** 采集区域存在对称类冲突或核心数量异常
- **THEN** 回显明确指出违规项，便于修正后再入库

#### Scenario: 采集带朝向楼梯
- **WHEN** 结构四方位为朝向对称的楼梯
- **THEN** 骨架四分之一条目带第 5 位朝向常量，且朝向破坏对称时回显违规
