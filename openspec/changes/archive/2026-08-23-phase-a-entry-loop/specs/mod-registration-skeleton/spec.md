## ADDED Requirements

### Requirement: 模组入口与注册体系
模组 SHALL 以单一 `@Mod` 主类为入口，物品/方块/实体类型/状态效果/创造标签 SHALL 全部经 DeferredRegister 挂载到 mod event bus；启动日志 MUST 无注册错误。

#### Scenario: 干净启动
- **WHEN** 客户端与专用服务器分别以本 mod 启动
- **THEN** 所有 gensokyou 注册项完成注册，无报错进入主菜单/服务器循环

### Requirement: 统一配置文件
本阶段全部可调数值（BOSS 五维与经验、妖精数值、弹幕伤害/速度/间隔、无想封印参数、掉落概率、刷怪权重）SHALL 从 COMMON 类型 ModConfigSpec 读取，按 boss/fairy/danmaku/items/spawn 分节；代码中不得出现这些类别的魔法数字。

#### Scenario: 配置生成与生效
- **WHEN** 首次运行生成 config 并修改 boss.maxHealth 后重启世界
- **THEN** 配置文件含全部分节及注释默认值；新生成的芙兰朵露生命值反映新配置

### Requirement: 创造模式标签
SHALL 存在 `gensokyou:gensokyou` 创造标签，收录本阶段全部可获得物品。

#### Scenario: 标签页完整
- **WHEN** 打开创造模式 Gensokyou 标签页
- **THEN** 引导书、两张符卡、四种材料、円、拉维坦剑、召唤催化剂均在列

### Requirement: 占位资产规范落地
所有资产 SHALL 遵循 project.md §5：仅引用 gensokyou 命名空间路径、最终命名、内容为原版像素拷贝（物品=海洋之心、实体皮肤=Alex、方块=钻石块）；SHALL 建立占位资产清单文档。

#### Scenario: 引用审计
- **WHEN** 在 assets/gensokyou 与源码中检索对 minecraft: 贴图路径的直接引用
- **THEN** 无匹配（parent 继承 vanilla 模型结构除外，但不得借用其贴图）

### Requirement: 模板残留清理
MDK 示例（example_* 物品方块标签与 Config 模板）MUST 移除。

#### Scenario: 构建验证
- **WHEN** 执行 gradlew build
- **THEN** 构建成功且产物不含 example_* 注册项
