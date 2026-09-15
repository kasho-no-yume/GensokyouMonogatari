package com.bitsson.gensokyou.spirit.attr;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 属性注册表完整性（spec"属性注册表"15 键 + "变身可改写白名单"）。
 * 只测纯元数据，不触碰 baseValue/cap（那会要求配置引导）。
 */
class AttributeKeyRegistryTest {

    @Test
    void hasExactlyFifteenKeysWithUniqueIds() {
        assertEquals(15, AttributeKey.values().length);
        Set<String> ids = new HashSet<>();
        for (AttributeKey key : AttributeKey.values()) {
            assertTrue(ids.add(key.id()), "duplicate id: " + key.id());
            assertSame(key, AttributeKey.byId(key.id()));
        }
    }

    @Test
    void unknownKeyIsNotRegistered() {
        assertNull(AttributeKey.byId("table_outside_attr"));
    }

    @Test
    void transformWhitelistMatchesDesignRuling() {
        // 已裁决改写域：生命/移速/灵力强度/灵力恢复 + 符卡增幅
        assertTrue(AttributeKey.HEALTH_BONUS.isTransformRewritable());
        assertTrue(AttributeKey.MOVE_SPEED_BONUS.isTransformRewritable());
        assertTrue(AttributeKey.SPIRIT_POWER.isTransformRewritable());
        assertTrue(AttributeKey.SPIRIT_REGEN_RATE.isTransformRewritable());
        assertTrue(AttributeKey.SPELL_AMP.isTransformRewritable());
        // 白名单外（机制/成长键）不可被变身改写
        assertFalse(AttributeKey.MAX_SPIRIT.isTransformRewritable());
        assertFalse(AttributeKey.GRAZE_CHANCE.isTransformRewritable());
        assertFalse(AttributeKey.SPIRIT_LEECH_RATE.isTransformRewritable());
        assertFalse(AttributeKey.SPELL_CDR.isTransformRewritable());
    }
}
