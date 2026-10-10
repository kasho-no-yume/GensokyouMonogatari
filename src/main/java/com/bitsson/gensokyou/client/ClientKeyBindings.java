package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyBindings {

    /** 符卡槽修饰键：按住 + 数字键 1..N 触发对应槽（可改键，默认 G）。 */
    public static final KeyMapping SKILL_MODIFIER = new KeyMapping(
            "key.gensokyou.skill_modifier", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.gensokyou");

    private ClientKeyBindings() {
    }

    @EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(SKILL_MODIFIER);
        }
    }
}
