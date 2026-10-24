# Validation — add-rumia-tier1-final-boss

日期：2026-10-24
状态：**全部 50 项已确认；可归档。**

## 自动化证据

| 项 | 命令/位置 | 结果 |
|---|---|---|
| 全量构建 | `.\tools\gradle_task.ps1 build`；`build/agent-logs/20261024-135834-build.log` | **BUILD SUCCESSFUL**（全量测试通过，2m10s） |
| 内容/清单测试 | `ProgramContentTest`、`ProgramManifestTest`、`LegacyManifestTest`、`BossCardLintTest`、`SpellCardThresholdTest`、`TrackRepeatTimingTest` | 通过（含在 build 内） |
| 召唤配方容量 | `SummonBossRecipeCapacityTest`（build 内）+ `python tools\audit_ritual_pedestal_recipe.py` | 通过；`audit ... ok` |
| 语言键 | `python tools\lang_audit.py` | 新增键零缺失；仅剩 31 条既有 `jei.gensokyou.recipe.zaohua_*` 基线缺失 |
| OpenSpec | `openspec validate add-rumia-tier1-final-boss --type change --strict` | **valid** |
| 表格 | `sheet/bossspellcard.xlsx` 第 4 页「露米娅」 | 4 行 + 汇总 + 考据；机械列标「待用户设计」 |

## 实机证据（用户确认，2026-10-24）

| 任务 | 确认内容 |
|---|---|
| 2.11 / 7.1 | 模型朝向、0.85 缩放、0.7×1.5 hitbox、idle/walk/cast、暗处轮廓；L1 四料召唤、扣料、光柱、生成正常 |
| 7.2 | 4 张卡按 1.00/0.75/0.50/0.25 立即切换；cast 与宣言演出可见；未崩 |
| 7.3 | 6 碎星、`nightfall_core` 保底、无整颗 `spellcard_star`、yen 范围、xp 300 正常 |
| 7.4 | `boss_residual_codex_rumia` 可合成；镇魂返照能识别 `gensokyou:rumia` |
| 7.5 | 真实 2/5 人与读档恢复通过；未重复装卡 |
| 7.6 | `#gensokyou:bosses` 标签生效；众生典籍不能收容露米娅（另由 `CodexOfBeingsItem.canCapture` 静态确认） |
| 7.7 | 视觉验证未导致 scale/hitbox/240s/7 hits/glowmask/底噪改动；按现值定稿 |

## 已知边界

- 露米娅 4 张卡当前仍是**最小占位程序**；正式分轨/参数/循环/演出由用户后续设计。本次验收只证明「接入、切卡、掉落、典籍、多人、读档」链路，不把占位程序当最终内容。
- `nomen_mask` → `rumia` 是 breaking change；旧实体/旧残魂典籍/旧 `hyakki:nomen` 会话不解析，不做 alias。release note 已记录。
- archive 中的傩面历史记录保留，未删除或重写。
