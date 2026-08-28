# 新增仪式检查单

新增 / 删除 / 修改一个仪式时，按此清单逐项核对。
JEI 条目与祭品配方卡均由 rituals JSON 自动派生，无需改动任何集成代码。

## 1. 结构定义（必做）

- 文件：`src/main/resources/data/gensokyou/rituals/<名称>_circle.json`
- 格式（稀疏偏移 v3，参照 `summon_circle.json`）：
  - `palette`：字符 → 精确方块 / `#方块标签`
  - `levels[].blocks[]`：每项 `{ "key", "x", "y", "z" }` 为**相对锚点**的偏移
  - **只存规范四分之一**：off-axis 存 (|x|,|z|)，加载自动展开 (±x,±z)；
    轴上格子存北位 (0,d)，加载自动四方成套（含坐标互换的东/西位）
  - `anchorKey`：全文件唯一，其字符必须出现在 x=0,z=0（核心即原点）
- 对称违规在格式层面不可表达；展开冲突 / 锚点异常会被 loader **拒载**并日志报因
- 生效方式：游戏内匹配随 `/reload` 即时生效；单人模式下 JEI 同会话同步

## 2. 可选字段

- `"toggleable": true`：界面出现启动/停止按钮（事件型/可开关仪式）
- `"requirements"`：逐台祭品要求，每项：
  - `key` + `slot`：寻址该字符键格位集合中规范序（层↑、z↓、x→）第 slot 个祭品台
  - `item`：物品 id 或 #物品标签；`count` 默认 1
  - `consume`：`none`（仅门槛）/ `on_activate`（启动瞬间扣）/ `periodic`+`period`（周期扣，断供自动停机）
  - 有 requirements 的仪式自动获得 JEI 配方卡与 U 键反查

## 3. 行为与周边（按需）

- 仪式行为：`RitualBehaviors` 注册对应 behavior
  - `hasDirectInteraction()=true` 才会保留"空手右键核心"的直连交互；
    否则空手右键打开仪式 UI（启动按钮是唯一启动入口）
  - 启动收费等前置逻辑写在 `onStart(...)`（返回 FAIL 阻止启动）
  - 成型瞬间的结构替换扩展点是 `onFormed(...)`（当前为空实现占位）
- 合成配方、战利品表、创造标签页等
- 语言键：界面 `gui.gensokyou.ritual.*`、提示 `msg.gensokyou.*`、JEI 效果名 `jei.gensokyou.effect.<文件名>`

## 4. 游戏内采集工具

- `/gs_ritual_capture <名称> <半径> <高度>`（权限≥2）：以玩家脚下为中心捕获
- **仪式构造仗**（创造标签页）：左键设角点 A → 右键方块设角点 B 即捕获；
  潜行右键清除选择；区域内须恰有一个仪式核心作锚点
- 两者共用管线：输出稀疏骨架至 `logs/latest.log`，对称冲突与锚点异常随聊天回显；
  骨架人工核对后放入 rituals 目录入库

## 5. 已知限制

- dedicated server 远程客户端的 JEI 图鉴为空（数据在服务端 JVM，未做网络同步），
  远程客户端空手右键核心也不会弹出界面（本地无 pattern 数据）
- 结构视图为固定视口 + 拖拽/滚轮平移；多层级仪式在条目内以翻层控件查看各层（不拆分条目）
- palette 标签无有效成员时，该格显示屏障方块并提示标签名
- 稀疏格式不再支持"该位置必须是空气"的表达（缺席 = 不限制）

## 2b. 配方编写（ritual_recipes）

- 目录：`src/main/resources/data/gensokyou/ritual_recipes/<名称>.json`，一文件一条配方
- 字段：`pattern`（挂靠仪式 id）、`mode`（activation 启动型 / passive 持续型）、
  `minTier`（等级门槛，高等级=低等级超集）、`ingredients[]`（无序，物品或 #标签 ×count）、
  `result`（实物产物）与 `effect`（效果 id，解释权在行为）至少其一；可选 `spCost`
- 匹配语义：**严格等值**——台面内容必须恰好等于原料表，多余物品即不匹配；
  摆放顺序与台位无关
- 歧义规则：同仪式同模式原料表完全相同的两条配方，后者拒载
- 加工环已迁移为配方驱动（spirit_processing 旧目录作废）