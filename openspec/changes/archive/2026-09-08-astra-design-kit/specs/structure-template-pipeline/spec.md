# 结构模板管道（structure-template-pipeline）规范增量

## ADDED Requirements

### Requirement: 高密度生成 helper 与编译入口
仓库 SHALL 提供 `tools/struct_compile.py`：
- 提供 put/slab/pillar 等 helper，Astra 的建筑 gen 脚本 import 这些 helper 构建坐标字典
  （方块 id → (x,y,z) 列表），禁止手写逐格坐标展开；
- 提供 `save_structure(name, cells)` 编译入口，一次调用同时产出结构模板与测试数据包函数。

#### Scenario: helper 冲突自检
- **WHEN** gen 脚本对同一坐标重复放置不同方块
- **THEN** helper 立即以单行错误定位该冲突并终止

#### Scenario: 只写 helper 不写裸坐标
- **WHEN** 审查一份建筑 gen 脚本
- **THEN** 其逐格坐标均经由 helper 产生（环/盘/柱由参数展开），无手工展开的坐标长列表

### Requirement: 产出原版结构模板 .nbt
`save_structure` SHALL 产出合法的原版结构模板文件
（`src/main/resources/data/gensokyou/structures/<name>.nbt`），可直接被
`/place template gensokyou:<name>` 放置；文件 MUST 携带与 1.21.1 匹配的 DataVersion。

#### Scenario: 游戏可放置
- **WHEN** 在游戏内执行 `/place template gensokyou:<已编译建筑>`
- **THEN** 结构按设计原样落地，无方块缺失、无版本拒载报错

### Requirement: 游戏内预览（setblock 测试包）
编译 SHALL 同时在 `gs_ritual_test` 测试数据包内生成 setblock mcfunction，
用户运行 `/function gs:building/<name>` 即可在测试区实地查看建筑，作为唯一的预览手段。

#### Scenario: 无预览图
- **WHEN** 查看一次建筑交付的全部产物
- **THEN** 不存在任何预览 PNG；美观评审通过游戏内 `/function` 实地完成

### Requirement: 校验器错误输出单行化
`tools/validate_ritual_pattern.py` 的错误输出 SHALL 为单行可判格式
（`ERROR <定位> <原因>`，如 `ERROR L3 (0,1,0): 与 capacitor_circle L1 冲突`），
MUST NOT 输出多行堆栈；其 `--render` 预览与 `KEY_COLORS` 调色逻辑 SHALL 移除。

#### Scenario: 单行错误
- **WHEN** 校验器发现 pattern 冲突
- **THEN** 输出恰好一行 `ERROR ...` 摘要，可直接被 AI 消费

#### Scenario: 预览逻辑移除
- **WHEN** 查看校验器命令行帮助
- **THEN** 不再存在 `--render` 选项，源码中不存在 KEY_COLORS
