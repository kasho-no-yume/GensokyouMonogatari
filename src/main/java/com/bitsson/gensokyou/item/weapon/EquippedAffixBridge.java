package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主手武器增幅核 → 玩家属性的装备来源注入。
 *
 * <p>设计立场「武器的加成也是给玩家的」：核的玩家属性词条与玩家其它来源的属性进<b>同一个加区</b>、
 * 同一个面板、同一套硬上限，经 {@code PlayerAttributes} 的临时层以 {@code seii_rune_*} 来源写入 ——
 * 与降神变身共用该层（两者生命周期一致：不入存档、由外部瞬态派生、整层丢弃即恢复），
 * 但白名单只约束变身来源。
 *
 * <p>红线：MUST NOT 写入 {@code ModAttachments} 的池台账（max_spirit / spirit_power），
 * 否则会出现"戴上武器变强、摘下武器掉属性"的假永久属性。
 */
public final class EquippedAffixBridge {

    /** 上一 tick 生效的来源集合，用于只在主手变化时重算（避免每 tick 全量写）。 */
    private static final Map<java.util.UUID, String> LAST_SIGNATURE = new java.util.HashMap<>();

    private EquippedAffixBridge() {
    }

    /**
     * 按主手武器当前增幅核重算装备来源贡献。
     * MUST 每 tick 调用（幂等）：签名未变则直接返回，不重复写。
     */
    public static void refresh(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        RuneSummary summary = RuneSummary.of(
                weapon.getOrDefault(com.bitsson.gensokyou.registry.ModDataComponents.WEAPON_SLOTS.get(),
                                com.bitsson.gensokyou.item.weapon.WeaponSlots.DEFAULT)
                        .slot3()
                        .getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.of()));
        String signature = signatureOf(summary);
        String previous = LAST_SIGNATURE.put(player.getUUID(), signature);
        if (signature.equals(previous)) {
            return;
        }
        PlayerAttributes.clearSources(player, PlayerAttributes.EQUIPPED_SOURCE_PREFIX);
        for (Map.Entry<AttributeKey, Float> e : attrContributions(summary).entrySet()) {
            if (e.getValue() != 0F) {
                PlayerAttributes.setEquipped(player, e.getKey(), e.getKey().id(), e.getValue());
            }
        }
    }

    /** 玩家下线 / 换维度时清掉签名缓存（贡献本身随 temp 层整层丢弃）。 */
    public static void forget(java.util.UUID uuid) {
        LAST_SIGNATURE.remove(uuid);
    }

    /** 玩家属性域的 Σ 值（未知 id 与武器专有 id 忽略）。 */
    public static Map<AttributeKey, Float> attrContributions(RuneSummary summary) {
        Map<AttributeKey, Float> out = new HashMap<>();
        for (Map.Entry<String, Float> e : summary.raw().entrySet()) {
            AttributeKey key = AttributeKey.byId(e.getKey());
            if (key != null) {
                out.merge(key, e.getValue(), Float::sum);
            }
        }
        return out;
    }

    private static String signatureOf(RuneSummary summary) {
        StringBuilder sb = new StringBuilder();
        summary.raw().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append('=')
                        .append(Math.round(e.getValue() * 1000F)).append(';'));
        return sb.toString();
    }
}
