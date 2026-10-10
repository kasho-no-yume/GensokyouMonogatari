# gensokyo-materials Specification

## Purpose
TBD - created by archiving change add-gensokyo-material-ladder. Update Purpose after archive.
## Requirements
### Requirement: 幻想乡素材目录（T1~T2）
系统 SHALL 注册幻想乡独有素材物品，分属矿产 / 土产 / 木材 / 海产 / 植物五类，每类 SHALL 具备中英语言键、物品模型与贴图，且 MUST 与原版物品名称与外形可辨：

- 矿产（原矿，大山祇神之座产出）：`cinnabar`（辰砂，T1）、`spirit_iron_ore`（灵铁矿，T1）、`star_silver_ore`（星银矿，T2）、`oni_stone`（鬼石，T2）
- 矿产（成品金属，金山彦命煅炉炼出）：`spirit_iron`（灵铁）、`star_silver`（星银）
- 矿产（精炼产物，金山彦命煅炉炼出）：`porcelain`（瓷器）
- 土产：`spirit_soil`（灵土，T1）、`porcelain_clay`（瓷土，T1）、`higan_soil`（彼岸土，T1）、`moon_sand`（月砂，T2）
- 木材：`sacred_wood`（神木，T1）、`magic_wood`（魔法木，T1）、`eternal_wood`（常世木，T2）
- 海产：`sanzu_flask`（瓶装三途川水，**即冥水**，T1）、`spirit_fish`（灵鱼，T1）、`mermaid_scale`（人鱼鳞，T1）、`tide_crystal`（潮汐晶，T2）、`dragon_scale`（龙鳞，T2）
- 植物（**由农业产出**）：`spirit_herb`（灵草，T1）、`gentian`（龙胆，T1）、`higanbana`（彼岸花，T1）、`magic_mushroom`（魔法菇，T2）
- 植物种子（**由茅野姬神花亭产出**）：`spirit_herb_seeds`、`gentian_seeds`、`higanbana_seeds`、`magic_mushroom_spores`
- mod potion（mod 专属药水线）：`crude_sanzu_potion`（粗制冥汤，基液）、`reiki_recovery`（回灵汤）、`spiritual_sight`（灵视药水）、`spirit_touch`（灵触药水）、`higanbana_poison`（彼岸花毒）

`sanzu_flask` 是 mod 专属药水线的独占基液，SHALL 直接以物品形态被消耗，MUST NOT 存在"开启为冥水"的交互或配方。上述新增交付物均无原版对应物，MUST NOT 违反素材唯一性红线。

#### Scenario: 注册与本地化齐备
- **WHEN** 完成注册后打开本 mod 创造标签并切换中英文
- **THEN** 全部素材（含新增交付物）可见、图标正常渲染、名称按语言本地化且无裸 id

#### Scenario: 与原版可辨
- **WHEN** 玩家同时持有任一新素材与其最相近的原版物品
- **THEN** 两者名称与图标均可区分，不产生「这是不是原版 X」的歧义

#### Scenario: 冥水无开启产物
- **WHEN** 审查 `sanzu_flask` 的相关行为
- **THEN** 不存在"开启为冥水"的交互或配方，它直接作为 mod 药水基液被消耗

### Requirement: 素材唯一性红线
原版可获得之物 MUST NOT 另立为重复的 mod 素材；如竹、樱花木、向日葵 SHALL 由对应资源仪式以原版物品形式作为「体积」产出，MUST NOT 造同形 mod 素材。mod 素材命名 MUST NOT 与原版物品重名。本条仅约束「是否新造重复物品」，MUST NOT 被解读为「配方不得使用原版物品」（后者见「配方构成与凡材使用」）。

#### Scenario: 原版物不另立素材
- **WHEN** 审查素材目录与仪式 commons 产出
- **THEN** 不存在与原版物品重名或同形的 mod 素材；原版竹 / 樱花木 / 向日葵只以原版物品出现

### Requirement: 配方构成与凡材使用
凡材（原版物品）SHALL 可自由用作任意配方配料，不设数量或比例上限。**入口层**物品（T0 仪式石、仪式核心、基础法阵）MUST NOT 被强制要求含幻想乡素材，允许以凡材 + 少量 mod 钥匙（如 `ppoint`）构成。**mod 独有品**（弹幕主武器、弹幕核、灵力核心、众生典籍、符卡）SHALL 含至少一件 mod 料。**阶级物品**（T(N) 仪式石、阶级装备）SHALL 含对应阶级的 mod 材。

