package com.bitsson.gensokyou.spirit;

/**
 * 玩家符卡即时状态（add-player-spellcards）：
 * 鲜花之铠挡弹计数与疫符窗口的纯逻辑边界。
 */
class SpellBuffDataTest {

    @org.junit.jupiter.api.Test
    void flowerArmorConsumesPetalsUntilEmpty() {
        SpellBuffData data = SpellBuffData.initial().withFlowerArmor(3, 1000L);
        org.junit.jupiter.api.Assertions.assertTrue(data.hasFlowerArmor(100L));
        data = data.consumePetal(100L);
        org.junit.jupiter.api.Assertions.assertEquals(2, data.flowerArmorPetals());
        data = data.consumePetal(100L).consumePetal(100L);
        org.junit.jupiter.api.Assertions.assertEquals(0, data.flowerArmorPetals());
        org.junit.jupiter.api.Assertions.assertFalse(data.hasFlowerArmor(100L), "花瓣耗尽即失效");
        // 再挡无效（不产生负花瓣）
        org.junit.jupiter.api.Assertions.assertEquals(0, data.consumePetal(100L).flowerArmorPetals());
    }

    @org.junit.jupiter.api.Test
    void flowerArmorExpiresWithTime() {
        SpellBuffData data = SpellBuffData.initial().withFlowerArmor(5, 200L);
        org.junit.jupiter.api.Assertions.assertTrue(data.hasFlowerArmor(199L));
        org.junit.jupiter.api.Assertions.assertFalse(data.hasFlowerArmor(200L), "到期即失效");
    }

    @org.junit.jupiter.api.Test
    void plagueWindowGate() {
        SpellBuffData data = SpellBuffData.initial().withPlagueRepay(500L);
        org.junit.jupiter.api.Assertions.assertTrue(data.hasPlagueRepay(499L));
        org.junit.jupiter.api.Assertions.assertFalse(data.hasPlagueRepay(500L));
        // 护盾与疫符互不影响
        SpellBuffData both = data.withFlowerArmor(2, 800L).clearedFlowerArmor();
        org.junit.jupiter.api.Assertions.assertEquals(0, both.flowerArmorPetals());
        org.junit.jupiter.api.Assertions.assertTrue(both.hasPlagueRepay(100L), "清护盾不碰疫符");
    }
}
