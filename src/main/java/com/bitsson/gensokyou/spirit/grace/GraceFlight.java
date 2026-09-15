package com.bitsson.gensokyou.spirit.grace;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.SpiritPowerSyncPayload;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 超人类创造飞行（superhuman-flight）：授予/重申、阶级费率耗灵、枯竭即摔。
 *
 * <p>权限双通道：{@code NeoForgeMod.CREATIVE_FLIGHT} 属性（服务端 mayFly()/摔落豁免的
 * 官方推荐通道，NeoForge 明确不建议裸设 abilities.mayfly）+ {@code abilities.mayfly}
 * （经 {@link ServerPlayer#onUpdateAbilities()} 同步，驱动客户端空格双击起飞）。
 * 灵力枯竭撤权时两通道同撤——否则 mayFly() 仍真、摔落伤害被豁免，"直接摔"不成立。
 * 惯性开关的关闭态为客户端手感实现（见 client GraceFlightClient）；开关值本身
 * 持久于灵力附件（玩家态，不随核心）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GraceFlight {

    private static final ResourceLocation FLIGHT_MODIFIER =
            Gensokyou.id("grace_creative_flight");

    private GraceFlight() {
    }

    /** 授予/重申飞行权限（阶级≥1、池仍可支付或 5 阶免费；登录/重生/换维度/进阶/回灵复飞共用）。 */
    public static void applyPermission(ServerPlayer player) {
        SpiritPowerData data = ModAttachments.get(player);
        int tier = data.temperLevel();
        boolean vanillaOverride = player.isCreative() || player.isSpectator();
        boolean granted = tier >= 1 && !vanillaOverride && canAffordToTakeoff(tier, data);
        var flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null) {
            if (granted) {
                flight.addOrReplacePermanentModifier(new AttributeModifier(FLIGHT_MODIFIER,
                        1D, AttributeModifier.Operation.ADD_VALUE));
            } else if (!vanillaOverride) {
                flight.removeModifier(FLIGHT_MODIFIER);
            }
        }
        if (granted && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    /** 起飞可支付判据：5 阶免费恒可；其余需池余额 > 0（凡人 0 池自然不可）。 */
    private static boolean canAffordToTakeoff(int tier, SpiritPowerData data) {
        return GraceNumbers.flightCostPctPerSecond(tier) <= 0D || data.current() > 0F;
    }

    /** 枯竭撤权：flying/mayfly/属性全撤 → 原版重力自然坠落（含摔落伤害）。 */
    public static void revoke(ServerPlayer player) {
        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;
        player.onUpdateAbilities();
        var flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null) {
            flight.removeModifier(FLIGHT_MODIFIER);
        }
    }

    public static void toggleInertia(ServerPlayer player) {
        setInertia(player, !ModAttachments.get(player).flightInertia());
    }

    public static void setInertia(ServerPlayer player, boolean inertia) {
        ModAttachments.set(player, ModAttachments.get(player).withFlightInertia(inertia));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyPermission(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyPermission(player);
        }
    }

    /** 耗灵主循环：仅生存/冒险的 grace 飞行计费；小数进位累积，付不起当期即摔。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        SpiritPowerData data = ModAttachments.get(player);
        int tier = data.temperLevel();
        if (tier <= 0) {
            return;
        }
        if (!player.getAbilities().flying) {
            if (!player.getAbilities().mayfly && canAffordToTakeoff(tier, data)) {
                applyPermission(player); // 回灵后复飞（含坠落途中）
            }
            return;
        }
        double perTick = GraceNumbers.flightCostPerTick(tier, data.max());
        if (perTick <= 0D) {
            return; // 5 阶免费飞行
        }
        float buffer = data.flightBuffer() + (float) perTick;
        float whole = (float) Math.floor(buffer);
        if (whole >= 1F) {
            if (data.current() < whole) {
                ModAttachments.set(player, data.withCurrent(0F).withFlightBuffer(0F));
                revoke(player);
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.grace_flight_fallen"), true);
                return;
            }
            buffer -= whole;
            data = data.withAddedCurrent(-whole);
        } else if (data.current() <= 0F) {
            ModAttachments.set(player, data.withFlightBuffer(0F));
            revoke(player);
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.grace_flight_fallen"), true);
            return;
        }
        ModAttachments.set(player, data.withFlightBuffer(buffer));
    }

    /** 惯性开关 + 阶级随灵力同步一并下发（客户端 HUD 槽数与飞行手感共用通道）。 */
    public static void syncGraceState(ServerPlayer player) {
        SpiritPowerData data = ModAttachments.get(player);
        PacketDistributor.sendToPlayer(player, new SpiritPowerSyncPayload(
                Math.round(data.current()), Math.round(data.max()),
                data.temperLevel(), data.flightInertia()));
    }
}
