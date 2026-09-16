# ritual-runtime-fx (delta)

## MODIFIED Requirements

### Requirement: 特效参数配置化与资产管道
火焰场的采样密度/火舌尺寸/辉光强度与脉动速率、雾带宽与层数、闪电分段与重掷频率、灵气球呼吸幅度/速率、各贴图 uv 滚动速率 SHALL 全部为 `GensokyouConfig`（COMMON）项，代码内 MUST NOT 硬编码魔数。新增 fx 贴图（火床、火舌、雾带、闪电芯/晕）SHALL 经 `tools/gen_tex.py` 数据管道产出，MUST NOT 使用坐标循环脚本。

#### Scenario: 调参不重编译
- **WHEN** 修改任一特效密度/速率配置项并重载
- **THEN** 运行态表现随之变化，无需改动代码
