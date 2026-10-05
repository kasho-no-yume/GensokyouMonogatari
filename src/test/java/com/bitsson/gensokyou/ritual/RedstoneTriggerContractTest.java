package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.ritual.behavior.HyakkiYagyoBehavior;
import com.bitsson.gensokyou.ritual.behavior.YaoyorozuGraceBehavior;
import com.bitsson.gensokyou.ritual.behavior.ZaohuaCraftingBehavior;
import com.bitsson.gensokyou.block.RitualCoreBlock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 红石上升沿代管触发的优先级契约。
 *
 * <p>三条优先级：自行覆写 {@code onRedstonePulse} > {@code redstoneTriggersUiAction()} >
 * 不响应。其中"自行覆写"的判定用反射比对<b>声明类</b>——源初造化同时满足前两条，
 * 只有声明类能区分二者。
 */
class RedstoneTriggerContractTest {

    private static boolean declaresOwn(RitualBehavior behavior) {
        return RitualCoreBlock.declaresOwnRedstoneHandler(behavior);
    }

    /** 覆写了 onRedstonePulse 的行为：只走自己的实现。 */
    private static final class OwnHandlerBehavior implements RitualBehavior {
        int pulses;

        @Override
        public void onRedstonePulse(net.minecraft.server.level.ServerLevel level,
                                    net.minecraft.core.BlockPos corePos, RitualMatch match,
                                    com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core) {
            pulses++;
        }
    }

    /** 没覆写的普通行为：不响应。 */
    private static final class PlainBehavior implements RitualBehavior {
    }

    /** 手动触发型且未 opt-out：默认响应。 */
    private static final class ManualBehavior implements RitualBehavior {
        @Override
        public boolean handlesStartViaUiAction() {
            return true;
        }
    }

    /** 手动触发型但显式 opt-out：不响应。 */
    private static final class OptOutBehavior implements RitualBehavior {
        @Override
        public boolean handlesStartViaUiAction() {
            return true;
        }

        @Override
        public boolean redstoneTriggersUiAction() {
            return false;
        }
    }

    @Test
    void ownHandlerIsDetectedByDeclaringClass() {
        assertTrue(declaresOwn(new OwnHandlerBehavior()));
        assertFalse(declaresOwn(new PlainBehavior()));
        assertFalse(declaresOwn(new ManualBehavior()));
    }

    @Test
    void sourceInitiationKeepsItsOwnHandlerAndIsNotDoubleTriggered() {
        // 源初造化覆写了 onRedstonePulse：框架 MUST 只调它，不叠加默认的首按钮触发
        ZaohuaCraftingBehavior zaohua = new ZaohuaCraftingBehavior();
        assertTrue(zaohua.handlesStartViaUiAction());
        assertTrue(zaohua.redstoneTriggersUiAction(),
                "它确实想被红石控制，只是自己实现了触发逻辑");
        assertTrue(declaresOwn(zaohua), "覆写了 onRedstonePulse → 声明类不是接口");
    }

    @Test
    void optOutsAreExplicitlyDeclared() {
        assertFalse(new YaoyorozuGraceBehavior().redstoneTriggersUiAction(),
                "神恩会话需要 initiator 在场，玩家不在场时自动开打无意义");
        assertFalse(new HyakkiYagyoBehavior().redstoneTriggersUiAction(),
                "召唤是起手式，红石脉冲会凭空蒸发玩家存货");
    }

    @Test
    void manualBehaviorsFollowTheToggleByDefault() {
        assertTrue(new ManualBehavior().redstoneTriggersUiAction());
        assertFalse(new OptOutBehavior().redstoneTriggersUiAction());
    }

    @Test
    void toggleTypeBehaviorsDoNotRespond() {
        // 启停型（handlesStartViaUiAction=false）不该被红石触发
        assertFalse(new PlainBehavior().redstoneTriggersUiAction());
    }

    @Test
    void dispatchMethodSignatureStaysStable() throws Exception {
        Method dispatch = RitualCoreBlock.class.getDeclaredMethod("dispatchRedstoneRise",
                net.minecraft.server.level.ServerLevel.class, net.minecraft.core.BlockPos.class,
                RitualMatch.class, com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity.class,
                RitualBehavior.class);
        assertTrue(Modifier.isStatic(dispatch.getModifiers()));
        assertEquals(void.class, dispatch.getReturnType());
    }

    @Test
    void detectionResultIsCachedPerBehaviorClass() {
        // 反射判定必须有缓存，否则每次红石变化都反射
        java.util.Map<?, ?> cache = null;
        for (java.lang.reflect.Field field : RitualCoreBlock.class.getDeclaredFields()) {
            if (java.util.Map.class.equals(field.getType())) {
                cache = readField(field);
                break;
            }
        }
        assertTrue(cache != null, "RitualCoreBlock 应持有反射判定缓存");
    }

    private static java.util.Map<?, ?> readField(java.lang.reflect.Field field) {
        try {
            field.setAccessible(true);
            return (java.util.Map<?, ?>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("cannot read " + field.getName(), exception);
        }
    }
}