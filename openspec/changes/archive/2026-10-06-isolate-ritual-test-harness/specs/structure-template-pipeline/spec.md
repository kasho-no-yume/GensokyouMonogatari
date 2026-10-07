## MODIFIED Requirements

### Requirement: 产出原版结构模板 .nbt
`save_structure` SHALL 产出合法的原版结构模板文件（1.21.1 目录用**单数** structure/），
可直接被 `/place template gensokyou:<name>` 放置；文件 MUST 携带与 1.21.1 匹配的
DataVersion（3955）。产出 SHALL 默认落**开发目录**，MUST NOT 默认写入分发的
`src/main/resources/data/gensokyou/structure/`；只有被显式登记为「可达内容」的结构才拷入该目录。

#### Scenario: 默认产出到开发目录
- **WHEN** 在未登记为可达内容时执行 `save_structure`
- **THEN** 结构模板写入开发目录，`src/main/resources` 中不出现该 `.nbt`

#### Scenario: 游戏可放置（已登记内容）
- **WHEN** 在游戏内执行 `/place template gensokyou:<已登记建筑>`
- **THEN** 结构按设计原样落地，无方块缺失、无版本拒载报错

### Requirement: 单人存档免数据包分发
`save_structure` SHALL 把 `.nbt` 副本写入全部已存在单人存档的
`generated/gensokyou/structures/` 回退目录（存档回退用**复数** structures/，与数据包内单数相反）。
把 `gs_ritual_test` 数据包整树同步进存档 `datapacks/` MUST 为**显式开启**的可选行为，
默认 MUST NOT 同步；开启后的同步操作 SHALL 幂等且不创建新存档。

#### Scenario: 默认不向存档写测试包
- **WHEN** 编译完成后用户重进单人存档（未显式开启数据包同步）
- **THEN** `/place template gensokyou:<name>` 可用（`.nbt` 回退已写入），
  但存档 `datapacks/` 中不含 `gs_ritual_test`

#### Scenario: 显式开启才分发测试包
- **WHEN** 用户显式开启数据包同步后编译
- **THEN** `.nbt` 回退与测试数据包双路径均可用，无需手动拷贝文件
