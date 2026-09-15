package com.bitsson.gensokyou.spirit.attr;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 属性容器（spec player-attribute-suite"属性容器与持久化"）：跨层合计、sourceId 分组、
 * Codec 只持久化 permanent 层（temp 层加载后必为空=变身到期恢复的存储前提）。
 */
class PlayerAttributesDataTest {

    @Test
    void totalsAcrossLayersBySource() {
        Map<String, Map<String, Float>> perm = new HashMap<>();
        perm.put("graze_chance", new HashMap<>(Map.of("rune:a", 0.1F, "rune:b", 0.15F)));
        Map<String, Map<String, Float>> temp = new HashMap<>();
        temp.put("graze_chance", new HashMap<>(Map.of("transform", 0.2F)));
        PlayerAttributesData data = new PlayerAttributesData(perm, temp);

        assertEquals(0.45F, data.totalContribution("graze_chance"), 1e-6);
        assertEquals(2, data.layerContributions("graze_chance", false).size());
        assertEquals(1, data.layerContributions("graze_chance", true).size());
        assertTrue(data.layerContributions("unknown_attr", false).isEmpty());
    }

    @Test
    void codecPersistsOnlyPermanentLayer() {
        Map<String, Map<String, Float>> perm = new HashMap<>();
        perm.put("danmaku_resist", new HashMap<>(Map.of("temper", 10F)));
        Map<String, Map<String, Float>> temp = new HashMap<>();
        temp.put("spirit_power", new HashMap<>(Map.of("transform", 99F)));
        PlayerAttributesData data = new PlayerAttributesData(perm, temp);

        var json = PlayerAttributesData.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, data).result().orElseThrow();
        PlayerAttributesData loaded = PlayerAttributesData.CODEC.decode(
                com.mojang.serialization.JsonOps.INSTANCE, json).result().orElseThrow().getFirst();

        assertNotNull(loaded);
        assertEquals(10F, loaded.totalContribution("danmaku_resist"), 1e-6);
        // temp 层不入档：重载后必为空（变身到期=恢复）
        assertEquals(0F, loaded.totalContribution("spirit_power"), 1e-6);
        assertTrue(loaded.temp().isEmpty());
    }

    @Test
    void emptyContainerDecodesLegacySave() {
        // 旧档无该附件 → 空容器；即便有 {} 也全取基准
        var result = PlayerAttributesData.CODEC.decode(
                com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.JsonObject());
        assertTrue(result.result().isPresent());
        assertEquals(0F, result.result().orElseThrow().getFirst().totalContribution("graze_chance"), 1e-6);
    }
}
