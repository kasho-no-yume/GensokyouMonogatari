package com.bitsson.gensokyou.danmaku.motion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code SEGMENTED} 档的判据。
 *
 * <p><b>本类的核心是那对刻意的解耦</b>：段式运动
 * {@link DanmakuTrackKinds#survivesReloadWithoutVelocity} 为 {@code true}
 * （速度可由种子与段表解出），而
 * {@link DanmakuTrackKinds#needsVelocityPersistence} <b>同时</b>为 {@code true}。
 * 两者看似矛盾，实则理由不同 —— 前者是「能不能算出来」，后者是「算的时机对不对」。
 * 若有人看到矛盾就把后者改成 {@code false}，段式弹读档后会因顺序依赖而失控。
 */
class DanmakuTrackKindsSegmentedTest {

    @Test
    @DisplayName("段式运动是独立的一档，不是 CLOSED_FORM 的别名")
    void segmentedIsItsOwnForm() {
        assertEquals(DanmakuTrackKinds.Form.SEGMENTED,
                DanmakuTrackKinds.formOf(false, false, false, true));
        assertEquals(DanmakuTrackKinds.Form.INCREMENTAL,
                DanmakuTrackKinds.formOf(false, false, false, false),
                "未挂段式运动时 MUST 仍是旧档位");
        assertNotEquals(DanmakuTrackKinds.Form.CLOSED_FORM, DanmakuTrackKinds.Form.SEGMENTED);
    }

    @Test
    @DisplayName("位置是累加的（所以不是 CLOSED_FORM），但速度不是")
    void segmentedIsIncrementalInPositionOnly() {
        // CLOSED_FORM 与 SEGMENTED 的分界在于速度能否脱离上一 tick：
        // 编队帧/速率曲线能直接覆写位置，段式运动只能 pos += v。
        assertEquals(DanmakuTrackKinds.Form.SEGMENTED,
                DanmakuTrackKinds.formOf(false, false, false, true));
        // 段式 + 速率曲线 / 编队帧：位置被更强的东西接管，仍判 CLOSED_FORM
        assertEquals(DanmakuTrackKinds.Form.CLOSED_FORM,
                DanmakuTrackKinds.formOf(false, true, false, true));
        assertEquals(DanmakuTrackKinds.Form.CLOSED_FORM,
                DanmakuTrackKinds.formOf(false, false, true, true));
    }

    @Test
    @DisplayName("段式运动缺 deltaMovement 时能自愈（速度是种子的纯函数）")
    void segmentedSurvivesReloadWithoutVelocity() {
        assertTrue(DanmakuTrackKinds.survivesReloadWithoutVelocity(false, false, false, true),
                "vᵢ = dir(seedᵢ)·speedᵢ 只由种子与段表决定 ⇒ 可重算");
    }

    @Test
    @DisplayName("但仍写速度：顺序依赖，不是「不可推导」")
    void segmentedStillPersistsVelocityDespiteBeingDerivable() {
        // 这条是本类最重要的一条：两个判据刻意不同。
        // 理由是服务端从存档 pos 继续，读侧重算速度必须先于位置积分，
        // 而 velocityPersistenceNeeded() 依赖「是否挂了段式运动」——
        // 那是个读档期顺序问题，不是可推导性问题。
        assertTrue(DanmakuTrackKinds.needsVelocityPersistence(false, false, false, true),
                "段式运动 MUST 写速度（顺序依赖）");
        assertTrue(DanmakuTrackKinds.needsVelocityPersistence(false, false, false, false),
                "裸直线弹同样写速度 —— pos += velocity，丢了就冻结（既有行为）");
        assertFalse(DanmakuTrackKinds.needsVelocityPersistence(false, true, false, true),
                "挂了速率曲线后位置不由速度决定 ⇒ 不写（且段式 MUST NOT 把它改回 true）");
        assertFalse(DanmakuTrackKinds.needsVelocityPersistence(false, false, true, true),
                "挂了编队帧后位置每 tick 被覆写 ⇒ 不写");
    }

    @Test
    @DisplayName("三参重载与四参重载在未挂段式运动时等价")
    void legacyOverloadsDelegate() {
        for (boolean curving : new boolean[]{false, true}) {
            for (boolean profile : new boolean[]{false, true}) {
                for (boolean frame : new boolean[]{false, true}) {
                    assertEquals(DanmakuTrackKinds.formOf(curving, profile, frame),
                            DanmakuTrackKinds.formOf(curving, profile, frame, false));
                    assertEquals(
                            DanmakuTrackKinds.survivesReloadWithoutVelocity(curving, profile, frame),
                            DanmakuTrackKinds.survivesReloadWithoutVelocity(curving, profile, frame, false));
                    assertEquals(
                            DanmakuTrackKinds.needsVelocityPersistence(curving, profile, frame),
                            DanmakuTrackKinds.needsVelocityPersistence(curving, profile, frame, false));
                }
            }
        }
    }

    @Test
    @DisplayName("写盘与读盘往返：段式弹的速度能被取回")
    void velocityRoundTripsForSegmented() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.world.phys.Vec3 velocity = new net.minecraft.world.phys.Vec3(0.3D, -0.1D, 0.4D);
        DanmakuTrackKinds.writeVelocity(tag, true, velocity);
        assertTrue(tag.contains(DanmakuTrackKinds.KEY_VELOCITY));
        assertEquals(velocity, DanmakuTrackKinds.readVelocity(tag, true));
    }

    @Test
    @DisplayName("不需要速度时既不写也不读")
    void velocityOmittedWhenNotNeeded() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        DanmakuTrackKinds.writeVelocity(tag, false, new net.minecraft.world.phys.Vec3(1, 2, 3));
        assertFalse(tag.contains(DanmakuTrackKinds.KEY_VELOCITY), "不该占那 6 个键");
        assertEquals(net.minecraft.world.phys.Vec3.ZERO,
                DanmakuTrackKinds.readVelocity(tag, false));
    }
}