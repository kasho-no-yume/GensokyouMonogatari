# Tasks: ritual-lifecycle-v2

## 1. 定义层（schema v2 + 校验器）

- [x] 1.1 RitualPattern 解析扩展：`requirements`（key+slot/item/count/consume/period）与 `toggleable` 字段，全部带默认值
- [x] 1.2 R1~R3 校验器独立组件（双轴镜像 / 轴上四方成套 / 单核心居中），接入 loader：违规拒载并日志报明文件与原因
- [x] 1.3 占位仪式迁移：七个 JSON 转稀疏偏移格式；processing/relay/tempering 补齐四方成套；barrier 开 toggleable（design D9 已同步）

## 2. 匹配器收敛

- [x] 2.1 删除镜像变换（8→4 旋转）；锚点改为全局唯一定位、各层相对偏移校验
- [x] 2.2 既有占位仪式回归：各环在四朝向搭建均可识别、行为不回退

## 3. 祭品门槛层（框架能力）

- [x] 3.1 `RitualOfferings.check`：按规范序（z,x,y）slot 寻址祭品台并核对要求
- [x] 3.2 消耗语义落地：on_activate 启动瞬间足额扣除（不足拒启）；periodic 周期扣减、断供自动停机

## 4. 生命周期状态机（框架能力）

- [x] 4.1 核心 BE 增加 enabled 态（持久化 + 同步），兼容读旧 barrierActivated 键
- [x] 4.2 全部 behavior 的 serverTick 统一 gate 于 enabled；重扫失效即自动停机 + 触发既有失效清理
- [x] 4.3 新增 `onFormed` 替换扩展点（空实现）并在命中瞬间调用；催化剂等持物路径保持现状

## 5. 核心右键 UI（框架能力）

- [x] 5.1 注册无槽位 MenuType；空手右键成型核心开界面、未成型 PASS、持物走行为分发
- [x] 5.2 `RitualInfoPayload` 下行与 Screen 渲染：仪式名/层级/运行状态/概要数值/逐 slot 供品清单（✓/✗）
- [x] 5.3 `RitualTogglePayload` 上行与服务端权威启停（toggleable 门控按钮显隐、失败原因回显）

## 6. JEI 配方卡（通用派生）

- [x] 6.1 requirements → 配方卡 category：输入区逐 slot 物品×数量、输出区效果名语言键；无要求仪式不出卡
- [ ] 6.2 验证既有结构条目与催化剂查找入口零回归

## 7. 仪式构造仗

- [x] 7.1 物品注册 + 双角点选择状态（左键设 A / 右键设 B / 潜行右键清除）与 AABB 尺寸上限校验
- [x] 7.2 捕获管线：AABB 全方格扫描 → 字符聚类 → 分层 slices → 区域唯一样本核心定锚 → 输出 rituals JSON 骨架至日志并回显
- [x] 7.3 接入 R1~R3 校验器回显结果；创造标签与语言条目

## 8. 收尾

- [x] 8.1 语言条目补齐（界面文案/按钮/提示/效果名）zh_cn 与 en_us
- [x] 8.2 重写 docs/new-ritual-checklist.md：对称规则、requirements 格式、slot 规范序、构造仗使用流程、替换钩子说明
- [x] 8.3 gradlew build 通过 + 游戏内验证：违规 JSON 拒载告警；任一环的 UI 启停全流程；构造仗框选现有环→输出骨架→入库可匹配；JEI 条目与卡片共存正常
