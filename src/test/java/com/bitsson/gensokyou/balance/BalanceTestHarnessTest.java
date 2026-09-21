package com.bitsson.gensokyou.balance;

import com.bitsson.gensokyou.entity.TestBossPattern;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数值测试台纯逻辑（add-balance-test-harness）：
 * BOSS 标定公式（TTK ≥ 2 分钟；min &lt; max）与十种模式的基础几何。
 */
class BalanceTestHarnessTest {

    @Test
    void minVariantMeetsTwoMinuteFloor() {
        double dps = 1234.5;
        double minHp = TestBossTuning.hp(dps, 120D);
        double maxHp = TestBossTuning.hp(dps, 180D);
        assertEquals(120D, TestBossTuning.ttkSeconds(minHp, dps), 1e-6, "min 变体 TTK 应 ≥ 2 分钟");
        assertTrue(TestBossTuning.ttkSeconds(maxHp, dps) > TestBossTuning.ttkSeconds(minHp, dps),
                "max 变体应比 min 更耐打");
    }

    @Test
    void damageScalesByAllowedHits() {
        double ehp = 5000D;
        assertTrue(TestBossTuning.damage(ehp, 7) > TestBossTuning.damage(ehp, 10),
                "hits 越少单发越痛（max 变体更强）");
        assertEquals(ehp / 10D, TestBossTuning.damage(ehp, 10), 1e-6);
    }

    @Test
    void tenPatternsWithBasicGeometry() {
        assertEquals(10, TestBossPattern.COUNT);
        assertEquals(10, TestBossPattern.values().length);

        Vec3 east = TestBossPattern.horiz(0D);
        assertEquals(1.0D, east.x, 1e-9);
        assertEquals(0.0D, east.z, 1e-9);
        Vec3 south = TestBossPattern.horiz(90D);
        assertEquals(0.0D, south.x, 1e-9);
        assertEquals(1.0D, south.z, 1e-9);
        assertEquals(0.0D, south.y, 1e-9, "水平环不带垂直分量");

        Vec3 rotated = TestBossPattern.rotY(east, 90D);
        assertEquals(0.0D, rotated.x, 1e-9);
        assertEquals(-1.0D, rotated.z, 1e-9, "绕 Y 轴 90° 把 +X 转向 -Z（与弹幕扇形同约定）");
    }
}
