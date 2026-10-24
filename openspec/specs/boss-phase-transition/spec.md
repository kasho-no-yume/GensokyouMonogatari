# boss-phase-transition Specification

## Purpose
TBD - created by archiving change fix-boss-phase-switch-and-group-leak. Update Purpose after archive.
## Requirements
### Requirement: 切阶段演出的触发
系统 SHALL 在符卡切换发生时触发一次切阶段演出。演出的触发 SHALL 由「该 BOSS 的符卡下标发生变化」判定。

「开始观测该 BOSS 时补发当前符卡下标」SHALL NOT 触发演出；补发只更新状态。

演出 SHALL NOT 新增携带符卡名字符串的网络包；符卡名 SHALL 由既有的下标同步通路在客户端本地反查。

演出 SHALL 只影响表现层，MUST NOT 改变任何判定、伤害、符卡内容时序或符卡切换时机。

#### Scenario: 切换触发演出
- **WHEN** 服务端把某东方 BOSS 切换到新符卡，且客户端此前已持有该 BOSS 的旧符卡下标
- **THEN** 客户端在该次切换后播放一次切阶段演出

#### Scenario: 补发不触发演出
- **WHEN** 玩家开始观测某东方 BOSS，服务端补发其当前符卡下标，而客户端此前没有该 BOSS 的记录
- **THEN** 客户端只更新符卡下标，MUST NOT 播切阶段演出

#### Scenario: 不跨实体串演
- **WHEN** 场上有两只东方 BOSS 且各自处于不同符卡
- **THEN** 每只 BOSS 的演出 SHALL 独立触发，MUST NOT 由另一只的切换带动

#### Scenario: 演出不改判定
- **WHEN** 切阶段演出播放期间
- **THEN** 符卡切换时机、弹幕判定与伤害 SHALL 与无演出时一致

### Requirement: 屏幕符卡宣言
系统 SHALL 在屏幕中央偏上展开一次符卡宣言，内容 SHALL 包含该符卡名、一枚旋转的法阵与一枚朱印，并伴随一次短促的白闪。

符卡名 SHALL 走 lang 键反查，MUST NOT 硬编码；语言键缺失时 SHALL 跳过该次宣言，MUST NOT 显示原始键名。

宣言 SHALL 在有限时间内自行结束，MUST NOT 永久遮挡屏幕或阻断玩家输入。

宣言 SHALL 复用既有法阵与叠加渲染资源，MUST NOT 要求为每只 BOSS 单独制作立绘。

宣言的叠加元素 SHALL 使用加法混合或等效手段，MUST NOT 遮挡弹幕；系统 MUST NOT 在宣言期间整体压暗弹幕——弹幕在压暗期间仍然判伤，压暗会制造「看不清却挨打」的不可读局面。

#### Scenario: 宣言显示符卡名
- **WHEN** 切阶段演出播放
- **THEN** 屏幕中央偏上出现该符卡名，伴随法阵与朱印

#### Scenario: 缺键不显示原始键名
- **WHEN** 某符卡的 lang 键在当前语言文件中缺失
- **THEN** 客户端跳过该次宣言，MUST NOT 把原始键名当作符卡名画出来

#### Scenario: 宣言自行结束
- **WHEN** 一次符卡宣言播完
- **THEN** 屏幕元素在约定时长内消失，玩家输入与视野 SHALL 不受影响

#### Scenario: 宣言不遮挡弹幕
- **WHEN** 宣言播放期间屏幕上有弹幕
- **THEN** 弹幕 SHALL 仍然可见可辨，MUST NOT 被宣言遮住或被整体压暗到难以判读

#### Scenario: 英文环境显示英文名
- **WHEN** 客户端语言为 `en_us` 且发生符卡切换
- **THEN** 宣言中显示该符卡的英文名

### Requirement: BOSS 宣言姿态
系统 SHALL 在符卡切换时让该 BOSS 播放一次宣言姿态。宣言姿态 SHALL 由 BOSS 自身的表现能力提供（模型动画或等效表现），MUST NOT 以通用占位动画冒充。

无对应动画的 BOSS SHALL 退化为可辨识的等效表现，MUST NOT 静默无反应。

#### Scenario: 切换触发姿态
- **WHEN** 某东方 BOSS 切换到新符卡
- **THEN** 该 BOSS 在该次切换后播放一次宣言姿态

#### Scenario: 无动画时仍有表现
- **WHEN** 某 BOSS 没有可用的宣言动画
- **THEN** 仍 SHALL 出现可辨识的等效表现，MUST NOT 完全无反应

### Requirement: 切阶段音效
系统 SHALL 在符卡切换时播放一次声明音效。

音效 SHALL NOT 影响判定与伤害；音效播放失败 MUST NOT 打断演出或抛出到服务端。

在项目尚无自制音效资源时，系统 SHALL 使用登记在案的原版音效占位，并 MUST NOT 假定该占位是最终素材。

#### Scenario: 切换播放音效
- **WHEN** 某东方 BOSS 切换到新符卡
- **THEN** 客户端在该次切换后播放一次声明音效

#### Scenario: 音效不影响判定
- **WHEN** 声明音效播放期间发生弹幕命中
- **THEN** 命中判定与伤害结果与无音效时一致

