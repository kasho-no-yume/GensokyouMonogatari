## Context

`KitsuneBiEntity`（狐火『無序』）是八云蓝的占位残影，现状：

| 项 | 现状 |
|---|---|
| 实体 id | `gensokyou:kitsunebi` |
| 渲染 | `RemnantBossRenderer` → `SkinMobRenderer`（Alex 人形占位） |
| 符卡 | 3 张，主题源于已作废的「序·破·結 / 缺段」装置 |
| 定位 | `bossTier=1`、`BOSS_SECONDS_T1 × 1.5`、`configHits=6` |
| 召唤 | 百鬼夜行 L2，钥匙 = `sukima_fragment` |
| 信物 | `signatureItem = sukima_fragment`、`themedCard = fox_servant` |

正式模型在 `C:\Users\spdimdriver\Documents\ran`：Bedrock 格式（`yakumo_ran.geo.json` 166 方块 /
19 骨骼 / 九尾；`yakumo_ran.animation.json` 仅 `animation.yakumo_ran.idle` 4 秒摆尾；
`ran_texture.png` 256×256）。角色连帽高 ≈2.47 格、九尾展幅 ≈3.6 格。

### 考据：八云蓝的官方符卡（THBWiki）

- 妖妖梦 EX（10 张）：式神「仙狐思念」、式神「十二神将の宴」、式輝「狐狸妖怪レーザー」、
  式輝「四面楚歌チャーミング」、式輝「プリンセス天狐 -Illusion-」、式弾「アルティメットブディスト」、
  式弾「ユーニラタルコンタクト」、式神「橙」、「狐狗狸さんの契約」、幻神「飯綱権現降臨」。
- 妖妖梦 PH 道中：式神「前鬼後鬼の守護」、式神「憑依荼吉尼天」。
- 文花帖 L8：密符「御大師様の秘鍵」、行符「八千万枚護摩」、超人「飛翔役小角」。
- 秘封噩梦日记：地妖符「イドの式神」/「式神大星」/「エゴの式神」。
- 兽王园（自机）：式神「スティルハンター チェン」/「マルチスティルハンター」；机体说明点明
  「放置型式神子弹，范围较广的**十字魔方阵**，EX 攻击从 4 个方向袭来」。

角色签名（不是人造装置）：**九尾 / 式神 / 狐火 / 狐狸妖怪激光 / 阴阳术（饭纲权现、荼吉尼天）/ 计算**。

## Goals / Non-Goals

**Goals**

- 把占位残影升级为正式的 T2 正主八云蓝（身份、模型、数值、8 张考据符卡）。
- 重整隙间碎片：从「T1 掉落 + T2 钥匙 + T2 合成料」改写为「T2 正主产出 + 2→3 门槛」。

**Non-Goals**

- 激光运动、符纸弹种、召唤分身、傩面/露米娅——均见 proposal 的 Non-Goals。

## Decisions

### D1 命名：全面改名为 `yakumo_ran`

实体 id、类名、纹理常量、`BossCards` 方法与色盘常量、lang、JEI、`bosses.json`、配方 `effect`
全部改名为 `yakumo_ran` / `RAN_PALETTE` / `yakumoRan()`。理由：`kitsunebi` 是占位期名字，
与正式身份名实不符；占位期"路径按最终命名建立"的原则在此兑现。代价是存档里旧 `kitsunebi`
实体会变成未知实体（可接受，占位残影本就不该长期存在）。

### D2 8 张符卡与阈值

符卡数取 **8**（在 boss-dev skill 的 3~15 带内）。理由：蓝是 EX BOSS 出身，官方素材充足；
加卡是**内容多样性**而非难度（难度继续由 `damageScale` / `bossHits` 表达）。
阈值等分递减（首卡须 > 0.9）：

```
1.00 / 0.875 / 0.75 / 0.625 / 0.50 / 0.375 / 0.25 / 0.125
```

