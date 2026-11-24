## Context

现有 `gensokyou:gensokyo` 使用噪声划分群系，隙间使用原点的地表高度作为落点。固定地理布局可以让生成器、入口和后续建筑共用坐标。八区风格、地理关系和尺度候选详见调研草案。 [幻想乡维度技术调研与八区地形草案](gensokyo-dimension-research.md)

## Goals / Non-Goals

- 本期交付单岛、八区主题地形、幻想乡自然素材、八处地标预留地和博丽固定入口。
- 本期验证三途川换边。在原型达到联机与乘船验收目标后，才启用正式环绕。
- 建筑制作、死亡复活规则调整和跨接缝建造属于后续范围。

## Decisions

1. 使用一份随世界种子变化的二维布局定义海岸和分区，地标沿用固定锚点。海岸半径的部分波形和分区坐标扭曲随种子变化。`BiomeSource`、地形密度和调试地图都读取噪声路由中的布局。八区使用各自的高度曲线，在边界渐变。守矢采用弯曲主脊，白玉楼采用带陡坎的阶地，竹林采用浅沟，人间之里采用低地缓坡。雾之湖使用独立湖盆，地灵殿使用弯曲裂口和相连洞腔。沿用 `minecraft:noise` 的区块生成流程。
2. 地形使用共享的幻想乡岩土和每区代表性植被。地表规则决定基底，放置特征生成树木与矿脉。灵土、神木、魔法木和星银矿沿用已有方块，重点补齐覆盖面积大的岩土和海岸方块。
3. 入境落点与孪生门位分别绑定庭院中心和边缘。生成时固定小范围安全地形，为后续建筑对齐。自然装饰围绕预留地布置，避免落点取到树顶或水面。
4. 环绕原型采用同维度双轴换边。在海上提前准备目标区块，保持越界距离、朝向和速度，把船及乘员作为整体同步。用一致的海面与薄雾减轻画面跳转。实机结果决定正式启用条件。
5. 群系调试地图从共享布局绘制 128×128 像素的底图，使用原版地图数据保存和同步。玩家在创造模式下可用原版主手或副手姿势查看地图，图例放在物品提示中。底图覆盖整岛且无需加载区块。玩家位于幻想乡时，客户端用底图的坐标比例投影原版玩家箭头。

## 群系方块与植被铺设规范

### 1. 全局通用基底（Shared Base & Subsurface）
- **默认基岩体（`default_block`）**：`gensokyou:gensokyo_stone`（幻想石）替代原版石头，作为全维度陆地与山脉的默认岩石主体；辰砂矿（`cinnabar`）、灵铁矿（`spirit_iron_ore`）、星银矿（`star_silver_ore`）直接嵌入其中。
- **深层岩（Deep Layer / `Y < 0` 或深渊区）**：`gensokyou:chireiden_deepslate`（地底暗岩）替代原版深板岩，通过垂直梯度规则在低海拔地底与裂谷深处平滑替换幻想石。
- **通用次表层（Subsurface / 距地表 2~4 格）**：除裸岩群系与沙滩外，陆地群系表层下方默认铺设 2~3 层 `gensokyou:gensokyo_dirt`（幻想土）替代原版泥土。
- **点缀方块定位**：`oni_stone`（鬼石）、`spirit_soil`（灵土）、`porcelain_clay`（瓷土）、`higan_soil`（彼岸土）、`moon_sand`（月砂）均**不作为全区满铺基底**，仅通过局部噪声斑块（Noise Patch）或矿团/浅滩特征（`ore` / `disk` feature）作为高价值可采集资源点缀。

### 2. 八大陆地群系 + 三途川的铺设与植被矩阵

