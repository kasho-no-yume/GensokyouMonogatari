# Proposal: 无尽藏晶物品存储

## Why

无尽藏晶（`gensokyou:crystal`）目前是纯装饰方块（BER 渲染、方块实体零数据、无生存获取途径）。幻想乡物资管理缺少正式物品存储设施——现有唯一"箱"是仪式核心的祭品台代理箱（每槽 1 件、活代理零存储），无法承载批量物资。本变更为终局内容补上首个物品存储方块，并补上其获取途径。

## What Changes

- 无尽藏晶成为**物品存储方块**：自适应格子箱，**全局物品总数上限默认 2000**（config 可调），任意种类混合，逐件保留物品 NBT/组件。
- **拒收规则**（插入路径统一校验）：
  - 拒绝能装物品的容器类（潜影盒 / 收纳袋 / 任何暴露物品 handler 的模组容器，含标签兜底）；
  - 拒绝单件 NBT 序列化体积超限的物品（默认 4KB，config 可调）。
- **破坏即全灭**：方块被破坏时内容全部消失，物品形态不携带内容，掉落干净的方块物品。
- 对全部方向暴露 `IItemHandler`：漏斗/管道可存可取，受全局物品总数预算背压截断。
- **玩家界面**：窗口化真 Slot 网格（每页 54 格），支持滚动与搜索；仅同步当前可见页，绝不整发物品列表。
- **获取途径**：0 阶源初造化配方——6 箱子 + 1 钻石块 + 1 金块 + 12000 灵力 → 1 无尽藏晶。
- 装饰渲染（BER）与"零方块实体同步"现状保持不变。

## Capabilities

### New Capabilities

- `crystal-storage`: 无尽藏晶的存储模型、拒收规则、破坏语义、自动化接口、玩家界面与配方。

### Modified Capabilities

（无。新增的造化配方只是既有 `ritual-recipes` 数据事实的一条例项，不改其 requirement；装饰渲染属既有行为，不动。）

## Impact

- **代码**：`CrystalBlockEntity`（物品池 + 拒收闸 + `IItemHandler` + 破坏语义）、`CrystalBlock`（右键开界面，保留渲染）、新增 Menu/Screen（可滚动 Slot 网格 + 搜索）、`ModCapabilities`（注册藏晶 handler）、`GensokyouConfig`（总量上限、单件 NBT 字节上限）、lang `zh_cn`/`en_us`。
- **数据**：`data/gensokyou/ritual_recipes/zaohua_circle.json` 追加一条配方；`gensokyou:crystal` 的 blockstate/模型/掉落表/创造栏已存在，无需新增美术。
- **依赖**：复用仓库既有范式——`ModCapabilities`（capability 注册）、`RitualRecipes`（配方数据）、`AbstractContainerScreen`（界面）、`ModNetworking`（如需翻页/搜索 C2S）。
- **存档**：新增方块实体数据字段，无旧档迁移压力（无数据即空箱）。
- **风险**：超大 NBT 物品（成书等）与网络包上限——由"单件字节闸 + 仅同步可见页"规避，详见 design。
