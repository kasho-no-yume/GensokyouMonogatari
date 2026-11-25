# NPC 头顶标记与博丽灵梦

博丽灵梦（`gensokyou:reimu`）在幻想乡维度的博丽神社庭院出生，脚下坐标为 `(3070.5, 96, 128.5)`。她在出生锚点左右 8 格、前后 9 格的庭院空地内寻路散步，对话期间停步，其余保护、恢复、持久化和交互逻辑继承 `TouhouNpcEntity`。已有神社的旧存档会在神社区块加载时补上灵梦，每个世界只自动生成一次。

模型原稿为 `bbmodel/hakurei_reimu.bbmodel`，导出命令为 `python tools/export_reimu_npc.py`；使用 `--check` 可核对已提交的模型、动画和贴图。

所有 `TouhouNpcEntity` 均支持以下标记，默认 `NONE`：

| Java 状态 | NBT `QuestMarker` | 显示 |
| --- | --- | --- |
| `NpcQuestMarker.NONE` | `none` | 无 |
| `NpcQuestMarker.EXCLAMATION` | `exclamation` | 黄色 `!` |
| `NpcQuestMarker.QUESTION` | `question` | 黄色 `?` |

未来任务系统在服务端调用 `npc.setQuestMarker(NpcQuestMarker.EXCLAMATION)` 等方法即可切换，用 `npc.getQuestMarker()` 读取。标记通过实体数据同步给所有追踪玩家，随 NBT 保存，NPC 恢复时保留；当前状态为每个 NPC 共用，尚未按玩家任务进度区分。

可在游戏中测试：

```mcfunction
/execute in gensokyou:gensokyo run data merge entity @e[type=gensokyou:reimu,limit=1,sort=nearest] {QuestMarker:"exclamation"}
/execute in gensokyou:gensokyo run data merge entity @e[type=gensokyou:reimu,limit=1,sort=nearest] {QuestMarker:"question"}
/execute in gensokyou:gensokyo run data merge entity @e[type=gensokyou:reimu,limit=1,sort=nearest] {QuestMarker:"none"}
```

标记在头顶名字上方朝向镜头显示，最远 64 格，使用黄色自发光文字并遵循场景遮挡。任务尚未接入，灵梦初始不显示标记。

开发验证：`bash tools/gradle_task.sh runGameTestServer` 检查 NPC 保护、恢复和标记同步/读档；`bash tools/gradle_task.sh runNpcProbe` 在隔离的 `build/npc-probe` 世界中检查实际幻想乡神社的唯一出生与庭院散步，结果写入该目录的 `npc-probe-result.txt`。
