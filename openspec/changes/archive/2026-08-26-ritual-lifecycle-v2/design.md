# Design: ritual-lifecycle-v2

## Context

阶段 B 框架现状：`RitualPattern`（palette + ASCII 分层切片）→ `RitualMatcher.matchAt`（8 变换暴力枚举、逐级各自找锚）→ 核心 BE 每 20tick 重扫 + behavior 分发；右键经 PASS 让位手中物品（召唤催化剂依赖此路径）；祭品台仅有单槽存取，仅加工环散装消费；结界引爆等开关状态以杂散字段散落在核心 BE。

**本变更是框架级交付**：只实现基类/模板能力（定义约束、祭品契约、生命周期、界面、图鉴、构造工具）。现有七个仪式均为占位，后续会有大改重做——因此对它们只做"通过新校验、代码不报错"的最小修补，不做任何玩法翻新。

沉浸工程的多方块成型机制作为将来大型仪式外观的思想参照，本变更仅预留其挂载点（替换函数空实现），不引入任何多方块替换实体。

## Goals / Non-Goals

**Goals:** 对称性与单核心成为可校验硬约定；声明式祭品要求（门槛/消耗/周期供给）的框架层；统一生命周期与 enabled 门控；核心右键 UI；JEI 通用配方卡派生；构造仗框选采集；成型替换扩展点（空实现）。
**Non-Goals:** 具体仪式的内容翻新（召唤祭品化、催化剂终局、发电机供能语义等均移交后续内容变更）；大型 tileblock 外观的实际实现（无模型资产，仅留空函数）；周期供给的缓冲宽限；其余占位仪式的 requirements 批量补配。

## Decisions

### D1 稀疏偏移存储与对称约定

存储弃用 ASCII 切片，改为**稀疏偏移表**：每层级一个 blocks 数组，逐项声明 `{k, x, y, z}`（k=palette 字符，xyz=相对锚点偏移）。空气格不再占存储（缺席即不限制）。

对称约定内建于格式：

```
只存规范四分之一：
  off-axis (x≠0,z≠0) → 存 (|x|,|z|)，加载展开 (±x,y,±z) 四份
  axis     (0,d)     → 存 (0,d)，    加展展开 (0,±d),(±d,0) 四方成套（坐标互换）
  原点 (0,0,0) = 锚点核心本体
```

对称违规在格式层面**不可表达**；加载期校验因此退化为两条：展开后无重复格冲突、anchorKey 全文件唯一且位于原点。旧网格校验器删除。

### D2 匹配器收敛：全局锚点 + 4 旋转

- 删除镜像变换（对称约定下镜像冗余），朝向枚举 8 → 4，匹配成本减半
- 匹配直接遍历展开后的方块偏移表，无网格游标；展开条目在解析期按 (y,z,x) 排序——该顺序即 **slot 规范序**（层自下而上、z 自北向南、x 自西向东），旋转不改变序列，保证祭品台 slot 寻址旋转不变
- 多层级仍自顶向下返回最高可达等级

### D3 RitualPattern schema v3

```jsonc
{
  "id": "...", "anchorKey": "C",
  "palette": { "C": "gensokyou:ritual_core", "S": "#gensokyou:ritual_stones" },
  "levels": [
    { "level": 1, "blocks": [
      { "k": "C", "x": 0, "y": 0, "z": 0 },
      { "k": "S", "x": 0, "y": 0, "z": 1 },
      { "k": "S", "x": 1, "y": 0, "z": 1 }
    ] }
  ],
  "requirements": [
    { "key": "P",            // 字符键，配合 slot 寻址单个祭品台
      "slot": 0,             // 该键位集合在规范序下的序号
      "item": "#tag|id", "count": 1,
      "consume": "none|on_activate|periodic", "period": 1200 }
  ],
  "toggleable": true         // UI 是否渲染启停按钮
}
```

新字段全部带默认值（requirements=[], toggleable=false）。slot 规范序同时是 UI 清单与 JEI 配方卡的展示顺序。不引入 collapse/model 字段——替换外观属后续变更，届时随其实现加字段。

### D4 祭品门槛层（独立于结构匹配）

- 匹配器保持纯方块判定；新增 `RitualOfferings.check(match, level)` 读各台 `RitualPedestalBlockEntity.getHeld()` 对照 requirements
- 三种 consume 语义：
  - `none`：常备门槛，只校验不扣（启动条件之一）
  - `on_activate`：启动瞬间按 count 扣减
  - `periodic`：运行期每 period 扣一次；**断供即自动停机**（enabled=false，玩家需重新启动）——不做缓冲宽限，语义简单
- UI 核对清单逐 slot 显示 ✓/✗ 及所缺物品

### D5 生命周期状态机（单轨制，事件型同轨）

```
搭好结构 ──► 重扫命中 = 成型(IDLE) ── 调用替换扩展点(空实现)
                │
          空手右键核心 → 打开 UI → 启动按钮（唯一启动入口，
                │                     事件型与设施型一律如此）
                ▼
        校验门槛 → 扣除 on_activate 消耗 → RUNNING(enabled=true)
                ▼
        behavior tick（统一 gate 于 enabled）
                │ 停止按钮 / periodic 断供 / 重扫失效
                ▼
        停止(enabled=false)；重扫失效另触发既有 onStructureLost 清理
```

