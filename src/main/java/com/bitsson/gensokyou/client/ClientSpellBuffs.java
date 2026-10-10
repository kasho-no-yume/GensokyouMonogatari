package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 玩家符卡即时状态的客户端镜像（add-player-spellcards 5.3）：花瓣护盾余量与疫符窗口。
 * 由 {@code SpellBuffSyncPayload} 写入，本地按 tick 递减；花瓣数量驱动"环绕身边的花瓣"表现。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientSpellBuffs {

    private static volatile int petals;
    private static volatile int armorTicks;
    private static volatile int plagueTicks;

    private ClientSpellBuffs() {
    }

    public static void update(int newPetals, int newArmorTicks, int newPlagueTicks) {
        petals = newPetals;
        armorTicks = newArmorTicks;
        plagueTicks = newPlagueTicks;
    }

    public static int petals() {
        return armorTicks > 0 ? petals : 0;
    }

    public static boolean hasPlagueRepay() {
        return plagueTicks > 0;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (armorTicks > 0) {
            armorTicks--;
        }
        if (plagueTicks > 0) {
            plagueTicks--;
        }
        if (event.getEntity() instanceof Player player
                && player == Minecraft.getInstance().player) {
            emitPetals(player);
        }
    }

    /** 剩余花瓣真的环绕在玩家身边（樱花叶粒子按轨道相位布置）。 */
    private static void emitPetals(Player player) {
        int count = petals();
        if (count <= 0 || Minecraft.getInstance().level == null) {
            return;
        }
        double time = player.tickCount;
        for (int i = 0; i < count; i++) {
            double angle = time * 0.12D + i * (Math.PI * 2D / count);
            double radius = 0.95D;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = player.getY() + 1.0D + Math.sin(time * 0.1D + i) * 0.15D;
            Minecraft.getInstance().level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z, 0D, 0D, 0D);
        }
    }
}
