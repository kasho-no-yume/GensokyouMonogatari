package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.client.screen.GensokyoDebugMapScreen;
import net.minecraft.client.Minecraft;

public final class GensokyoDebugMapClient {
    private GensokyoDebugMapClient() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GensokyoDebugMapScreen());
    }
}
