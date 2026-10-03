package com.bitsson.gensokyou.danmaku.track;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 激光两种形态的声明级契约。
 *
 * <p><b>被遮挡形态 MUST NOT 被移除</b> —— 它是默认形态，且不是遗留物：
 * 「可被掩体规避」是一类真实设计。本类守住「两种形态并存」这条结构，
 * 免得日后有人为了「统一」把裁剪路径删掉。
 *
 * <p>本类只测声明层（{@link Projectile}）。<b>逐形态长度不变量</b>
 * （视觉长度 == 判伤长度）需要 {@code LaserDanmaku} 的世界查询，
 * 属 {@code danmaku-talisman-target} 任务 5.8 记录的那个脚手架缺口。
 */
class ProjectileLaserVariantTest {

    @Test
    @DisplayName("默认是被遮挡形态（穿墙必须显式声明）")
    void defaultIsOccluded() {
        Projectile laser = Projectile.laser(80.0D, 0.8D, 2.0D, 6.0D);
        assertTrue(laser.isLaser());
        assertFalse(laser.laserPiercesBlocks(),
                "默认 MUST 是被遮挡形态 —— 在役符卡行为逐位不变靠的就是这条");
    }

    @Test
    @DisplayName("穿墙形态可显式声明，且两种形态的参数完全相同")
    void piercingIsExplicitAndOtherwiseIdentical() {
        Projectile occluded = Projectile.laser(80.0D, 0.8D, 2.0D, 6.0D);
        Projectile piercing = Projectile.piercingLaser(80.0D, 0.8D, 2.0D, 6.0D);

        assertTrue(piercing.laserPiercesBlocks());
        assertNotEquals(occluded.laserPiercesBlocks(), piercing.laserPiercesBlocks(),
                "两种形态 MUST 可区分");
        // 除形态外一切相同 —— 否则「同一张卡换个形态」会顺带改玩法数值
        assertEquals(occluded.kind(), piercing.kind());
        assertEquals(occluded.laserLength(), piercing.laserLength());
        assertEquals(occluded.laserRadius(), piercing.laserRadius());
        assertEquals(occluded.laserDelaySeconds(), piercing.laserDelaySeconds());
        assertEquals(occluded.laserDurationSeconds(), piercing.laserDurationSeconds());
    }

    @Test
    @DisplayName("四参重载与五参重载的默认行为一致")
    void fourArgOverloadEqualsExplicitOccluded() {
        assertEquals(Projectile.laser(80.0D, 0.8D, 2.0D, 6.0D, false),
                Projectile.laser(80.0D, 0.8D, 2.0D, 6.0D));
        assertEquals(Projectile.laser(80.0D, 0.8D, 2.0D, 6.0D, true),
                Projectile.piercingLaser(80.0D, 0.8D, 2.0D, 6.0D));
    }

    @Test
    @DisplayName("球弹的形态位恒为 false，且不受激光重载影响")
    void sphereHasNoPiercing() {
        assertFalse(Projectile.sphere().isLaser());
        assertFalse(Projectile.sphere().laserPiercesBlocks(),
                "球弹没有形态概念 —— 该位恒为 false");
        assertEquals(Projectile.SPHERE, Projectile.sphere());
    }

    @Test
    @DisplayName("形态位不改变参数的合法性夹取")
    void piercingDoesNotBypassValidation() {
        // 两个形态走同一条夹取路径 —— 否则穿墙激光会绕过下限检查。
        for (boolean pierces : new boolean[]{false, true}) {
            Projectile laser = Projectile.laser(-5.0D, 0.0D, -1.0D, 0.0D, pierces);
            assertEquals(1.0D, laser.laserLength(), 1.0E-9D, "长度下界 1");
            assertEquals(0.05D, laser.laserRadius(), 1.0E-9D, "半径下界 0.05");
            assertEquals(0.0D, laser.laserDelaySeconds(), 1.0E-9D, "延迟下界 0");
            assertEquals(1.0D, laser.laserDurationSeconds(), 1.0E-9D, "持续下界 1");
            assertEquals(pierces, laser.laserPiercesBlocks(), "形态位原样透传");
        }
    }

    @Test
    @DisplayName("在役的喷泉激光保持被遮挡形态")
    void shippedFountainLaserStaysOccluded() {
        // 在役符卡（喷泉激光 80 格）用的是四参重载 ⇒ 必为被遮挡。
        // 这条是「在役行为逐位不变」的回归锁：若有人把它改成穿墙，此条失败。
        Projectile fountainLaser = BossCards.bigFairy().stream()
                .flatMap(card -> card.tracks().stream())
                .flatMap(track -> track.beats().stream())
                .map(Track.Beat::projectile)
                .filter(java.util.Objects::nonNull)
                .filter(Projectile::isLaser)
                .findFirst()
                .orElseThrow(() -> new AssertionError("大妖精符卡表里没有激光"));
        assertFalse(fountainLaser.laserPiercesBlocks(),
                "在役的喷泉激光 MUST 仍是被遮挡形态");
    }

    @Test
    @DisplayName("激光形态位是弹种属性，与几何形状正交")
    void variantIsOrthogonalToShape() {
        // 同一个形状（DISC_RING）既能被遮挡激光发，也能被穿墙激光发 ——
        // 形态是「弹种」的属性，不是「几何」的属性。
        Shape.Params ring = Shape.Params.defaults().count(8).radius(4.0D).speed(0.3D);
        Track occludedRing = Track.of("t", 0xFFFFFF).terminates()
                .at(0, Shape.DISC_RING, ring, Behaviour.NONE, TargetMode.SELF_AXIS,
                        Projectile.laser(20.0D, 0.3D, 1.0D, 2.0D))
                .build();
        Track piercingRing = Track.of("t", 0xFFFFFF).terminates()
                .at(0, Shape.DISC_RING, ring, Behaviour.NONE, TargetMode.SELF_AXIS,
                        Projectile.piercingLaser(20.0D, 0.3D, 1.0D, 2.0D))
                .build();

        assertEquals(occludedRing.beats().get(0).shape(), piercingRing.beats().get(0).shape());
        assertFalse(occludedRing.beats().get(0).projectile().laserPiercesBlocks());
        assertTrue(piercingRing.beats().get(0).projectile().laserPiercesBlocks());
    }
}