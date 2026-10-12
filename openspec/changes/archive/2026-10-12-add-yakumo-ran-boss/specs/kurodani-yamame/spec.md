## MODIFIED Requirements

### Requirement: 掉落与进度位

黑谷山女死亡时 SHALL 掉落配置数量的碎符卡星，并 SHALL **保底**掉落其主题信物土蜘蛛之丝
（`gensokyou:earth_spider_silk`）。

她 MUST NOT 再掉落隙间碎片（`gensokyou:sukima_fragment`）——隙间碎片的产出与
「2→3 门槛」定位已移交给 T2 正主八云蓝（见 capability `boss-signature-materials`）。
黑谷山女在其召唤链路中的角色 SHALL 由「隙间碎片的持有者」改为「土蜘蛛之丝的持有者」。

#### Scenario: 掉碎符卡星与土蜘蛛之丝
- **WHEN** 击杀黑谷山女
- **THEN** 至少掉落 1 个土蜘蛛之丝与配置数量的碎符卡星

#### Scenario: 不再掉隙间碎片
- **WHEN** 击杀黑谷山女
- **THEN** 其掉落中 MUST NOT 出现隙间碎片
