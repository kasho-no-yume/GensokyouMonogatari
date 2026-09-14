package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.RitualCraftFxPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端造化演出状态：FLIGHT 起点收包后按结构包围盒程序化生成上升紫色粒子
 * （WITCH 主簇 + 紫 DUST 混喷），到期自灭；服务端零逐 tick 广播。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientCraftFxState {

    private static final Vector3f PURPLE = new Vector3f(0.62F, 0.32F, 0.86F);

    private static final class Fx {
        final BlockPos min;
        final BlockPos max;
        final long untilGameTime;
        double carry;

        Fx(BlockPos min, BlockPos max, long untilGameTime) {
            this.min = min;
            this.max = max;
            this.untilGameTime = untilGameTime;
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static ClientLevel lastLevel;

    private ClientCraftFxState() {
    }

    public static void add(RitualCraftFxPayload payload, ClientLevel level) {
        long now = level == null ? 0L : level.getGameTime();
        ACTIVE.add(new Fx(payload.min(), payload.max(), now + payload.durationTicks()));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ClientLevel level)) {
            return;
        }
        if (level != lastLevel) {
            ACTIVE.clear();
            lastLevel = level;
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        ACTIVE.removeIf(fx -> now >= fx.untilGameTime);
        double perTick = GensokyouConfig.ZAOHUA_RISING_PARTICLES_PER_SEC.get() / 20D;
        RandomSource random = level.getRandom();
        for (Fx fx : ACTIVE) {
            fx.carry += perTick;
            int count = (int) fx.carry;
            fx.carry -= count;
            for (int i = 0; i < count; i++) {
                double x = fx.min.getX() + random.nextDouble() * (fx.max.getX() + 1 - fx.min.getX());
                double y = fx.min.getY() + random.nextDouble() * (fx.max.getY() + 1 - fx.min.getY());
                double z = fx.min.getZ() + random.nextDouble() * (fx.max.getZ() + 1 - fx.min.getZ());
                if (random.nextFloat() < 0.6F) {
                    level.addParticle(ParticleTypes.WITCH, x, y, z, 0D, 0.06D, 0D);
                } else {
                    level.addParticle(new DustParticleOptions(PURPLE, 1.0F),
                            x, y, z, 0D, 0.08D, 0D);
                }
            }
        }
    }
}
