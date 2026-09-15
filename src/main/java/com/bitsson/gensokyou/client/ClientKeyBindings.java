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

    public static final KeyMapping SKILL_SLOT_1 = new KeyMapping(
            "key.gensokyou.skill1", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.gensokyou");
    public static final KeyMapping SKILL_SLOT_2 = new KeyMapping(
            "key.gensokyou.skill2", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.gensokyou");
    public static final KeyMapping SKILL_SLOT_3 = new KeyMapping(
            "key.gensokyou.skill3", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.gensokyou");
    public static final KeyMapping SKILL_SLOT_4 = new KeyMapping(
            "key.gensokyou.skill4", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.gensokyou");
    public static final KeyMapping SKILL_SLOT_5 = new KeyMapping(
            "key.gensokyou.skill5", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.gensokyou");

    private ClientKeyBindings() {
    }

    @EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(SKILL_SLOT_1);
            event.register(SKILL_SLOT_2);
            event.register(SKILL_SLOT_3);
            event.register(SKILL_SLOT_4);
            event.register(SKILL_SLOT_5);
        }
    }
}
