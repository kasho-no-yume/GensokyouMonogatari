## ADDED Requirements

### Requirement: 模组入口与注册骨架
模组 SHALL 以单一 `@Mod("gensokyou")` 主类作为入口，所有可注册对象（物品、实体类型、状态效果、创造模式标签）SHALL 通过 DeferredRegister 在主类构造器中挂载到 mod event bus 注册，代码中 MUST NOT 出现对已弃用的手动 registry API 的调用。

#### Scenario: 游戏启动完成注册
- **WHEN** 客户端或专用服务器以本 mod 启动
- **THEN** 所有 gensokyou 命名空间的物品/实体/效果/标签在启动日志无错误地完成注册，游戏进入主菜单

### Requirement: 包结构与命名规范
源码 SHALL 位于 `com.bitsson.gensokyou` 包下并按域分子包（registry/item/entity/effect/spellcard/config/client），类名 SHALL 使用 PascalCase；registry name SHALL 使用小写蛇形路径。

#### Scenario: 新增注册项遵循约定
- **WHEN** 开发者新增一个注册项（如新符卡物品）
- **THEN** 其类位于对应域子包且类名为 PascalCase，registry name 为小写（如 `gensokyou:new_card`），无需额外配置即可被创造标签收录

### Requirement: 创造模式标签
模组 SHALL 提供专属创造模式标签 `gensokyou:gensokyou`，收录本期全部玩家可获得物品。

#### Scenario: 创造模式下查看标签页
- **WHEN** 玩家在创造模式打开物品栏的 Gensokyou 标签页
- **THEN** 光弹、拉维坦剑、P/B 点、符卡星与碎星、两张可用符卡均出现在该标签页

### Requirement: 清理模板残留
模板示例内容（example_block、example_item、example_tab 及相关 Config 引用）SHALL 全部移除，`gradlew build` 在迁移首个阶段后保持通过。

#### Scenario: 构建不含示例残留
- **WHEN** 执行 `gradlew build`
- **THEN** 构建成功且产物中不存在 example_* 注册项

### Requirement: 死代码不入库
旧仓库中的死代码（Reflection.java 草稿类、注释掉的旧 theWorld 实现、未注册的导入书物品）MUST NOT 迁移到新代码库。

#### Scenario: 代码库检索死代码
- **WHEN** 在新代码库中检索 Reflection 类或旧 theWorld 注释块
- **THEN** 无匹配结果
