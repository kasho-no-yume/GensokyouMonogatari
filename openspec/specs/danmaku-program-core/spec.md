# danmaku-program-core Specification

## Purpose
TBD - created by archiving change add-danmaku-program-core. Update Purpose after archive.
## Requirements
### Requirement: 程序编译与扩展接口
系统 SHALL 提供类型化节点、句柄、四种时间域和稳定定义hash；时间组合必须支持多拍Once、Loop及Stream。通道冲突、无界派生与依赖环 MUST 在编译期被拒绝，编码必须有界。内容及旧后端性能基线 SHALL 保存以供后续比较。

#### Scenario: 偏移子拍全部运行
- **WHEN** 周期60包含0/20/40三拍
- **THEN** 三拍在每周期均被调度，不只保留0相位

#### Scenario: 重复声明结果稳定
- **WHEN** 相同定义和配置被编译两次
- **THEN** 产生相同节点身份和hash，非法同通道写入报告明确错误

