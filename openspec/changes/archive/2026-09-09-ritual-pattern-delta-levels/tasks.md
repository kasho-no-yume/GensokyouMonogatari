# Tasks: ritual-pattern-delta-levels

## 1. 校验器先行（离线语义与迁移工具）

- [x] 1.1 `tools/validate_ritual_pattern.py` 支持 v5 增量解析（`{level, adds}`，四重展开后逐级累积），累积语义校验替代子集比对：增量与低级累积切片相交即 ERROR（含同 key 重复）；level 号重复 ERROR
- [x] 1.2 锚点校验改为全文件级：anchorKey 全文件恰一次、(0,0,0)、y=0、且位于最低级增量；palette 缺锚点键仍 ERROR
- [x] 1.3 新增 `--convert-v4`：v4 快照逐级集合差拆分为 v5 增量，落盘前断言"v5 逐级累积 == 原 v4 快照"，并输出迁移前后每级展开格数与 key 分布比对表
- [x] 1.4 `--test-out` 测试包生成改为基于累积切片；`only_additions` 路径直接取增量条目（产物行为与迁移前一致）
- [x] 1.5 对现仓库 7 个 pattern 跑 `--convert-v4` 完成迁移；全量校验零 ERROR/WARN 并留档比对表

## 2. Java loader（程序侧）

- [x] 2.1 动工前读 neoforge-1211-dev skill；`RitualPatternLoader` 解析改为 v5：逐级 `adds` → `expandInto` → 累积合并，累积冲突/level 重复即拒载并报明格位与层级
- [x] 2.2 v4 兼容移除：条目对象式（v3）或层级用 `blocks` 字段（v4）均拒载，日志给出迁移指引；`RitualPattern`/`LevelSlice` 数据结构不变（仍存全量切片）
- [x] 2.3 锚点校验补齐：恰一次、(0,0,0)、y=0、仅最低级增量；specificity 取累积切片，逻辑不变
- [x] 2.4 编译通过；`/gs_ritual_capture`/构造仗单层骨架输出不受影响（回归确认）

## 3. 实机验证

- [x] 3.1 重建 `run/world/datapacks/gs_ritual_test`，交用户运行 `tools/_run_ritual_test.ps1`，确认 T1..T5/NEG/SP 全部按原期望输出
- [x] 3.2 游戏内抽查任一多级仪式（如 generator_circle）：低级成型、扩建升高级、拆除降级行为与迁移前一致

> **3.1/3.2 实机结果留档（2026-09-09）**：T1_OK / SP_OK:cap1 / T2_OK / T3_OK / SP_OK:cap2 / NEG_OK / ALL_DONE 全部按原期望输出，迁移前后行为一致（回归通过）。
> **存量发现（非本变更引入，另立 change 处理）**：
> 1. **T5_OK 未出现**——a5 的 level 5 建筑未成型为 5 级（SP_OK:cap2 走低层级配方发电）。测试包序列与 pattern 切片均与迁移前逐字节一致，证明与迁移无关。
> 2. 用户观感：发电机仪式设计疑似不符合现行设计规则，待专项排查。

## 4. 工具链与文档同步

- [x] 4.1 `tools/gen_generator_circle.py` 输出段改为 v5 增量序列化（顺带移除 v3 对象格式遗留），重跑产物与仓库迁移结果一致
- [x] 4.2 `tools/gen_catalog.py` 适配 v5 输入（统计口径不变：累积后展开数），重新生成 `astra-design/PATTERNS.md`
- [x] 4.3 更新 ritual-design SKILL.md：§1 格式说明改 v5 增量（core 仅最低级声明、删除"全量快照"表述）、§4 不变量 1 与 §7 陷阱同步；其余（三层分离、选材、工作流）不动
