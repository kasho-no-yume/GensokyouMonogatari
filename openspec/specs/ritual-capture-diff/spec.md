# ritual-capture-diff Specification

## Purpose
TBD - created by archiving change ritual-wand-dev-editor. Update Purpose after archive.
## Requirements
### Requirement: diff 捕获以低级累积为不可动地基
捕获第 N 阶 SHALL 以该仪式 `cumulative(N-1)` 切片为**不可动地基**（N 为最低阶时地基=仅锚点格），对工作区内非空气方块重导出本阶 `adds` 草稿——完全替换既有本阶草稿，允许作者自由增删本阶自己的格位。捕获视野 SHALL 严格限于工作区 AABB，区外一切不参与判定。对称类归并、锚点唯一、朝向反推与对称校验 SHALL 复用现有 `RitualCapture` 管线。

#### Scenario: 本阶自由改
- **WHEN** 作者删掉了本阶自己新增的一个装饰格并在别处新增一个，捕获保存
- **THEN** 新草稿不含被删格、含新格，低级地基格位逐格不变

#### Scenario: 地基被碰即报违规
- **WHEN** 某低级累积切片声明的格位在工作区内被换成其他方块或被挖空（AIR/EXACT/TAG 谓词不满足）
- **THEN** 该格报"低级格被改动"违规并附坐标与来源层级，本阶草稿不产出该变更

### Requirement: 新增格 key 判定次序
工作区内不属于地基的新增格 SHALL 按序定 key：① 命中仪式既有 palette 某 key 的谓词（EXACT 同方块、或石/台属该标签）→ 复用该 key；② 仪式石/祭品台且无既中 key → 反导品阶标签 `#gensokyou:ritual_stones_N_plus` / `#ritual_pedestals_N_plus`（N=本阶号），特例：N=0 石用全量标签 `#gensokyou:ritual_stones`、1 阶新增台被迫用 `_2_plus`（标签体系缺 1 档）；③ 其余方块 → 分配新 EXACT key（A..Z 除 C）。
#### Scenario: 复用既有蓝石 key
- **WHEN** 新增格放置的方块与仪式中既有 EXACT key 所指方块相同
- **THEN** 草稿条目使用该既有 key，不膨胀新 key

#### Scenario: 三阶新环反导标签
- **WHEN** 作者在阶级 3 新增一圈仪式石（品阶 3 满块），仪式原无石标签 key
- **THEN** 条目以 `#gensokyou:ritual_stones_3_plus` 新 key 写入

### Requirement: 输出格式与落选语义
草稿 adds SHALL 以**规范四分之一位置式数组** `[key,x,y,z(,o)?]` 序列化（off-axis 取绝对值象限、轴上归并北位 `(0,d)`、SHALL NOT 出现 `(d,0)` 条目、无朝向条目省略第 5 位）；落盘前服务端 SHALL 用与 loader 相同的 `expandInto` 四重展开自检并与地基比对，任何相交或非法条目 SHALL 以内部错误拒绝产出而非写出坏文件。地基本身的格 SHALL NOT 重复出现在 adds。旧本阶格位在世界的消失属合法删除（不产条目、不报违规）；IGNORE 谓词格在世界状态不受捕获约束。

#### Scenario: 轴上格归并北位
- **WHEN** 工作区四向轴上各有一个同 key 新增格
- **THEN** 草稿仅一条 `(0,d)` 条目，展开自检覆盖四格

#### Scenario: 自检拦截坏补丁
- **WHEN** 产出的 adds 与地基展开后存在任意格位相交
- **THEN** 保存被拒、明确报内部冲突，不写任何文件

