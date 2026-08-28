## 1. 基建：数据/同步/HUD

- [x] 1.1 移除阶段 A 遗留的 [gensokyou-debug] 日志
- [x] 1.2 GensokyouConfig 新增 power 节（池/电容/发电机/中继/MuPower/冰卡/Cirno）
- [x] 1.3 SpiritPowerData Attachment 注册 + Clone 死亡清零 + 登录/重生/换维同步
- [x] 1.4 SpiritPowerSyncPayload S2C 包注册与客户端缓存
- [x] 1.5 HUD 灵力条图层渲染
- [x] 1.6 服务端自然回复 tick

## 2. 电容与发电机

- [x] 2.1 ModBlockEntities 注册器 + 电容方块实体（存取/NBT）
- [x] 2.2 电容方块交互（充能/存入/回显）
- [x] 2.3 发电机核心 BE（结构校验复用、产能、推送相邻电容）
- [x] 2.4 新方块资产（模型/blockstate/item 模型）+ 提取脚本映射补充

## 3. 传输与加工

- [x] 3.1 灵力中继方块+BE（两步绑定/周期搬运/解绑/失效保护）
- [x] 3.2 加工配方 JSON 监听器（AddReloadListenerEvent）+ 四条默认配方文件
- [x] 3.3 加工核心方块+BE（投料/扣款/计时/产出）

## 4. 淬炼

- [x] 4.1 淬炼祭坛方块（结构校验/供品/双段支付/成长/提示）
- [x] 4.2 成长持久化验证路径（Clone 规则覆盖）

## 5. 战斗扩展

- [x] 5.1 MuPower 效果注册 + 受击倍率接入 DamageEventHandler
- [x] 5.2 冰符「冰击」物品（扇形弹幕）
- [x] 5.3 CirnoEntity + cirno_catalyst + 合成配方 + 渲染注册

## 6. 收尾

- [x] 6.1 zh_cn/en_us 全部新条目
- [x] 6.2 gradlew build 通过 + 冒烟清单自测（充电→发电→传输→加工→淬炼闭环）
