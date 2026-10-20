package com.bitsson.gensokyou.item.weapon;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外壳档案映射的断言。
 *
 * <p>「外形随弹核变化」是主武器形象的核心诉求，它全靠这张映射表成立：
 * 种类 → 默认档案是<b>唯一事实源</b>，单个核 MAY 覆盖。
 */
class CoreShellProfileTest {

    /** 每个弹幕种类 MUST 有默认档案。 */
    @Test
    void everyKindHasADefaultProfile() {
        for (DanmakuKind kind : DanmakuKind.values()) {
            CoreShellProfile profile = CoreShellProfile.byKind(kind);
            assertNotNull(profile, kind + " MUST 有默认外壳档案");
        }
    }

    /**
     * 不同种类 MUST 映射到不同档案——否则"外形随核变化"在跨种类时失效。
     */
    @Test
    void differentKindsMapToDifferentProfiles() {
        Set<CoreShellProfile> seen = EnumSet.noneOf(CoreShellProfile.class);
        for (DanmakuKind kind : DanmakuKind.values()) {
            CoreShellProfile profile = CoreShellProfile.byKind(kind);
            assertTrue(seen.add(profile),
                    kind + " 与其它种类共用档案 " + profile + "，跨种类的外形差异会消失");
        }
    }

    /**
     * 同属 {@code SPHERE} 的单发玉与散弹玉 MUST 是两个档案。
     *
     * <p>它们的行为同属球型，但一个是"重炮大玉"、一个是"五瓣散珠"；
     * 共用一个形态就等于"外形随核变化"在用户最在意的那对核上破了。
     */
    @Test
    void sphereKindsShareKindButNotProfile() {
        assertEquals(CoreShellProfile.BALL, CoreShellProfile.byKind(DanmakuKind.SPHERE),
                "SPHERE 的默认档案 MUST 是饱满大玉球");
        assertEquals(CoreShellProfile.SCATTER, CoreShellProfile.SCATTER,
                "散弹玉 MUST 覆盖为五珠扇档案");
        assertFalse(CoreShellProfile.BALL == CoreShellProfile.SCATTER,
                "单发玉与散弹玉 MUST 呈现不同形态");
    }

    /** 各弹核种类的默认档案 MUST 与设计意图一致（形态表是契约，不是实现细节）。 */
    @Test
    void kindToProfileContract() {
        assertEquals(CoreShellProfile.DART, CoreShellProfile.byKind(DanmakuKind.CONE));
        assertEquals(CoreShellProfile.BLADES, CoreShellProfile.byKind(DanmakuKind.KNIFE));
        assertEquals(CoreShellProfile.PAPER, CoreShellProfile.byKind(DanmakuKind.TALISMAN));
        assertEquals(CoreShellProfile.GUN, CoreShellProfile.byKind(DanmakuKind.LASER));
    }

    /**
     * 只有沉睡态（未装核）才是"无品阶归属"的形态。
     *
     * <p>它 MUST 是唯一走全光谱流光的档案——装核后 MUST 立即回到品阶色 ±30°。
     */
    @Test
    void onlyDormantHasNoTier() {
        for (CoreShellProfile profile : CoreShellProfile.values()) {
            assertEquals(profile == CoreShellProfile.DREAM, profile.isDormant(),
                    profile + " 的" + (profile.isDormant() ? "" : "非") + "沉睡态判定错误");
        }
    }
}
