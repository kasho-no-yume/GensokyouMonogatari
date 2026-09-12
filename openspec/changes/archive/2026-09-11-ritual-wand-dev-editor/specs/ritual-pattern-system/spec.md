## REMOVED Requirements

### Requirement: 构造仗框选采集
**Reason**: 构造杖重构为开发者编辑杖，"两点框选 → 骨架 JSON 落盘人肉入库"的捕获方式被工作区 diff 捕获（`ritual-capture-diff`）取代——裸骨架捕获会把 TAG 谓词退化为 EXACT、丢失 AIR 格，且产出仍需人肉搬运，与编辑闭环冲突。
**Migration**: 编辑杖锚定核心 + 配置工作区后执行 diff 捕获即得本阶 adds 补丁；仍需一次性全量骨架的场景走保留原样的 `/gs_ritual_capture` 命令（开发命令，权限 ≥2）。
