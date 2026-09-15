package com.bitsson.gensokyou.spirit.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家属性容器数据：attrId → (sourceId → 贡献值)。
 *
 * <p>permanent 层随存档持久化（copyOnDeath）；temp 层承载变身等限时改写，
 * 不入 Codec（加载后恒为空，到期或下线整层丢弃即恢复）。
 * 未知 attrId 允许驻留数据但读取侧因注册表查不到而失效（表外属性不生效）。
 */
public record PlayerAttributesData(Map<String, Map<String, Float>> permanent,
                                   Map<String, Map<String, Float>> temp) {

    private static final Codec<Map<String, Map<String, Float>>> LAYERS =
            Codec.unboundedMap(Codec.STRING,
                    Codec.unboundedMap(Codec.STRING, Codec.FLOAT));

    public static final Codec<PlayerAttributesData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    LAYERS.optionalFieldOf("attr", Map.of())
                            .forGetter(PlayerAttributesData::permanent)
            ).apply(instance, saved -> new PlayerAttributesData(saved, new HashMap<>())));

    public static PlayerAttributesData empty() {
        return new PlayerAttributesData(new HashMap<>(), new HashMap<>());
    }

    /** 取某键的层贡献（层内只读快照，可能为空 map）。 */
    public Map<String, Float> layerContributions(String attrId, boolean temporary) {
        return (temporary ? temp : permanent).getOrDefault(attrId, Map.of());
    }

    /** 某键跨层贡献合计（未封顶、不含基准）。 */
    public float totalContribution(String attrId) {
        return sumOf(permanent.get(attrId)) + sumOf(temp.get(attrId));
    }

    private static float sumOf(Map<String, Float> bySource) {
        if (bySource == null) {
            return 0F;
        }
        float sum = 0F;
        for (float v : bySource.values()) {
            sum += v;
        }
        return sum;
    }
}
