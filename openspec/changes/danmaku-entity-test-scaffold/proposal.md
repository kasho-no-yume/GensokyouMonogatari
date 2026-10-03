# 为弹幕实体建立可构造的测试脚手架

## Why

三份已归档变更留下了 **7 条「实现已就位但无断言」的验收**，它们的根因是同一个：

> **本仓库没有能构造 `Level` 与实体的测试脚手架。**

`MinecraftTestBootstrap` 只起注册表；全仓**没有任何测试构造过 `Entity`**
（`grep` 全树确认）。于是凡是「构造一枚弹 → 推进若干 tick → 读存档/读同步位」才能验的
断言，都只能停在「实现就位」这一步。

这些断言不是可有可无的礼节 —— 它们各自盯的是一条**已经写进主 specs 的 requirement**，
而该 requirement 目前**没有任何可执行证据**：

| 来自 | 断言 | 守护的 requirement |
|---|---|---|
| `danmaku-talisman-target` 5.2 | 两个残缺存档（有种无段 / 有段无种子）读档不抛异常 | `目标状态 SHALL 纳入存档恢复` |
| `danmaku-leg-motion` 5.4 | 重载后轨迹与从未卸载时逐位相同 | `重载后轨迹 MUST 与从未卸载时逐位相同` |
| `danmaku-leg-motion` 6.4 | 逐形态不变量：视觉长度 == 判伤长度 | `激光长度 ... 逐形态` |
| `danmaku-leg-motion` 7.4 | 两条降级路径在换向后第一 tick 位置相同 | `段类型 SHALL 显式区分方向来源` |
| `danmaku-leg-motion` 7.5 | `TARGET` 段重载后不自行求解方向 | 同上 |

**为什么现在做**：这些 requirement 已在主 specs 里生效，而守护它们的断言不存在。
再拖下去，下一个改动会以为「那几条已经验过了」。

**为什么单独立项**：它的服务对象**不止本轮三份变更** ——
`fix-ring-card-geometry` 的环卡重载轨迹、`danmaku-timeline-sync` 的年龄连续性
都需要同一套脚手架。混进某一份变更会让那份变更的归档状态变得不诚实。

## 归属与工具链说明（两条实测记录，勿轻易搬动）

**1. 本变更的 spec delta 挂在 `danmaku-pipeline-capacity` 下，
而本该是全新的 capability `danmaku-test-scaffold`。**

实测记录：`openspec validate --strict` 对**尚无主 spec 的全新 capability 文件夹**
会误报 `ADDED "..." must contain SHALL or MUST` —— 即便标题明写 SHALL、
即便正文改为纯 ASCII、即便照抄一条既有 spec 的标题，均复现。已排除内容因素。
故暂挂在已存在的 capability 下（它本就涵盖「准入判据」与「可观测」）。

**2. `openspec validate --strict` 的正文检查只看 requirement 的首段。**
实测：正文首段为不含 SHALL/MUST 的一句话时失败；把含 MUST 的句子提到首段即通过 ——
即使后续段落也含 SHALL/MUST。这解释了本轮归档时若干 delta 反复报同一错的原因。

两条都是工具链行为，不是 spec 内容问题。若校验器修复，应把本变更的 delta
搬回 `danmaku-test-scaffold`。

## What Changes

一套测试专用的最小 `Level` 实现 + 实体构造与 tick 推进工具，使下列四类断言可写：
1. **存档往返**：构造弹 → 推进 → `addAdditionalSaveData` / `readAdditionalSaveData`
   → 逐键比对，包括**残缺存档**与**旧存档缺键**两种降级。
2. **重载轨迹一致性**：构造 → 推进 200 tick 记录轨迹 → 模拟读档
   （`restoredAge` / `tickCount`）→ 再推进 200 tick ⇒ 断言逐位相同。
3. **世界查询相关**：激光的方块裁剪（`level.clip` 需要真实方块状态）。
4. **同步位往返**：accessor 写值 → `SynchedEntityData` 打包 → 解包 → 比对。

**明确不做**：真实多人同步验证（那需要两个进程，超出单元测试）。
跨进程一致性靠实机双客户端对照。

## Non-Goals

- 不改任何生产代码的行为。若脚手架暴露出实现缺陷，
  那是一个**独立的缺陷修复变更**，MUST NOT 混进本变更。
- 不追求覆盖全部 `Level` 抽象方法 —— 只实现被测路径真正用到的那部分，
  未实现的方法 `throw new UnsupportedOperationException`（而非返回假值，
  假值会让断言在错误的通过路径上变绿）。

## 验收

脚手架本身 MUST 有它自己的测试，且那些测试 MUST 包含**至少一条会失败的** ——
证明它真的能构造实体、能推进 tick、能触发存档读写。
一个从不失败的脚手架无法证明任何事。