#### Scenario: 入口层低门槛
- **WHEN** 审查 T0 仪式石与仪式核心配方
- **THEN** 二者以凡材为主、仅含 `ppoint` 等低阶 mod 钥匙，不含任何幻想乡材

#### Scenario: 凡材可自由作配料
- **WHEN** 审查任一 mod 独有品配方
- **THEN** 凡材配料比例不受限制，只需同时含 ≥1 件 mod 料

#### Scenario: 独有品不得全凡材
- **WHEN** 审查弹幕武器 / 弹幕核 / 灵力核心 / 众生典籍 / 符卡配方
- **THEN** 每条配方至少含一件 mod 料，不存在纯凡材配方

### Requirement: 材料—仪式来源映射
五类素材 SHALL 各由同类的既有资源仪式经 `gensokyou` 条件池产出：矿产→大山祇神之座（镐）、土产→埴山姬神之壤（锹）、木材→久久能智神庭（斧）、海产→绵津见神之藏（钓）、植物→茅野姬神花亭（锄）。素材在池中的分层 SHALL 与其阶级一致（T1 材属低阶带、T2 材属中阶带）。

植物类 SHALL 另有一条**农业产出路径**：茅野姬神花亭负责供给种子，植物本体由对应作物成熟产出。土产类 SHALL 有两条派生路径：瓷土经金山彦命煅炉精炼为瓷器、`spirit_soil` 可被锄为 `spirit_soil_farmland`。

#### Scenario: 按类归位
- **WHEN** 在久久能智神庭上解锁对应材料带并结算
- **THEN** 抽取结果只含木材类素材，不出现矿产 / 海产等其他类

#### Scenario: 植物类双来源
- **WHEN** 玩家需要任意一种植物本体
- **THEN** 唯一途径是种植对应种子并等待成熟；茅野姬神花亭不再直出植物本体

#### Scenario: 瓷土派生
- **WHEN** 玩家持有 `gensokyou:porcelain_clay`
- **THEN** 可在金山彦命煅炉获得瓷器，而原版熔炉、高炉、烟熏炉 SHALL NOT 产出瓷器

### Requirement: 访问信物与材料带
系统 SHALL 使用两枚信物分带解锁 `gensokyou` 池：**指导书**（既有物品）解锁低阶带（T1 材），**隙间碎片**（新增物品 `sukima_fragment`）解锁中阶带（T2 材）。信物 SHALL 置于祭品台且 **MUST NOT 被消耗**；未摆放对应信物时该带材料产出恒为零。信物解锁后产量仍 SHALL 随结构阶 `×4^阶` 放大。

#### Scenario: 未解锁零产出
- **WHEN** 资源仪式运行但祭品台上没有对应信物
- **THEN** 该带素材不进入抽取池，结算结果中不出现任何该带材料

#### Scenario: 信物不消耗
- **WHEN** 摆放指导书并使仪式多次结算
- **THEN** 低阶带素材持续可产出，指导书始终留在祭品台上

#### Scenario: 解锁后产量随阶
- **WHEN** 同一信物在同仪式上分别于 0 阶与 2 阶结算
- **THEN** 2 阶产出总件数为 0 阶的 16 倍（`×4^阶`）

### Requirement: 精炼与灵炭归属（后续仪式）
系统 SHALL 提供灵炭物品 `spirit_charcoal` 作为幻想乡原矿精炼的专属辅料。灵炭的木材碳化产出暂不归属本变更，且本变更 MUST NOT 在造化之仪或任何既有仪式中定义精炼 / 碳化配方。金山彦命煅炉 SHALL 接受方块原矿与对应粗矿物品，并按以下比例执行专属精炼：辰砂与灵铁均为原矿 ×1 对应灵炭 ×1；星银为原矿 ×1 对应灵炭 ×2。方块原矿与 `rough_*` 物品均 SHALL 产出对应成品，但只有方块原矿的产物 SHALL 获得矿石翻倍。未列出的原矿或鬼石 SHALL 不获得本变更的专属精炼规则。精炼 SHALL 仅在成功完成时消耗原矿与已绑定灵炭。

