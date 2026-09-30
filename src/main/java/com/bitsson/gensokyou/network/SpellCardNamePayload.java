package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 符卡名下标：服务端在符卡切换时，向该血条的受众玩家单发当前符卡在表中的下标。
 *
 * <p><b>为什么只发下标、不发字符串</b>——客户端拿这个下标在<b>本地</b>的符卡表里
 * 反查 {@code name()}，于是 Component 永远不上线：省掉 {@link net.minecraft.network.chat.Component}
 * 的序列化带宽，也省掉「语言环境不同步导致的显示不一致」。包体 2 个 VAR_INT，约 1~3 字节。
 *
 * <p><b>为什么不走 {@code SynchedEntityData}</b>——理由与 {@link DanmakuAgePayload} 完全相同，
 * 不重复论证：配对 bundle 携带的 entityData 是 {@code ServerEntity} <b>构造时的快照</b>，
 * 在配对时刻 {@code set} 的字段要等下一次脏更新才到达客户端。
 *
 * <p><b>为什么必须补发</b>——切卡才发 ⇒ 晚进场的玩家从没见过任何包，血条下方会一直空白。
 * 而「空白符卡位」与「这只 BOSS 没有符卡」在画面上<b>完全无法区分</b>，是最难自查的一类
 * bug。故 {@code StartTracking} 时必须补发一次当前下标。
 *
 * <p>带宽：每「切卡 × 每受众玩家」约 1~3 字节。一场战斗 3~5 次切卡 ⇒ <b>持续带宽为零</b>。
 *
 * @param entityId BOSS 的实体 id（不是 UUID：血条判别路径已用实体查表，沿用 id 省一次查表）
 * @param cardIndex 符卡在 {@code BossCards} 表中的下标
 */
public record SpellCardNamePayload(int entityId, int cardIndex) implements CustomPacketPayload {

    public static final Type<SpellCardNamePayload> TYPE =
            new Type<>(Gensokyou.id("spellcard_name"));

    public static final StreamCodec<FriendlyByteBuf, SpellCardNamePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SpellCardNamePayload::entityId,
                    ByteBufCodecs.VAR_INT, SpellCardNamePayload::cardIndex,
                    SpellCardNamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
