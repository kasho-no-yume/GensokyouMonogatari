## 可重复验证

所有 Gradle 调用均通过 `tools/gradle_task.ps1`。

### 服务端游戏测试

首次运行前，将已同意的 `run/eula.txt` 复制到 `build/gametest-run/eula.txt`。

```powershell
.\tools\gradle_task.ps1 runGameTestServer --project-prop withoutJei=true -TimeoutSec 180
```

测试运行目录为 `build/gametest-run`，不会占用 `run/` 或 `run-test/`。`withoutJei` 省略 JEI 的运行时依赖；四项测试在无 JEI 环境全部通过，记录见 `build/agent-logs/20261121-165750-runGameTestServer.log`。

- `coreInteractionAndReload`：通过服务端原版交互入口检查主副手插核、空台、满台、非核心物品让位、双手潜行切档、取回与拆除掉落；将方块实体序列化、移除并重建，验证档位与核心剩余储灵。
- `oxidationRingAndCadence`：对角楼梯与铜门按轮转逐级加工；20 tick 至多处理一个目标；铜门两半只收一次费用；上蜡铜、已达上限铜、圈外及上下方铜不处理；余额不足、空转与取核后不处理。
- `freezingModesAndAffordableTarget`：0 阶核心驱动三档六种产物并精确扣费；切档不改变既有产物；含水台阶、流动水保持原状；三档余额 6,000 时跳过水源并处理岩浆源。
- `tierZeroFullCoreYield`：满载 50,000 灵力的 0 阶核心每 20 tick 处理一格；三档分别产出 6 格蓝冰（余 1,400）和 8 格哭泣的黑曜石（余 2,000），之后保留源格与余额。

流体测试显式清除夹具源格的待执行流体 tick，以隔离源格加工与原版流体扩散；没有修改装置运行时的流体规则。这些验证没有替代玩家对操作体验与经济性的主观试玩。

测试类位于 `testkit/`，测试结构位于 `data/gensokyou/structure/test/`，均由打包守卫禁止进入发布 jar。

### JEI 客户端查询

```powershell
.\tools\gradle_task.ps1 runClient --project-prop testSpiritTreatmentJei=true -TimeoutSec 180
```

独立运行目录为 `build/spirit-treatment-jei-run`，需预先将测试世界复制到其 `saves/world`。首次启动辅助功能提示会阻止快速进入世界，可在该目录的 `options.txt` 设置 `onboardAccessibility:false`。

仅设置上述测试属性时，开发用 JEI 插件才会对真实 JEI 运行时执行装置用途查询、铜产物与六种冻结产物的配方查询，并创建配方布局。成功日志为 `SPIRIT_TREATMENT_JEI PASS`，验证后自动关闭测试客户端。

2026-11-21 客户端验证通过，日志见 `build/agent-logs/20261121-170301-runClient.log`（17:04:09 的 PASS）。配方档位与耗灵由实际加工规则直接生成，服务端六种产物的扣费由游戏测试覆盖。

### 其他检查

- 最终全量 `build` 已结束：1,378 项测试，1 项失败。日志为 `build/agent-logs/20261121-170355-build.log`；编译和 `verifyNoTestContent` 均通过，失败位于并行工作区中的 `OyamatsumiOutputQuotaTest.stoneAndMineralQuotasScaleIndependently`，报告为 `expected: <40> but was: <140>`。当前源码可见 `assertEquals(40, 120 + 20)`，属于山神配额测试的算术断言，与本 change 的装置逻辑无关。本轮未修改该测试，任务 4.1 保留未完成，避免将全量构建标为通过。
- `openspec validate add-spirit-treatment-devices --strict` 通过。
- `python tools/lang_audit.py` 的 Java 字面量键检查通过；全局检查仍报告已有的 4 个书籍双语差异及 32 个其他源初造化配方中文键缺失，均非本 change 的装置键。
- 暂保留设计中的经济与处理节奏初值；人工主观试玩尚未完成。
