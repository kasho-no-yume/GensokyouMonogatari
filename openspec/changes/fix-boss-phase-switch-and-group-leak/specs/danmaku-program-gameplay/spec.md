## ADDED Requirements

### Requirement: 碰撞半径倍率的适用形态
碰撞半径的平衡倍率 SHALL 只作用于弹射物型弹幕。激光型弹幕的判定半径 SHALL 取其声明的碰撞半径，MUST NOT 再乘该倍率。

激光的判定半径 SHALL 与其视觉粗细口径一致，MUST NOT 出现判定窗口窄于视觉粗细一半的情形。

该倍率的调整 MUST NOT 影响视觉半径；客户端绘制 SHALL 继续使用视觉半径。

#### Scenario: 激光判定不打折
- **WHEN** 某激光成员声明碰撞半径 0.4
- **THEN** 其判定使用的半径为 0.4，MUST NOT 为 0.4 再乘以弹射物型弹幕的倍率

#### Scenario: 普通弹判定维持倍率
- **WHEN** 某弹射物型弹幕成员声明碰撞半径 0.4
- **THEN** 其判定使用的半径维持现行倍率结果，MUST NOT 因激光分流而改变

#### Scenario: 改判定不动画面
- **WHEN** 激光的判定半径发生变化
- **THEN** 客户端绘制的激光粗细 SHALL 保持不变
