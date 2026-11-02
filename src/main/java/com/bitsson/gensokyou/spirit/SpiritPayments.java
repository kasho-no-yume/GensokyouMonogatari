package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.balance.SpiritPaymentMath;
import net.minecraft.server.level.ServerPlayer;

/** Single shared payment channel for weapon and learned spell fractional costs. */
public final class SpiritPayments {
    private SpiritPayments() { }

    public static boolean pay(ServerPlayer player, double cost) {
        SpiritPowerData data = ModAttachments.get(player);
        var payment = SpiritPaymentMath.pay(data.current(),
                player.getData(ModAttachments.SPIRIT_PAYMENT_REMAINDER.get()), cost);
        if (!payment.affordable()) return false;
        player.setData(ModAttachments.SPIRIT_PAYMENT_REMAINDER.get(), payment.remainder());
        ModAttachments.set(player, data.withCurrent(payment.balance()));
        return true;
    }
}
