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
     * 写入临时改写层；白名单外键与未知键拒绝（越界改写被拒）。
     *
     * <p><b>白名单约束的是"来源"而不是"层"</b>：{@link AttributeKey#isTransformRewritable()} 描述的是
     * <b>降神变身</b>的改写域，故只有变身来源命名空间受它约束；装备来源（{@code seii_rune_*} 等前缀）
     * 写入同一层时不受限制 —— "变身可改写"与"装备提供"是两个独立语义，把白名单当作层级别的写权限会混淆二者。
     *
     * @return 是否生效
     */
    public static boolean setTemp(ServerPlayer player, AttributeKey key, String sourceId, float value) {
        if (key == null || (isTransformSource(sourceId) && !key.isTransformRewritable())) {
            return false;
        }
        PlayerAttributesData data = data(player);
        Map<String, Map<String, Float>> temp = copyLayers(data.temp());
        setIn(temp, key.id(), sourceId, value);
        store(player, new PlayerAttributesData(data.permanent(), temp));
        return true;
    }

    /** 装备来源前缀（主手武器增幅核的玩家属性词条）。 */
    public static final String EQUIPPED_SOURCE_PREFIX = "seii_rune_";

    /** 来源是否属于降神变身命名空间（即 MUST 受 transformRewritable 白名单约束）。 */
    public static boolean isTransformSource(String sourceId) {
        return sourceId == null || !sourceId.startsWith(EQUIPPED_SOURCE_PREFIX);
    }

    /** 写入装备来源的加区贡献（不受变身白名单约束）。 */
    public static boolean setEquipped(ServerPlayer player, AttributeKey key, String sourceId, float value) {
        return setTemp(player, key, EQUIPPED_SOURCE_PREFIX + sourceId, value);
    }

    /** 丢弃某层内指定前缀的全部来源（换装 / 卸下主手时清装备贡献，不动变身来源）。 */
    public static void clearSources(ServerPlayer player, String prefix) {
        PlayerAttributesData data = data(player);
        Map<String, Map<String, Float>> temp = copyLayers(data.temp());
        boolean changed = false;
        var it = temp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Map<String, Float>> e = it.next();
            e.getValue().keySet().removeIf(id -> id.startsWith(prefix));
            if (e.getValue().isEmpty()) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            store(player, new PlayerAttributesData(data.permanent(), temp));
        }
    }

    /** 整层丢弃临时改写（变身到期=恢复原值；换装=装备贡献清空）。 */
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
        return rollCrit(player, random, 0F, 0F);
    }

    /**
     * 暴击发射时 roll（带增幅核词条加成）：`extraChance` 加暴击率、`extraDamage` 加暴伤加成，
     * 折入玩家属性后统一 roll。返回 1（未暴击）或 1+暴伤加成（暴击）。
     */
    public static float rollCrit(ServerPlayer player, net.minecraft.util.RandomSource random,
                                 float extraChance, float extraDamage) {
        float chance = AttributeMath.clampPercent(
                finalValue(player, AttributeKey.CRIT_CHANCE) + Math.max(0F, extraChance));
        double chanceCap = AttributeKey.CRIT_CHANCE.cap();
        if (chanceCap >= 0D) {
            chance = (float) Math.min(chance, chanceCap);
        }
        if (random.nextFloat() >= chance) {
            return 1F;
        }
        return Math.max(0F, 1F + finalValue(player, AttributeKey.CRIT_DAMAGE) + Math.max(0F, extraDamage));
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
        float jumpBlocks = finalValue(player, AttributeKey.JUMP);
        var jump = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        if (jump != null) {
            var id = com.bitsson.gensokyou.spirit.attr.AttributeBridgeIds.JUMP;
            if (jumpBlocks <= 0F) {
                jump.removeModifier(id);
            } else {
                jump.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        id, AttributeMath.jumpStrengthDelta(jumpBlocks),
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }
}
