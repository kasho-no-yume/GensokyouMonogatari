package com.bitsson.gensokyou.spirit.grace;

import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 八百万神恩进阶/洗练的服务端应用逻辑（superhuman-temper）。
 *
 * <p>写入规约（player-attribute-suite"阶级 roll 贡献源"）：池两键（最大灵力/灵力强度）
 * 经 {@link SpiritPowerData#withLedgerTier} 写池字段+阶级台账；其余键写属性容器
 * permanent 层，sourceId={@code grace_tier_N}，洗练整组替换、组间隔离。
 */
@EventBusSubscriber(modid = com.bitsson.gensokyou.Gensokyou.MODID)
public final class GraceService {

    /** 阶级贡献组 sourceId 前缀（命名空间隔离：与 command 调试写入互不覆盖）。 */
    public static final String SOURCE_PREFIX = "grace_tier_";

    private GraceService() {
    }

    public static String sourceId(int tier) {
        return SOURCE_PREFIX + tier;
    }

    public static int tierOf(ServerPlayer player) {
        return ModAttachments.get(player).temperLevel();
    }

    /** 凡人（0 阶）：一切玩家灵力消耗系统的统一拦截判据。 */
    public static boolean isMortal(ServerPlayer player) {
        return tierOf(player) <= 0;
    }

    /** 消耗类系统统一门槛：凡人拦截 + actionbar 提示。返回 false = 已拦截。 */
    public static boolean requireGrace(ServerPlayer player) {
        if (!isMortal(player)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("msg.gensokyou.grace_required"), true);
        return false;
    }

    /** 该阶级池份额是否已由进阶写入（洗练配方前置）。 */
    public static boolean hasTier(ServerPlayer player, int tier) {
        return ModAttachments.get(player).hasLedgerTier(tier);
    }

    /** 进阶 N-1→N：roll→台账→阶级→贡献组→飞行权限，一次落地。 */
    public static SpiritPowerData advance(ServerPlayer player, int newTier, RandomSource random) {
        GraceNumbers.GraceRoll roll = GraceNumbers.rollTier(newTier, random);
        ModAttachments.set(player, ModAttachments.get(player)
                .withLedgerTier(newTier, roll.maxGain(), roll.powerGain())
                .withTemper(newTier));
        applyContributions(player, roll);
        GraceFlight.applyPermission(player);
        if (newTier >= 1) {
            com.bitsson.gensokyou.event.AchievementAwards.award(player, "divine_grace");
        }
        return ModAttachments.get(player);
    }

    /** 洗练采纳：整组替换该阶级（台账+贡献组；其余阶级零触碰）。 */
    public static void applyRefine(ServerPlayer player, GraceNumbers.GraceRoll roll) {
        ModAttachments.set(player, ModAttachments.get(player)
                .withLedgerTier(roll.tier(), roll.maxGain(), roll.powerGain()));
        applyContributions(player, roll);
        GraceFlight.applyPermission(player);
    }

    private static void applyContributions(ServerPlayer player, GraceNumbers.GraceRoll roll) {
        String source = sourceId(roll.tier());
        for (AttributeKey key : AttributeKey.values()) {
            if (key == AttributeKey.MAX_SPIRIT || key == AttributeKey.SPIRIT_POWER) {
                continue; // 单写规约：池字段为事实来源，容器不留副本
            }
            PlayerAttributes.setPermanent(player, key, source,
                    roll.contributions().getOrDefault(key, 0F));
        }
    }

    /** 某阶级当前已落库的 roll 值（预览"现状"列）；池两键读台账。 */
    public static GraceNumbers.GraceRoll currentRoll(ServerPlayer player, int tier) {
        SpiritPowerData data = ModAttachments.get(player);
        Map<AttributeKey, Float> contributions = new EnumMap<>(AttributeKey.class);
        PlayerAttributesDataView view = PlayerAttributesDataView.of(player);
        for (AttributeKey key : AttributeKey.values()) {
            if (key == AttributeKey.MAX_SPIRIT || key == AttributeKey.SPIRIT_POWER) {
                continue;
            }
            Float value = view.permanentContribution(key, sourceId(tier));
            if (value != null && value != 0F) {
                contributions.put(key, value);
            }
        }
        return new GraceNumbers.GraceRoll(tier, data.ledgerMaxGain(tier),
                data.ledgerPowerGain(tier), contributions);
    }

    /** 属性容器 grace 组只读视图（避免直接触碰容器内部可变 map）。 */
    private record PlayerAttributesDataView(Map<String, Map<String, Float>> permanent) {
        static PlayerAttributesDataView of(ServerPlayer player) {
            return new PlayerAttributesDataView(
                    new HashMap<>(PlayerAttributes.data(player).permanent()));
        }

        Float permanentContribution(AttributeKey key, String source) {
            Map<String, Float> bySource = permanent.get(key.id());
            return bySource == null ? null : bySource.get(source);
        }
    }

    /**
     * 登录：先旧档迁移再授予飞行权限（顺序敏感，单点处理；
     * 重生/换维度重申在 {@link GraceFlight}，迁移幂等无需再跑）。
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            migrateIfNeeded(player);
            GraceFlight.applyPermission(player);
        }
    }

    /**
     * 旧档一次性迁移（PlayerEvent.Load）：
     * 凡人（tier 0）池清零；缺台账（旧线性淬炼调试档）或**旧表量级**（balance v1 前的百分比/
     * 小池表）按新表逐阶重 roll 覆盖——整组替换顺带清掉已退役的 danmaku_resist 贡献。
     */
    public static void migrateIfNeeded(ServerPlayer player) {
        SpiritPowerData data = ModAttachments.get(player);
        if (data.temperLevel() <= 0) {
            if (data.max() > 0F || data.spiritDamage() > 0F || !data.graceLedger().isEmpty()) {
                ModAttachments.set(player, new SpiritPowerData(0F, 0F, 0, 0F, 0F,
                        List.of(), 0F, data.flightInertia()));
            }
            return;
        }
        boolean emptyLedger = data.graceLedger().isEmpty();
        // 旧表 1 阶 max_spirit ≈ [170,230]；新表 ≈ [800,1200]：量级差足够安全地区分版本。
        boolean legacyScale = data.hasLedgerTier(1) && data.ledgerMaxGain(1) < LEGACY_MAX_SPIRIT_T1_THRESHOLD;
        if (!emptyLedger && !legacyScale) {
            return; // 已是新表
        }
        SpiritPowerData next = data;
        for (int tier = 1; tier <= Math.min(SpiritPowerData.MAX_TIER, data.temperLevel()); tier++) {
            GraceNumbers.GraceRoll roll = GraceNumbers.rollTier(tier, player.getRandom());
            next = next.withLedgerTier(tier, roll.maxGain(), roll.powerGain());
            applyContributions(player, roll);
        }
        ModAttachments.set(player, next.withTemper(data.temperLevel()));
    }

    /** 旧表 1 阶 max_spirit 上界（230）与新表下界（800）之间的判别阈值。 */
    private static final float LEGACY_MAX_SPIRIT_T1_THRESHOLD = 500F;
}
