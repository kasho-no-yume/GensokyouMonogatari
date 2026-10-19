# 第0节作者入口交接（2026-10-18）

## 作者文档可读性修订

按低经验agent阅读反馈重写`docs/danmaku-program-authoring.md`：能力边界前置、Targets/Group区分、全部示例参数/标签/种子/坐标说明、完整loop例子及两种TailPolicy、伤害/方向/隐藏分节、派生触发/失败语义、clearance与limits入口、术语就地解释、真实stats和REJECT诊断表。author_hidden明确总长441tick约22.05秒，其中预警2秒、成员展示20秒。另核对`.laser`第二布尔实际是piercesBlocks，文档明确false裁剪。仅修改文档/skill，不修改API或迁移内容；未为本次文档修订重跑游戏构建。

## 远程恢复显示策略更新

用户明确接受轻微拉回优于闪烁。ProgramClock初始化后不再因不可信而返回不可渲染：保留既有至多2tick小误差holdover，随后冻结最后时间；收到下一独立权威观测允许重锚校正。严重积压与陈旧映射同样冻结、不无限外推；尚无初始观测仍不可渲染。End/Release等权威删除仍由副本立即执行。该决定覆盖阶段5“严重积压隐藏”和旧短grace耗尽后整批隐藏的策略，不宣称任意网络中断无停顿。

ProgramClockTest增加心跳间隔恢复期间逐帧不空白/冻结、长期陈旧不外推；ProgramLatencyMatrixTest检查冻结中End清场；既有延迟矩阵/重追踪/晚包不复活继续回归。远程实机效果待用户复验。

证据：`20261018-121219-test.log`时钟/网络/延迟定向通过；`20261018-121323-build.log`完整build通过；`20261018-121619-test.log`冻结期间End补充回归通过。strict/diff通过。

本轮只实施作者入口，不迁移符卡、不接现有实体、不归档change。原1.1–1.8全部保持未完成。阶段5已验收口径不变，不新增M2或真实多人矩阵门槛。

## 实现地图

- `program/author/StandardProgramOperations`：标准codec/操作注册/服务端执行器，客户端默认注册不再依赖预览类。
- `CardProgram`：具名时间线、所属句柄、创建时刻校验、固定目标槽、有限连发、Once/Loop、标准释放/重瞄/控制；框架分配ID。
- `MemberProgram`：显式attack/decoration、必选方向、激光外观与碰撞一致性、递归保守派生需求；预算独立按limits校验。
- `AuthoringTemplates`：齐射、复合花阵、移动母弹两层派生；新增三条author预览命令。
- `BossProgramController` / `gameplay/BossProgramHost`：同维度共享session、多owner战斗、独立底噪、切卡与结束、移动锁；未接现有BOSS。实际实体血量/HUD/加载及后端选择仍属第1节。
- `gameplay/ProgramMotionBounds`：标准路径/变换/派生树与后续释放/重瞄全寿命保守包络，无内容名称分支；旧PreviewBounds仅兼容转发。按不可变hash缓存分析。
- `ProgramVisuals`：具名样式注册、已注册模型选择、RGB颜色；renderer消费注册贴图。复杂新增几何仍需扩展；未把旧profile全部辉光/变色字段声称为已支持。
- `ProgramSession/ServerControlOperations`：END_CARD在同一权威边界撤销对应非background实例及未来调度，避免后续节点复活发射。

## 文档与验证

入口：[实际作者指南](../../../../docs/danmaku-program-authoring.md)，boss skill首页已更新并区分旧后端规则。`ProgramAuthoringTest`覆盖攻击/装饰、方向、数量、自动派生、预算/样式/句柄错误、网络往返与轨迹、预留未来释放包络、多owner/底噪隔离、循环重锚、END_CARD撤调度、移动锁。

最终完整wrapper build通过：`build/agent-logs/20261018-112113-build.log`（2m36s，134 suites / 1061 tests / 0 failures / 0 errors；作者测试7项）。OpenSpec strict和diff检查通过。lang_audit实际执行失败于31个既有`jei.gensokyou.recipe.zaohua_*`数据派生翻译键，本轮未修改语言资源、配方或审计脚本，也未新增语言键；不宣称审计全绿。

本轮未运行实机：模板入口可用，视觉可读性仍应在内容开发时实测；多战斗隔离为真实内存session自动化证据，不冒充实机多BOSS验收。性能矩阵沿用阶段5已有边界。保守包络可能较大，自定义控制必须扩展通用规则或显式扩大包络；普通作者不手写兴趣管理。

## 隐藏态补充

用户实机反馈后的修正优先于下文历史统一96/255口径：run/logs/latest.log确认第二次球批次被clearance拒绝；测试模板改clearance(0)，新增三维star_prism对照。单人display-error恢复导致全批隐藏，集成服客户端改用同进程ServerLevel真实gameTime，不走独立包到达时钟拟合；远程时钟恢复契约保持。球/模型本体按profile.hiddenAlpha降alpha且保留RGB；激光复用旧实体0.18并避免RGB重复乘alpha。共享DanmakuHiddenStyle供旧实体和新程序引用。任意外部实体模型并未自动接入程序。

反馈修正完整build `20261018-115820-build.log`通过；附加自定义hiddenAlpha=48模型回归 `20261018-120342-test.log`通过。首次附加测试与完整构建结果目录争用失败，待前者退出后重跑通过。修正效果尚未实机复验。

修正VisibilityCycle曾把隐藏弹完全剔除的问题。普通弹与激光隐藏时保持drawable，alpha和brightness乘96/255；激光束、端点和附属法阵共用投影，降低加法RGB与alpha。服务端隐藏阶段不调用CollisionSink，实体与方块检测均跳过；正常阶段恢复且路径/寿命连续。MemberProgram.hiddenCycle提供周期入口，Fade与伤害门仍独立。未变更wire record，不迁移现有卡。新增ProgramAuthoringTest双类型正常→隐藏→正常网络与碰撞回归；实际透明度观感本轮未实机确认。

补充构建：`20261018-113350-build.log`完整通过（134 suites / 1062 tests / 0 failures / 0 errors，作者测试8项），strict和diff检查通过。
