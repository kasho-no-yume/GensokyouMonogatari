package com.bitsson.gensokyou.spirit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * player-spellcard-quality：品质 × 灵力强度缩放回归。
 * 注入表与 config grace 表同构，单测不触 config 加载态。
 */
class SpellCardScalingTest {

    private static final List<String> TABLE = List.of(
            "1,spirit_power,1,0.2", "2,spirit_power,9,0.2", "3,spirit_power,80,0.2",
            "4,spirit_power,683,0.2", "5,spirit_power,5750,0.2");

    @Test
    void standardSpiritPowerMatchesGraceTable() {
        // Σ 1..N：1 / 10 / 90 / 773 / 6523
        assertEquals(1D, SpellCardScaling.standardSpiritPower(TABLE, 1), 1e-9);
        assertEquals(10D, SpellCardScaling.standardSpiritPower(TABLE, 2), 1e-9);
        assertEquals(90D, SpellCardScaling.standardSpiritPower(TABLE, 3), 1e-9);
        assertEquals(773D, SpellCardScaling.standardSpiritPower(TABLE, 4), 1e-9);
        assertEquals(6523D, SpellCardScaling.standardSpiritPower(TABLE, 5), 1e-9);
    }

    @Test
    void scalingIsSublinear() {
        // 伤害卡 α=0.9：S=1 vs S=6523 之比 = 6523^0.9，远低于线性 ×6523
        double low = SpellCardScaling.scaled(1D, 0.9D, 1D, 0D, 1.0E9D);
        double high = SpellCardScaling.scaled(1D, 0.9D, 6523D, 0D, 1.0E9D);
        assertEquals(1D, low, 1e-9);
        assertEquals(Math.pow(6523D, 0.9D), high, 1e-6);
        assertTrue(high / low < 6523D, "亚线性：比值必须远低于灵力强度线性比");
        // 恢复卡 α=0.2：总倍率约 ×5.8
        double healRatio = SpellCardScaling.scaled(1D, 0.2D, 6523D, 0D, 1.0E9D);
        assertEquals(Math.pow(6523D, 0.2D), healRatio, 1e-6);
        assertTrue(healRatio < high, "恢复卡增长必须明显低于伤害卡");
    }

    @Test
    void itemValueIsFixedByQuality() {
        // 道具固定值 = Base × S_std(品)^α，与玩家无关（itemValue 不接受玩家参数即结构保证）
        double v3 = SpellCardScaling.itemValue(3D, 0.22D, 3, TABLE, 0D, 1.0E9D);
        assertEquals(SpellCardScaling.scaled(3D, 0.22D, 90D, 0D, 1.0E9D), v3, 1e-9);
        assertEquals(3D, SpellCardScaling.itemValue(3D, 0.22D, 1, TABLE, 0D, 1.0E9D), 1e-9, "品 1 = Base");
    }

    @Test
    void multiplierClampBoundsExtremes() {
        // 上钳：1e9^1 被压到 maxFactor=4
        assertEquals(40D, SpellCardScaling.scaled(10D, 1D, 1.0E9D, 0.25D, 4D), 1e-9);
        // 下钳：S=0 时 0^1=0 被抬到 minFactor=0.25
        assertEquals(2.5D, SpellCardScaling.scaled(10D, 1D, 0D, 0.25D, 4D), 1e-9);
    }

    @Test
    void qualityTableIndexClamps() {
        List<Double> table = List.of(6D, 7D, 8D, 9D, 10D);
        assertEquals(6D, SpellCardScaling.tableEntry(table, 1), 1e-9);
        assertEquals(10D, SpellCardScaling.tableEntry(table, 5), 1e-9);
        assertEquals(6D, SpellCardScaling.tableEntry(table, 0), 1e-9, "品下界钳到 1");
        assertEquals(10D, SpellCardScaling.tableEntry(table, 9), 1e-9, "品上界钳到 5");
        assertEquals(0D, SpellCardScaling.tableEntry(List.of(), 3), 1e-9);
    }
}
