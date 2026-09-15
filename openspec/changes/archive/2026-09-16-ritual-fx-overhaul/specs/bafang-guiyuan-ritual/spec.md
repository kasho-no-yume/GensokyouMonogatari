# bafang-guiyuan-ritual (delta)

## ADDED Requirements

### Requirement: 运行态灵气球
仪式 enabled 运行期间，核心正上方 SHALL 悬浮呈现绿色**灵气球**：fresnel 边缘光 shader 球体（边缘亮、内部半透、加法光感），以程序化呼吸（缩放正弦起伏）呈现"活"的质感；悬浮高度与球半径 SHALL 随阶级增大（半径阶级单调不减），呼吸幅度与速率进配置。非 enabled（停机/未启动/不成型）时 MUST 零呈现。灵气球 SHALL 由客户端 BER 逐帧本地绘制，运行态与阶级经共享渲染态通道（kind=bafang，仅 enabled+tier）下发，MUST NOT 为此新增独立 payload 或逐 tick 包。球体参数 SHALL 由单一装配点计算（`阶级 → 半径/高度`），为后续"水位联动表现"预留数据位，MUST NOT 将装配点散写多处。

#### Scenario: 阶级放大灵气球
- **WHEN** 同一仪式分别在阶级 0 与阶级 5 下运行
- **THEN** 阶级 5 的灵气球半径明显更大、悬浮更高，呼吸节奏可见

#### Scenario: 停机即灭
- **WHEN** 玩家按下停止按钮
- **THEN** 灵气球消失（淡出，MUST NOT 单帧硬切）

#### Scenario: 通道复用零新包
- **WHEN** 八方归元首次启用灵气球表现
- **THEN** 其渲染态经既有共享通道下发，网络行为满足"稳态零包、变化各推一次"，不引入周期性特效包