#### Scenario: 造化不含精炼/碳化
- **WHEN** 审查造化之仪与各既有仪式配方
- **THEN** 不存在任何「原矿→金属」精炼配方或「木材→灵炭」碳化配方

#### Scenario: 灵炭物品就位
- **WHEN** 从创造标签取出灵炭
- **THEN** 物品正常显示；其木材碳化产出仍不属于本变更

#### Scenario: 辰砂与灵铁比例
- **WHEN** 辰砂或辰砂粗矿、灵铁矿或灵铁粗矿分别与等量灵炭同时位于祭品台
- **THEN** 每个主原料可独立建立一条需要一件灵炭的精炼任务

#### Scenario: 星银比例
- **WHEN** 星银矿或星银粗矿与两件灵炭同时位于祭品台
- **THEN** 每个主原料可独立建立一条需要两件灵炭的精炼任务

#### Scenario: 粗矿与方块原矿均可精炼
- **WHEN** 精炼输入分别为方块原矿物品或对应 `rough_*` 物品
- **THEN** 两者都能被金山彦命煅炉处理并产出对应成品

### Requirement: 冶炼仪式独占
mod 素材 MUST NOT 提供任何原版熔炼 / 烟熏 / 高炉配方；原版炉具既炼不出、亦 MUST NOT 接受 mod 素材产出成品。辰砂、灵铁、星银及其粗矿的精炼职责 SHALL 仅由金山彦命煅炉承担；该仪式 SHALL 通过专属灵炭比例规则处理方块原矿与粗矿，并 SHALL NOT 将这些规则写入原版 cooking recipe 目录。Forge/NeoForge 矿石标签仅用于识别哪些方块输入 SHALL 享受产物翻倍，标签本身 MUST NOT 创建原版炉配方。

#### Scenario: 原版炉子炼不出
- **WHEN** 将任一幻想乡原矿或粗矿放入原版熔炉、高炉或烟熏炉
- **THEN** 不产出任何 mod 精炼产物

#### Scenario: 原版炉子不执行灵炭比例
- **WHEN** 玩家只在原版炉具中放入辰砂、灵铁、星银或对应粗矿
- **THEN** 原版炉具不会自动补入灵炭，也不会执行金山彦命煅炉的比例规则

#### Scenario: 煅炉专属规则可用
- **WHEN** 成型金山彦命煅炉的祭品台上摆放符合比例的方块原矿或粗矿与灵炭
- **THEN** 煅炉建立独立精炼任务，并在完成时消耗锁定输入、产出对应成品

#### Scenario: 方块原矿翻倍而粗矿不翻倍
- **WHEN** 同一精炼规则分别由方块原矿和对应粗矿触发
- **THEN** 方块原矿的成品数量翻倍，粗矿成品数量保持规则声明的基础数量

#### Scenario: 矿石标签不创建原版配方
- **WHEN** 幻想乡方块原矿加入 Forge/NeoForge 矿石标签
- **THEN** 标签只改变煅炉的产物翻倍判定，不新增任何原版炉配方

### Requirement: 冥河瓶形态
三途川主题海产 SHALL 以瓶装物 `sanzu_flask`（冥河瓶）呈现，作为绵津见仪式钓上的漂流物，MUST NOT 以「水」形态（流体或普通水瓶）出现。冥河瓶 SHALL 可开启为冥水，供日后药水 / 酿造使用（开启产物为后续变更内容）。

#### Scenario: 钓得瓶装物
- **WHEN** 绵津见仪式在中阶带结算并命中三途川主题条目
- **THEN** 产出 `sanzu_flask` 物品实体，而非任何流体或原版水瓶

### Requirement: 既有材料定位
`ppoint` 与 `bpoint` SHALL 继续作为合法合成材料使用，但 MUST NOT 充当任何阶物品的唯一钥匙；`memory_fragment` SHALL 升格为幻想乡知识料，用于符卡 / 众生典籍 / 符纸类合成。

#### Scenario: 记忆残页的新用途
- **WHEN** 使用记忆残页作为合成材料
- **THEN** 其可参与众生典籍等知识类配方，而不再仅用于合成指导书

