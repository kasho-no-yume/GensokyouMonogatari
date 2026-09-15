package com.bitsson.gensokyou.spirit.attr;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家属性套件统一服务（结算唯一入口）。全部服务端权威。
 *
 * <p>灵力池三键（最大灵力/恢复速率/灵力强度）经注册表 baseFn 合并读取 SpiritPowerData
 * 既有字段——存储事实来源仍是池数据，本容器不双写。
 */
public final class PlayerAttributes {

    /** 一次属性结算的分解（调试命令/测试用）。 */
    public record Breakdown(AttributeKey key, double base, float permanent, float temp,
                            double cap, float effective, boolean capped) {
    }

    private PlayerAttributes() {
    }

    public static PlayerAttributesData data(ServerPlayer player) {
        return player.getData(com.bitsson.gensokyou.spirit.ModAttachments.PLAYER_ATTRIBUTES.get());
    }

    public static float finalValue(ServerPlayer player, AttributeKey key) {
        return finalValue(player, key, data(player));
    }

    private static float finalValue(ServerPlayer player, AttributeKey key, PlayerAttributesData data) {
        return AttributeMath.finalFrom(key.baseValue(player), data.totalContribution(key.id()), key.cap());
    }

    public static Breakdown breakdown(ServerPlayer player, AttributeKey key) {
        PlayerAttributesData data = data(player);
        double base = key.baseValue(player);
        float perm = sum(data.permanent().get(key.id()));
        float tmp = sum(data.temp().get(key.id()));
        double cap = key.cap();
        float effective = finalValue(player, key, data);
        boolean capped = cap >= 0D && base + perm + tmp > cap;
        return new Breakdown(key, base, perm, tmp, cap, effective, capped);
    }

    private static float sum(Map<String, Float> bySource) {
        if (bySource == null) {
            return 0F;
        }
        float s = 0F;
        for (float v : bySource.values()) {
            s += v;
        }
        return s;
    }

    /** 写入持久层贡献（sourceId 分组，0 值移除该来源）；未知键拒绝。返回是否生效。 */
    public static boolean setPermanent(ServerPlayer player, AttributeKey key, String sourceId, float value) {
        if (key == null) {
            return false;
        }
        PlayerAttributesData data = data(player);
        Map<String, Map<String, Float>> permanent = copyLayers(data.permanent());
        setIn(permanent, key.id(), sourceId, value);
        store(player, new PlayerAttributesData(permanent, data.temp()));
        return true;
    }

    /**
     * 写入临时改写层（变身用）；白名单外键与未知键拒绝（越界改写被拒）。
     * 返回是否生效。
     */
    public static boolean setTemp(ServerPlayer player, AttributeKey key, String sourceId, float value) {
        if (key == null || !key.isTransformRewritable()) {
            return false;
        }
        PlayerAttributesData data = data(player);
        Map<String, Map<String, Float>> temp = copyLayers(data.temp());
        setIn(temp, key.id(), sourceId, value);
        store(player, new PlayerAttributesData(data.permanent(), temp));
        return true;
    }

    /** 整层丢弃临时改写（变身到期=恢复原值）。 */
    public static void clearTemp(ServerPlayer player) {
        PlayerAttributesData data = data(player);
        if (data.temp().isEmpty()) {
            return;
        }
        store(player, new PlayerAttributesData(data.permanent(), new HashMap<>()));
    }

    private static void setIn(Map<String, Map<String, Float>> layers, String attrId,
                              String sourceId, float value) {
        if (value == 0F) {
            Map<String, Float> bySource = layers.get(attrId);
            if (bySource != null) {
                bySource.remove(sourceId);
                if (bySource.isEmpty()) {
                    layers.remove(attrId);
                }
            }
            return;
        }
        layers.computeIfAbsent(attrId, k -> new HashMap<>()).put(sourceId, value);
    }

    private static Map<String, Map<String, Float>> copyLayers(Map<String, Map<String, Float>> layers) {
        Map<String, Map<String, Float>> copy = new LinkedHashMap<>();
        layers.forEach((attrId, bySource) -> copy.put(attrId, new HashMap<>(bySource)));
        return copy;
    }

    private static void store(ServerPlayer player, PlayerAttributesData data) {
        player.setData(com.bitsson.gensokyou.spirit.ModAttachments.PLAYER_ATTRIBUTES.get(), data);
        refreshBridged(player);
    }

    // ---- 便捷消费入口 ----

    /** 灵力强度：伤害公式唯一读取入口（player-spirit-attributes 收编后语义）。 */
    public static float spiritPower(ServerPlayer player) {
        return finalValue(player, AttributeKey.SPIRIT_POWER);
    }

    /** 每秒灵力恢复速率（含贡献）。 */
    public static double regenPerSecond(ServerPlayer player) {
        return finalValue(player, AttributeKey.SPIRIT_REGEN_RATE);
    }

    /** 有效灵力上限（池自身 max + 贡献；池写路径的旧钳制仍以池 max 为准，贡献只放宽读取侧）。 */
    public static float effectiveMaxSpirit(ServerPlayer player) {
        return finalValue(player, AttributeKey.MAX_SPIRIT);
    }

    /** 施放瞬间读取折减后的符卡冷却（中途属性变化不回溯由调用方一次性取值保证）。 */
    public static long effectiveSkillCooldown(ServerPlayer player, long baseTicks) {
        return AttributeMath.effectiveCooldownTicks(baseTicks,
                AttributeMath.clampPercent(finalValue(player, AttributeKey.SPELL_CDR)));
    }

    /** 暴击发射时 roll：命中返回 1+暴击伤害加成（负加成钳到 0 倍率），未命中返回 1。 */
    public static float rollCrit(ServerPlayer player, net.minecraft.util.RandomSource random) {
        float chance = AttributeMath.clampPercent(finalValue(player, AttributeKey.CRIT_CHANCE));
        if (random.nextFloat() >= chance) {
            return 1F;
        }
        return Math.max(0F, 1F + finalValue(player, AttributeKey.CRIT_DAMAGE));
    }

    // ---- D3 原版属性桥（幂等重算，非累加） ----

    public static void refreshBridged(ServerPlayer player) {
        float healthBonus = finalValue(player, AttributeKey.HEALTH_BONUS);
        var health = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) {
            var id = com.bitsson.gensokyou.spirit.attr.AttributeBridgeIds.HEALTH_BONUS;
            if (healthBonus <= 0F) {
                health.removeModifier(id);
            } else {
                health.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        id, healthBonus, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }
        float speedBonus = finalValue(player, AttributeKey.MOVE_SPEED_BONUS);
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            var id = com.bitsson.gensokyou.spirit.attr.AttributeBridgeIds.MOVE_SPEED;
            if (speedBonus <= 0F) {
                speed.removeModifier(id);
            } else {
                speed.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        id, speedBonus,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }
}
