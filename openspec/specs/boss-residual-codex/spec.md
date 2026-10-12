# boss-residual-codex Specification

## Purpose
TBD - created by archiving change chinkon-hanshou-ritual. Update Purpose after archive.
## Requirements
### Requirement: 物品与寄宿数据组件

系统 SHALL 提供正常物品 `gensokyou:boss_residual_codex`（寄宿残魂的众生典籍），不可堆叠，并 SHALL 以数据组件编码其寄宿的 BOSS：`{boss: ResourceLocation, tier: int}`（`boss` 为 BOSS 的实体类型 id，`tier` 为其 `bossTier()`）。组件 SHALL 持久化并网络同步，缺失时为无效物品（MUST NOT 被镇魂返照之仪识别为有效供品），MUST NOT 导致加载失败。物品 SHALL 提供静态访问器读取寄宿 BOSS 身份与品阶，供镇魂返照之仪调用。

物品 SHALL 为**独立物品类**，MUST NOT 为 `gensokyou:codex_of_beings` 的实例或其数据组件变体。

#### Scenario: 组件持久化与同步

- **WHEN** 获得一件残魂典籍后存取存档并登录客户端
- **THEN** `boss` 与 `tier` 保持，tooltip 正确显示寄宿 BOSS 名

#### Scenario: 无效物品兜底

- **WHEN** 加载一件缺少寄宿组件的 `boss_residual_codex`
- **THEN** 视为无效，镇魂返照之仪不识别其为供品，不报错

### Requirement: 被众生余录排除

残魂典籍 MUST NOT 被「众生余录」（`shujou_yoroku_circle`）识别或处理。因其为独立物品类（非 `CodexOfBeingsItem`），众生余录既有的 `instanceof CodexOfBeingsItem` 判据 SHALL 天然排除之，MUST NOT 为此在众生余录中加入残魂典籍的特判分支。

#### Scenario: 众生余录不识别

- **WHEN** 将一件残魂典籍放上众生余录的祭品台并启动
- **THEN** 该典籍不计入有效 species，不产生任何扣费或产出

#### Scenario: 不污染众生余录契约

- **WHEN** 审查众生余录的行为实现
- **THEN** 其中不含针对 `boss_residual_codex` 的条件分支

### Requirement: 合成获取（空典籍 + 主题信物）

系统 SHALL 提供数据驱动的合成：`空的众生典籍 ×1 + 该 BOSS 的主题信物 ×1` → `寄宿残魂的众生典籍 ×1`，结果 SHALL 写入 `{boss, tier}` 组件。系统 SHALL 为**每只声明了主题信物（`TouhouBoss.signatureItem()` 返回且属 `#gensokyou:boss_signature`）的 BOSS 各提供一条**配方。基底典籍 MUST 为**空的**众生典籍；未满/满态典籍 MUST NOT 被接受为原料。

#### Scenario: 大妖精残魂合成

- **WHEN** 用空的众生典籍 + `gensokyou:fairy_nectar` 合成
- **THEN** 得到一件寄宿大妖精（`tier` 为其 `bossTier()`）的残魂典籍

#### Scenario: 满态典籍不可作基底

- **WHEN** 用一本满 20 的众生典籍尝试残魂合成
- **THEN** 该配方不成立

#### Scenario: 每只带信物 BOSS 一条配方

- **WHEN** 审查配方集合
- **THEN** 大妖精/黑谷山女/八云蓝残影/露米娅 各有对应配方；未声明信物的 BOSS 无配方

### Requirement: 表现与获取途径

残魂典籍 SHALL 具 tooltip，显示其寄宿的 BOSS 本地化名（如「寄宿着 大妖精 的残魂」），并 SHALL 呈附魔光效以区别于普通典籍。物品 SHALL 出现在本 mod 创造物品栏，并 SHALL 具备物品模型与独立贴图（占位期走项目资产规范，路径按最终命名创建）。

#### Scenario: tooltip 显示寄宿对象

- **WHEN** 查看一件寄宿大妖精残魂的典籍
- **THEN** tooltip 显示大妖精的本地化名，且物品发光

#### Scenario: 创造栏可见

- **WHEN** 打开本 mod 创造物品栏
- **THEN** 可见残魂典籍并可取出，图标正常显示