| 群系 ID | 表层主铺方块 (Top Floor) | 次表层 / 陡坡裸露 (Under / Steep) | 资源点缀斑块 (Patches / Disks) | 树木与灌木 (Trees / Giant Features) | 地表草本与薄层装饰 (16×16 & 64×64 Plants) |
| --- | --- | --- | --- | --- | --- |
| **博丽神社**<br>`hakurei_shrine` | `gensokyo_grass_block`<br>（幻想草径） | 次表层：`gensokyo_dirt`<br>陡坎：`gensokyo_stone` | `spirit_soil`（灵土斑块，近庭院缓坡与山肩点缀） | `sacred_wood` + `sacred_leaves`（高冠神木疏林） | **主铺**：`gensokyo_short_grass`（幻想灵茵）<br>**点缀**：`spirit_herb`（野生灵草）、`fallen_sakura_petals`（神社周边零星落瓣） |
| **守矢山**<br>`moriya_mountain` | 山腰与高地以 `gensokyo_grass_block` 为主 | 次表层：`gensokyo_dirt`<br>陡坡：`gensokyo_stone` | 深山岩层生成 `star_silver_ore`（星银矿）；高山湖岸点缀 `porcelain_clay` | 山腰：`sacred_wood`（神木）与少量原版云杉混交 | **山腰**：`gensokyo_short_grass`（幻想灵茵）<br>**高岭岩缝**：`gentian`（野生龙胆）、山顶湖畔少量 `misty_reed`（雾湖芦苇） |
| **魔法森林**<br>`magic_forest` | `forest_humus`<br>（林间腐殖土，满铺） | 次表层：`gensokyo_dirt`<br>洼地暗岩：`chireiden_deepslate` | 潮湿洼地点缀 `spirit_soil`（灵土）与 `higan_soil`（局部暗红腐泥） | 密集宽冠 `magic_wood` + `magic_leaves`（魔法木），交织原版巨型蘑菇 | **主铺**：`glow_mushroom_cluster`（幽光蕈簇，提供林下微光）<br>**点缀**：`magic_mushroom`（野生魔法菇）、`gensokyo_short_grass` |
| **迷途竹林**<br>`misty_bamboo_grove` | `forest_humus`（主铺，含枯竹叶斑）与 `gensokyo_grass_block`（林窗）按噪声交替 | 次表层：`gensokyo_dirt` | 浅沟湿地点缀 `spirit_soil`（灵土）与 `moon_sand`（林间月华细砂斑） | 高密度原版竹子（`minecraft:bamboo`，依赖 `#minecraft:dirt` 标签生长于腐殖土上） | **主铺**：`gensokyo_short_grass`（幻想灵茵）<br>**点缀**：背阴竹根处零星 `glow_mushroom_cluster`（幽光蕈簇）、`spirit_herb` |
| **白玉楼**<br>`hakugyokurou` | 台地平面：`hakugyoku_stone`（白玉岩）与浅层 `gensokyo_grass_block` 阶状交替 | 台地陡坎与基底：全层 `hakugyoku_stone`（白玉岩） | 庭园边缘与阶地缝隙点缀 `higan_soil`（彼岸土）与 `moon_sand`（月砂） | `eternal_wood` + `eternal_leaves`（常世木/冥界白樱树，疏朗高冠） | **主铺**：`fallen_sakura_petals`（冥界落樱瓣，成片铺于白玉岩与草径上）<br>**点缀**：`higanbana`（彼岸花，点缀于彼岸土斑块上） |
| **地灵殿裂谷**<br>`chireiden` | 地表裂谷与洞腔全层：`chireiden_deepslate`（地底暗岩） | 次表层与深渊壁：`chireiden_deepslate` | 裂谷断层点缀 `oni_stone`（鬼石矿团）、`cinnabar`（辰砂矿）与 `higan_soil` | 无地表乔木（保持荒凉险峻的裂谷风貌） | **点缀**：洞口与裂谷阴暗面零星分布 `glow_mushroom_cluster`（幽光蕈簇，微光路标） |
| **红魔馆 & 雾之湖**<br>`scarlet_devil_mansion`<br>`misty_lake` | 陆地岸坡：`gensokyo_grass_block` 与 `forest_humus` 混铺<br>湖滩与浅湾湖床：`gensokyo_sand`（幻想沙） | 次表层：`gensokyo_dirt`<br>深水湖床：`gensokyo_dirt` + `gensokyo_stone` | 湖湾浅滩沉积 `porcelain_clay`（瓷土圆盘）与暗红岸泥 `higan_soil`（彼岸土斑块） | 环湖低密林：`magic_wood`（魔法木）与少量 `sacred_wood` | **水岸线主铺**：`misty_reed`（雾湖芦苇，沿湖岸浅滩成丛分布）<br>**林下**：`gensokyo_short_grass`、`higanbana`（红魔馆周边点缀） |
| **人间之里**<br>`human_village` | 冲积平原主铺：`gensokyo_grass_block`（幻想草径）<br>浅溪河漫滩：`gensokyo_sand`（幻想沙） | 次表层：厚层 `gensokyo_dirt`（幻想土） | 河阶沉积 `porcelain_clay`（瓷土）与肥沃农田斑块 `spirit_soil`（灵土） | 疏朗小树群：`sacred_wood`（神木）点缀于村落预留地外围 | **主铺**：`gensokyo_short_grass`（幻想灵茵，开阔草甸感）<br>**溪岸**：`misty_reed`（雾湖芦苇）、`spirit_herb`（灵草） |
| **三途川（外海）**<br>`sanzu_river` | 近岸海滩与浅滩：大面积 `gensokyo_sand`（幻想沙）<br>深海海床：`gensokyo_sand` + `chireiden_deepslate` | 海床次表层：`gensokyo_stone` | 冥河岸线点缀 `moon_sand`（月砂星斑）与 `higan_soil`（彼岸土斑块） | 无树木 | **近岸浅滩**：`misty_reed`（雾湖芦苇）<br>**彼岸土斑块上**：`higanbana`（彼岸花，强化三途川冥河辨识度） |

