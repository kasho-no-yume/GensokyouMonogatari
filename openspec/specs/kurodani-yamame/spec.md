# kurodani-yamame Specification

## Purpose
TBD - created by archiving change add-kurodani-yamame-boss. Update Purpose after archive.
## Requirements
### Requirement: 黑谷山女的身份与召唤
本 mod SHALL 提供黑谷山女（`kurodani_yamame` / 实体 id `yamame`）作为经百鬼夜行召唤仪式
降临的 **T1 野生 BOSS**，接在大妖精之后的进度位。

她 SHALL 被定义为**土蜘蛛妖怪本人**，MUST NOT 被定义为「残影」或任何缺段型存在；
其符卡表 SHALL NOT 受任何缺段约束。

#### Scenario: 身份为普通 BOSS
- **WHEN** 读取黑谷山女的实体定义与其符卡表
- **THEN** 她是 `AbstractTouhouBoss` 的普通子类，其符卡表不含「缺序 / 缺破 / 缺結」之类约束

#### Scenario: 沿召唤通路降临
- **WHEN** 百鬼夜行召唤仪式的配方 `hyakki_boss_yamame` 匹配成功并完成演出
- **THEN** 黑谷山女生成于仪式核心正上方，按野生 BOSS 处理（不绑定祭坛场地）

#### Scenario: 覆盖鬼蛛的进度位
- **WHEN** 对比本变更前后的 T1 召唤 BOSS 名单
- **THEN** 原先的鬼蛛位由黑谷山女占据；鬼蛛不再被注册、不再有召唤配方

### Requirement: 四张签名符卡

黑谷山女的符卡表 SHALL 由 **4 张**构成，起始占比随血量递减；每张符卡 SHALL 携带名称、血量区间与 1~5 条并发轨道。四张卡 SHALL 使用两组可学习的母题：第 1、3 张为蛛丝编织系列，第 2、4 张为瘴气螺旋系列。后两张 SHALL 通过同母题增加层数、相位、空间实例或节奏压力加难，MUST NOT 以互不相关的脚下柱、随机激光网和压墙组合替代主图案。

四张卡 SHALL 对应下述设计基准：

| 序 | 符卡 | 母题 | 结构意念 |
|---|---|---|---|
| 1 | 罠符「キャプチャーウェブ」 | 網 | 稀疏蛛丝逐批编织 → 缺口横移 → 网线离场 |
| 2 | 瘴符「フィルドミアズマ」 | 瘴 | 中央螺旋膨胀 → 一组凝结冻结 → 另一组穿出 → 收束 |
| 3 | 蜘蛛「石窟の蜘蛛の巣」 | 網 | 密集蛛丝编织 → 两面错位网幕 → 保留三维通道 → 收束 |
| 4 | 瘴気「原因不明の熱病」 | 瘴 | 中央大螺旋 + 左右小螺旋同步展开 → 分组凝结 → 中央收束 |

每张卡 SHALL 能描述为预备、展开、压力和收束四个阶段；主图案 SHALL 优先使用显式节拍时间线与独立循环长度表达。

#### Scenario: 符卡数为四
- **WHEN** 读取黑谷山女的符卡表
- **THEN** 得到 4 张符卡，起始占比依次递减，且每张轨道数在 1~5 之间

#### Scenario: 同母题逐级加压
- **WHEN** 对比第 1 张与第 3 张，或对比第 2 张与第 4 张
- **THEN** 后者仍然使用前者的主图案语法，并通过网幕纵深、缺口变化、螺旋数量或相位压力加难

#### Scenario: 每张卡具有完整时间线
- **WHEN** 玩家进入任一山女符卡的一轮
- **THEN** 玩家先看到预备与展开，再进入压力段，最后看到主图案收束或旧弹离场；MUST NOT 由多个无关重复周期组成没有边界的噪声

#### Scenario: 符卡提供可复用解法
- **WHEN** 玩家观察并理解当前符卡的主图案
- **THEN** 玩家可以通过短距离换位、穿越缺口或利用上下/前后通道躲避，MUST NOT 只能持续远离攻击范围

#### Scenario: 阶段随血量切换不叠加
- **WHEN** 生命跨过下一张符卡的起始阈值
- **THEN** 切换被挂起到当前符卡循环边界后发生，MUST NOT 出现两张符卡同时运行

### Requirement: 符卡表通过轨道静态校验