- **所有仪式同一套判据与交互**：每秒重扫维持，失效即停机并清理；不存在事件型/设施型分叉路径；事件结束后结构原样保留，补供可复用
- 所有 behavior 的 serverTick 入口统一 gate 于 `core.isEnabled()`；`barrierActivated` 收编为 enabled 态（存档兼容读旧键）
- 召唤催化剂等持物交互路径**保持现状不动**——召唤流程的祭品化翻新属后续内容变更，届时以本框架的 requirements + UI 启动重新实现

### D6 成型替换扩展点（空实现）

- `RitualBehavior` 接口新增 `onFormed(level, corePos, match)` 默认回调，在重扫由未命中变为命中的瞬间调用；基类默认方法为空体
- 本变更不引入新方块、BE 或渲染；函数签名携带完整 `RitualMatch`，将来填充"替换为大型 tileblock"时无需改动调用方与生命周期

### D7 核心右键 UI 与网络

- 交互契约：**成型仪式右键核心一律打开 UI——无论空手或持物，无例外**；未成型给出提示后 PASS（物品链照旧）；**潜行右键保留原链路**（贴放方块 / 旧行为直连），作为放置类操作的标准逃生口
- UI 内容可由各仪式重写：`RitualBehavior.uiActions/onUiAction` 允许行为注入自定义操作按钮（id≥10，网络层加 100 偏移路由）；电容存取、淬炼已迁入各自界面；中继绑定依赖视线目标，暂留潜行链路待后续重做
- 采用**无槽位 Menu**（注册 MenuType）：复用 vanilla 菜单生命周期与服务端权威按钮通道；结构化信息（清单/状态/操作）经一次性 `RitualInfoPayload` 下行，启停与自定义操作经 `RitualTogglePayload` 上行——异构信息不走 ContainerData
- 全 mod 首个 Menu/Screen，建立 `client/screen` 包约定

### D8 JEI 配方卡（通用派生）

- requirements 非空的仪式各派生一张配方卡：输入区逐 slot 摆所需物品 ×count，输出区为效果名语言键文本占位
- 卡片为纯数据派生的通用模板功能，不绑定任何具体仪式；无 requirements 的仪式不出卡，既有结构条目与催化剂查找入口零改动

### D9 占位仪式最小合规修补

- 七个 rituals JSON 全部迁移为稀疏偏移格式（存储格式更换的连带，语义等价转换）
- processing / relay / tempering 三环的轴上祭品台原仅 N/S 或 E/W 成对，违反"轴上四方成套"约定——迁移时补齐四方位（占地足迹变化：加工环 4 台、中继十字形、淬炼环 4 台 + 四角仪式石以区分加工环）
- barrier_break_circle 补 `"toggleable": true`（生命周期收编的连带）
- 不给任何占位仪式补配 requirements、不做 summon 翻新——它们只是让校验器与生命周期跑通的最小样本

### D10 仪式构造仗（框选采集）

- 新物品 `ritual_wand`，服务端按玩家记录两角点：左键方块设角 A、右键方块设角 B；潜行右键清除选择
- 两点确立后走采集管线：扫描 AABB 全部方格 → 方块状态聚类自动分配字符键（air→`-`）→ 按 Y 分层生成 slices → 区域内恰一个祭仪核心定为 anchorKey=C → 调用 R1~R3 校验器 → JSON 输出日志并向玩家回显结果摘要
- 输出仍为文本骨架（手动入库到 `data/gensokyou/rituals/`），不自动写盘——服务端资源目录只读且热载管线已覆盖验证闭环
- 输出仍为文本骨架（稀疏偏移格式，手动入库到 `data/gensokyou/rituals/`），不自动写盘——服务端资源目录只读且热载管线已覆盖验证闭环
- 捕获归并：全量扫描 → 方块按对称类归并（同类不同字符即违规，回显明确原因）→ 输出规范四分之一
- `/gs_ritual_capture` 命令保留，与构造仗共用同一管线；物品本身即使用门槛，不加权限检查

## Risks / Trade-offs

- [删镜像后若遗漏不对称旧 JSON] → 展开冲突/锚点唯一性拒载兜底，七个 JSON 随本变更迁移，启动日志立即可见
- [旧核心 BE 杂散字段的存档兼容] → loadAdditional 兼容读旧键（barrierActivated 等），一次性收敛
- [断供即停机偏严厉] → 语义最简且与 UI 启停模型自洽；宽限机制留待实际体感反馈
- [构造仗大范围框选的性能] → 半径沿用命令上限语义并在交互时限制 AABB 尺寸上限，超限回显拒绝
- [替换钩子将来填充时的语义待定] → 成型时 vs 启动时替换、是否需要数据开关，留给后续变更决策；本变更只保证调用点稳定
- [dedicated server 远程客户端 JEI 为空] → 沿用既有已知限制，不在本变更解决
- [phase-b 已完成待归档] → 发电机 JSON 最小修正与其归档顺序协调，归档说明附注

## Migration Plan

schema v2 新字段全默认值 → 旧文件仅需过对称校验；generator_circle 最小修正随本变更落地。服务端 /reload 即生效；无独立回滚面，git revert 单变更即可。

## Open Questions

- 构造仗输出是否附剪贴板复制快捷方式（纯体验增强，可后补）
