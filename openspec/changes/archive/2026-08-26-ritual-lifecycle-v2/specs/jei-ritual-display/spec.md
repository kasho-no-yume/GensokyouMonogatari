# jei-ritual-display Specification (delta)

## ADDED Requirements

### Requirement: 祭品要求配方卡
`requirements` 非空的仪式 SHALL 各派生一张配方卡：输入区按 slot 规范序逐位展示所需物品 ×数量，输出区展示仪式效果名文本；卡片数据 SHALL 仅由 rituals JSON 自动派生，增删仪式文件后无需改动任何 Java 集成代码。既有结构条目与催化剂查找入口 SHALL 保持现状不受影响。

#### Scenario: 供品仪式配方卡
- **WHEN** 查看某个声明了四项 requirements 的仪式
- **THEN** 配方卡输入区按规范序显示四台各自所需物品与数量，输出区显示效果名

#### Scenario: 无要求仪式不出卡
- **WHEN** 查看未声明 requirements 的仪式
- **THEN** 仅显示既有结构条目，不产生空配方卡

#### Scenario: 新增仪式自动出卡
- **WHEN** 新增一个含 requirements 的 rituals JSON 并重启客户端
- **THEN** JEI 自动出现对应结构条目与配方卡，无需代码改动
