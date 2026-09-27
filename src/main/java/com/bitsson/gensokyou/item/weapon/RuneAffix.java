package com.bitsson.gensokyou.item.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 单条增幅核词条：affixId 对应词条池配置中的效果类型，value 为区间内 roll 值。 */
public record RuneAffix(String affixId, float value) {

    public static final Codec<RuneAffix> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("affix_id").forGetter(RuneAffix::affixId),
                    Codec.FLOAT.fieldOf("value").forGetter(RuneAffix::value)
            ).apply(instance, RuneAffix::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RuneAffix> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RuneAffix::affixId,
            ByteBufCodecs.FLOAT, RuneAffix::value,
            RuneAffix::new);

    /**
     * 词条的 tooltip 行：按语义选单位 —— 玩家属性走 {@code attribute.gensokyou.<id>}，
     * 武器专有走 {@code affix.gensokyou.<id>}。
     *
     * <p>flat 属性用 {@code +0.5} 绝对值、比率属性用 {@code +2.8%}、
     * {@code danmaku_reduce} 反解成玩家直观的减伤倍率 {@code x2.00}。
     *
     * <p>增幅核自身与武器（装着该核时）两处 tooltip MUST 走本方法，
     * 否则两处数值格式会漂移。
     */
    public static net.minecraft.network.chat.MutableComponent tooltipLine(RuneAffix affix) {
        String id = affix.affixId();
        com.bitsson.gensokyou.spirit.attr.AttributeKey key =
                com.bitsson.gensokyou.spirit.attr.AttributeKey.byId(id);
        String value;
        if (key == com.bitsson.gensokyou.spirit.attr.AttributeKey.DANMAKU_REDUCE) {
            value = "x" + String.format(java.util.Locale.ROOT, "%.2f",
                    Math.pow(2D, Math.max(0F, affix.value())));
        } else if (key != null && key.isFlat()) {
            value = String.format(java.util.Locale.ROOT, "%+.1f", affix.value());
        } else {
            value = String.format(java.util.Locale.ROOT, "%+.1f%%", affix.value() * 100F);
        }
        String keyName = key != null ? key.langKey() : "affix.gensokyou." + id;
        return net.minecraft.network.chat.Component.translatable(keyName, value);
    }
}