| # | 符卡（考据） | 意象 | 3D 写法（合法几何） | 依赖 |
|---|---|---|---|---|
| 1 | 式神「仙狐思念」 | 狐灵归附 | `AROUND_TARGET` 狐火朝玩家收拢 | — |
| 2 | 式神「前鬼後鬼の守護」 | 前后护卫 | 两轨相向 `AIMED` 扇 + `CAGE(gap>0)` | — |
| 3 | 式輝「狐狸妖怪レーザー」 | 狐妖激光 | `AROUND_TARGET` + `Projectile.laser`；**旋转星** | ★激光变更 |
| 4 | 式輝「四面楚歌チャーミング」 | 四面楚歌 | `CAGE` 合围 + `AIMED` 补刀，缺口随拍转 | ★激光变更（扫射） |
| 5 | 式神「十二神将の宴」 | 十二神将 | 12×`PILLAR_UP`（部分 `PLAYER_GROUND`），分批 | — |
| 6 | 式弾「ユーニラタルコンタクト」 | 片面接触 | 单侧 `AIMED` 重扇，留反向通道 | — |
| 7 | 地妖符「式神大星」 | 十字魔方阵 | `GRID_FACING` 十字点阵 / `DISC_RING` 逐颗点亮 | — |
| 8 | 幻神「飯綱権現降臨」 | 狐神降临（终局） | `LATTICE`(aimBias≈0.3) 激光网 + 狐火光晕 | 静态即可 |

**激光运动**：卡 3 的"旋转的激光星"、卡 4 的"扫射"需要光束绕枢轴旋转。激光**实体层**
已由 change `danmaku-laser` 实现（`bindToFrame` + 角速度曲线 + 平移，均以年龄为纯函数，
含延迟预警线随旋转、运动指纹覆盖）。但**轨道层尚未把姿态参数喂给它**——`Projectile` 无旋转
字段，`DanmakuEmitter.create()` 对激光只传静态参数（javadoc 明写"行为在此一律不施加"）。
本变更 SHALL 补这层接线（见 capability `danmaku-track-composition` 的「激光运动的轨道层表达」），
使卡表可直接声明激光旋转/扫射。

### D3 签名色盘

`RAN_PALETTE`（容量 6 ≥ 单卡最大并发轨数）：

| 色 | 值 | 归属意象 |
|---|---|---|
| 金 | `0xF2C14E` | 九尾 |
| 狐火青 | `0x66E8FF` | 狐火 / 默认攻击 |
| 白 | `0xF5F0E6` | 尾尖 / 白袍 / 符纸 |
| 靛蓝 | `0x2E3A8C` | 长襟 / 阴阳术 |
| 符纸黄 | `0xFFE066` | 符纸 / 符弹 |
| 紫 | `0x9C6BFF` | 隙间 / 终局 |

### D4 残影外观：暗化贴图 + 狐火自发光

「残影」= 暗化贴图（离线把 256×256 乘暗、降饱和）+ 狐火/符纸自发光层。理由：她既是
"残影"（暗），又要是九尾狐（狐火亮），暗底发光最贴角色；纯运行期 tint 不利于正式模型复用。

### D5 数值：T2 偏上，且为确定值

`bossTier = 2`、`referenceTier = 2`；秒数取 T2 带偏上、**确定值**。因 ±25% 随机会让"T2 偏上"
在实现层无法稳定表达，本决策**依赖** change `boss-stats-deterministic`：该变更落地后蓝的
秒数可直接写作确定值（约 300s，最终以实机回写）。挨弹数偏高（比同阶更耐打）。

### D6 隙间碎片定位：T2 正主产出 → 2→3 门槛

- 蓝的主题信物 = 隙间碎片（保底掉落）；
- 山女删除隙间碎片的硬掉；
- L2 蓝召唤钥匙改为 `fairy_nectar`（花精之滴，1 阶可达、互斥）；
- `spirit_core_2` 与 `grace_advance_2`/`refine_2` 移除隙间碎片（2 阶道具不依赖门槛料）；
- `fox_servant` 保留隙间碎片（主题卡使用主题信物）；
- 隙间碎片 = **预留 T3 门槛**（当前无 T3 消费者，同 `nightfall_core` 处境）。

## Open Questions

- **蓝的精确秒数/挨弹数**：待在 `boss-stats-deterministic` 落地后以 `/gs_boss spawn` 实测回写。
- **卡 3/4 的旋转激光**：实体层已就绪，本变更补**轨道层**接线后即可接入（不再用退化静态解）；旋转参数 MUST 在 `addFreshEntity` 之前写入。
- **与 change `boss-stats-deterministic` 对 `remnant-touhou-bosses` 同一需求的并发修改**：
  两者都改「BOSS 数值以秒带与挨弹带约束」。**归档顺序 MUST 为先 A 后本变更**，本变更的 delta
  按 A 已应用后的文本编写。
- **`hyakki-yagyo-summon` 的配方容量测试**：L2 换料后 Σcount 与互斥需重验（钥匙由
  `sukima_fragment` 换成 `fairy_nectar`，二者不得与其它配方钥匙冲突）。
