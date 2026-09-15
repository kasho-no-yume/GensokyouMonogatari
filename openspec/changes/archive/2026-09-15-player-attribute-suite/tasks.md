# Tasks: player-attribute-suite

## 1. 注册表与容器

- [x] 1.1 定义 `AttributeKey` 注册表（15 键：域/上限/可改写标记/配置基项/淬炼放大声明），表外键写入被拒
- [x] 1.2 注册 `player_attributes` 附件（Codec 持久层 + transient 临时改写层，copyOnDeath；旧档缺附件=空容器）
- [x] 1.3 实现分层结算服务 `finalValue(key, player)`：(基准+Σ平值)×(1+Σ百分比)×Π乘区，sourceId 分组，百分比封顶；灵力强度键合并读取 `spirit_damage` 旧字段（单一事实来源，不双写）
- [x] 1.4 全部基准值/初始值/硬上限（擦弹、减免全局、CDR、韧性、强效延长）与汲取参数（转化率/每秒上限/总开关）入 `GensokyouConfig`

## 2. 消费点接线

- [x] 2.1 原版桥：生命增幅→`minecraft:max_health`、移速→`minecraft:movement_speed`，固定 modifier id 属性变更后整体重算（幂等）
- [x] 2.2 `DamageEventHandler` 重构为管道：mu_power 顺序不变；玩家+danmaku 新增 擦弹 roll→(减免×护盾) 乘算并全局封顶→抵抗减至可为 0
- [x] 2.3 暴击：`WeaponFiring` 发射时 roll，系数写弹幕 NBT，命中结算进 `critMult` 乘区（命中不重 roll）
- [x] 2.4 灵力汲取：命中后按伤害×转化率回灵，周期账本限速、满池截断、开关=0 时全链路失效
- [x] 2.5 CDR：`SkillStateData` 施放写冷却处折减 `base×(1−CDR)`，中途属性变化不回溯
- [x] 2.6 效果时长缩放：MobEffect 施加点折算正/负面时长（强效延长/韧性），预留变身 T 秒消费入口

## 3. 调试与验证

- [x] 3.1 `/gs_attributes` 命令：dump 15 键最终值与来源分解（基准/加区/乘区/封顶标记）
- [x] 3.2 回归用例：danmaku-combat 既有场景（III 级盾 100→60、XI 级→0、非玩家不走玩家管线）+ 新管线全场景（擦弹/乘算/减至0/全局封顶）+ 幂等重算/增幅降低钳血 + 死亡保留/旧档空容器 + 变身临时层到期恢复与越界改写被拒
- [x] 3.3 构建验证：`cmd /c "gradlew.bat build --console=plain > build_out.txt 2>&1"` 后读文件确认零告警；`runServer` 启动日志断言无注册表错误

## 4. 收尾

- [x] 4.1 属性键与上限提示文案 zh_cn 优先录入，en_us 同步
- [x] 4.2 逐 Scenario 对照 delta specs 验收，确认无表外属性、无新增网络包