### 3. 岸线、竹林与基础矿脉

- 三途川与雾之湖使用统一水位，地形从水下斜坡过渡至干岸。关闭幻想乡的噪声含水层，避免沿岸出现局部水位。
- 迷途竹林使用原版竹子方块生成 16～22 格的高竹。密度随噪声变化，保留疏密起伏。自然生成的高竹沿用原版掉落和玩家栽种规则。
- 八区陆地下均可生成灵铁和辰砂矿脉，雾之湖湖床下也有同样的矿脉。星银仍分布于守矢山、魔法森林和地灵殿。普通妖精沿用自然刷新，大妖精由现有召唤渠道获取。

### 4. 群系装饰与树形

- [生态方案](tier-3-materials-review.md)为八个陆地区、雾之湖和三途川分别安排观赏植被。按地表、岩壁、洞顶、湿岸和水底选择生成位置。区域矿脉按各自的基底与高度带放置。
- 森林由星冠木巨冠和魔法木下层构成。神树混用分枝冠与偏冠，常世木混用白樱冠与风折冠，守矢山混生松柏轮廓的树。自然树从地表找根位，并校验空气与下方基底。巨木校验整个 2×2 根基。地标附近留出树冠净空。

## 验收数值（task 3.2 / 3.3）

[八区地形与素材验收数值](acceptance-numbers.md)固化了全部阈值与实测结果。要点：

- **地表/地下占比**：每区分别 ≥90% / ≥95%，最差区块 ≥85% / ≥90%。采样用
  `MOTION_BLOCKING_NO_LEAVES` 并排除树干和竹子；原版云杉/竹子属植被，不计入"自然地表"。
- **边界过渡**：32 格步长相邻高差 ≤48 格（守矢主脊实测 44 为最大值）。
- **内部路线**：地标四向射线至少一条满足水隙 ≤3 格、相邻高差 ≤20 格。
- **点缀斑块**：灵土/彼岸土/月砂/瓷土以 `minecraft:disk`（half_height 1）生成，鬼石用
  size 5 矿团，彼岸花用 random_patch；全部由 `tools/gensokyo_ecology_data.py` 生成。
- **探针**：`GensokyoWorldgenProbe` 增加自然资源、占比、过渡、路线四组断言，双种子验证。

## Risks / Trade-offs

- 噪声细节和装饰可能破坏海岸连通性或预留地。按实际地表采样，测量各区陆地连通性，并逐个检查落点与地块。
- 海上换边涉及客户端区块同步。以低视距、联机延迟、目标区块首次生成和双人同船测试覆盖冷启动场景。
- 星银矿的自然供给会影响现有仪式收益。评审矿脉分布与密度，保留现有精炼流程。

## Migration Plan

保留维度 ID。以新世界验收布局和入口。旧幻想乡区块仍由存档保留，新生成的区块使用曲折海岸和分区，两者相接处可能出现断层。旧调试地图首次重新手持时刷新为新版布局。

## Open Questions

- 三阶加工配方与植被密度按[生态方案](tier-3-materials-review.md)和客户端实景复核。
- 八区的方位、尺度和风格按草案审阅，重点确认白玉楼台地和地灵殿的地表入口。
- 星银矿等天然素材的分布密度与接缝的可感知程度分别按原型结果复核。