黑谷山女的全部符卡 SHALL 通过 `TrackLint` 的三维可读性契约（R1 前向威胁 / R2 解法全向 / R3 稳态并发预算）、轨道视觉独占与签名色盘容量校验；实现验证还 SHALL 检查主图案连续性、预备与收束、稳定安全区、旧弹回收和玩家能否停止观察。

#### Scenario: lint 全绿
- **WHEN** 以 `TrackLint.lint` 校验黑谷山女的符卡表与其签名色盘
- **THEN** 返回空违规列表

#### Scenario: 稳态并发留有余量
- **WHEN** 按其符卡表计算各卡稳态并发密度
- **THEN** 各卡不超过 `STEADY_STATE_BUDGET` 的一半（在役卡余量要求）

#### Scenario: 实机可停留观察
- **WHEN** 以生存模式进入任一山女符卡的第一轮
- **THEN** 玩家能够停止连续移动并观察至少一个完整生成动作，同时仍存在可用躲避解

#### Scenario: 主图案连续
- **WHEN** 玩家改变站位但没有离开当前符卡
- **THEN** 已生成的网幕或螺旋保持其发射时的空间关系，不围绕玩家新位置重新洗牌

### Requirement: 默认攻击与符卡叠加

黑谷山女 SHALL 拥有一条独立于符卡表的常驻默认攻击轨（蛛丝扇），其频率与参数 SHALL 从 config 读取，MUST NOT 硬编；该轨 MUST NOT 计入符卡轨道数。默认攻击可以与符卡叠加，但符卡期间 SHALL 作为低频、少量、可辨识的底噪运行，MUST NOT 与当前符卡使用相同的高频主视觉，也不得持续否定符卡安全区。

#### Scenario: 底噪不夺取视觉中心
- **WHEN** 黑谷山女处于任一符卡中
- **THEN** 玩家能优先识别当前符卡的网幕或螺旋，默认蛛丝扇不会读作第二套符卡

#### Scenario: 默认攻击不抹掉安全区
- **WHEN** 玩家站在符卡主图案规定的安全区或通道内
- **THEN** 默认攻击不会以高频连续扇射否定该安全区；必要时可在符卡期间降频或暂停

#### Scenario: 不占符卡轨道
- **WHEN** 校验任一张符卡的轨道数
- **THEN** 该计数 MUST NOT 包含默认攻击轨

### Requirement: 数值以秒带与挨弹带约束
黑谷山女的生命 SHALL 由「参照玩家 DPS × 战斗秒数」得出，弹伤 SHALL 由「参照玩家 EHP ÷ 挨弹数」
得出；秒数与挨弹数 MUST 从 config（`yamameBossSeconds` / `yamameBossHits`）读取。

其参照阶 SHALL 为 T1，秒数 SHALL 落在 2~15 分钟秒带内（配置区间 120~900s）。秒数为**目标值
而非保证**：SHALL 以 `/gs_boss spawn` 实测后回写。

#### Scenario: 数值可配置
- **WHEN** 调整 `yamameBossSeconds` 或 `yamameBossHits`
- **THEN** 其生命与单发弹伤随之改变，无需改动实体代码

#### Scenario: 秒带内
- **WHEN** 以默认配置计算黑谷山女的战斗秒数并加 spawn ±25% roll
- **THEN** 结果落在 2~15 分钟秒带内

### Requirement: 掉落与进度位
黑谷山女死亡时 SHALL 掉落碎符卡星，并 SHALL **保底**掉落隙间碎片（`SUKIMA_FRAGMENT`）——
她是解锁 T2 材料带与 L2 祭坛的唯一前置，掉落 MUST NOT 退化为概率。

#### Scenario: 保底掉隙间碎片
- **WHEN** 击杀黑谷山女
- **THEN** 至少掉落 1 个隙间碎片与配置数量的碎符卡星

### Requirement: GeckoLib 渲染接入
黑谷山女 SHALL 由 GeckoLib 的 `GeoModel` / `GeoEntityRenderer` 渲染，使用自有命名空间的
几何、动画与贴图资源；MUST NOT 走残影（billboard / `RemnantBossRenderer`）占位通路。

#### Scenario: 走正式模型
- **WHEN** 黑谷山女被渲染
- **THEN** 渲染器为 `YamameGeoRenderer`，资源取 `assets/gensokyou/` 下山女自有几何/动画/贴图

#### Scenario: 不与残影混淆
- **WHEN** 解析渲染注册点
- **THEN** 山女 MUST NOT 出现在 `RemnantBossRenderer` 的残影注册表中

