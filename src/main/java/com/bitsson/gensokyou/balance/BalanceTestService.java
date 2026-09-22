package com.bitsson.gensokyou.balance;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.BalanceTestBossEntity;
import com.bitsson.gensokyou.item.weapon.RuneGenerator;
import com.bitsson.gensokyou.item.weapon.WeaponSlots;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import com.bitsson.gensokyou.spirit.grace.GraceFlight;
import com.bitsson.gensokyou.spirit.grace.GraceNumbers;
import com.bitsson.gensokyou.spirit.grace.GraceService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * 数值测试台服务（add-balance-test-harness）：标准数值配置、无限灵力、测试 BOSS 生成/清理。
 *
 * <p>"标准数值" = grace 表 base 中点（不做 roll）；非池键写独立 sourceId {@code test_standard}
 * （与 {@code grace_tier_N}/command 隔离）。池两键受单写规约约束，只能经阶级台账写入——
 * 故本命令会覆盖 1..N 阶台账份额（以 {@code test_standard} 之外的方式），{@code reset} 时清空。
 */
@EventBusSubscriber(modid = com.bitsson.gensokyou.Gensokyou.MODID)
public final class BalanceTestService {

    /** 标准数值的非池键来源（与 grace_tier_N 隔离）。 */
    public static final String TEST_SOURCE = "test_standard";

    private BalanceTestService() {
    }

    // ---------------------------------------------------------------
    // 玩家标准数值
    // ---------------------------------------------------------------

    public static void configurePlayer(ServerPlayer player, int tier, boolean infinite) {
        clearContributions(player);

        SpiritPowerData data = ModAttachments.get(player);
        SpiritPowerData next = new SpiritPowerData(0F, 0F, 0, 0F, 0F, List.of(), 0F, data.flightInertia());
        java.util.Map<AttributeKey, Double> totals = new java.util.EnumMap<>(AttributeKey.class);
        for (int t = 1; t <= tier; t++) {
            float maxGain = 0F;
            float powerGain = 0F;
            for (AttributeKey key : AttributeKey.values()) {
                GraceNumbers.Entry entry = GraceNumbers.entry(t, key);
                if (entry == null) {
                    continue;
                }
                float value = (float) entry.base();
                switch (key) {
                    case MAX_SPIRIT -> maxGain = value;
                    case SPIRIT_POWER -> powerGain = value;
                    default -> totals.merge(key, (double) value, Double::sum);
                }
            }
            next = next.withLedgerTier(t, maxGain, powerGain);
        }
        for (java.util.Map.Entry<AttributeKey, Double> entry : totals.entrySet()) {
            PlayerAttributes.setPermanent(player, entry.getKey(), TEST_SOURCE, entry.getValue().floatValue());
        }
        next = next.withTemper(tier);
        ModAttachments.set(player, next.withCurrent(next.max()));
        PlayerAttributes.refreshBridged(player);
        GraceFlight.applyPermission(player);

        giveStandardGear(player, tier);
        ModAttachments.setTestInfiniteSpirit(player, infinite);

        player.displayClientMessage(Component.translatable("msg.gensokyou.test_player_configured",
                tier, infinite ? "infinite" : "normal"), false);
    }

    public static void resetPlayer(ServerPlayer player) {
        clearContributions(player);
        SpiritPowerData data = ModAttachments.get(player);
        ModAttachments.set(player, new SpiritPowerData(0F, 0F, 0, 0F, 0F, List.of(), 0F, data.flightInertia()));
        PlayerAttributes.refreshBridged(player);
        GraceFlight.revoke(player);
        ModAttachments.setTestInfiniteSpirit(player, false);
        player.displayClientMessage(Component.translatable("msg.gensokyou.test_player_reset"), false);
    }

    /** 清空 grace_tier_1..5 与 test_standard 的全部贡献（池台账随 configure 覆盖 / reset 清空）。 */
    private static void clearContributions(ServerPlayer player) {
        for (AttributeKey key : AttributeKey.values()) {
            PlayerAttributes.setPermanent(player, key, TEST_SOURCE, 0F);
            for (int t = 1; t <= SpiritPowerData.MAX_TIER; t++) {
                PlayerAttributes.setPermanent(player, key, GraceService.sourceId(t), 0F);
            }
        }
    }

    private static void giveStandardGear(ServerPlayer player, int tier) {
        int band = Math.min(3, (Math.max(1, tier) + 1) / 2);
        ItemStack weapon = new ItemStack(ModItems.DANMAKU_WEAPON.get());
        Item levelCore = switch (band) {
            case 1 -> ModItems.WEAPON_CORE_LV1.get();
            case 2 -> ModItems.WEAPON_CORE_LV2.get();
            default -> ModItems.WEAPON_CORE_LV3.get();
        };
        Item ampCore = switch (band) {
            case 1 -> ModItems.AMP_CORE_T1.get();
            case 2 -> ModItems.AMP_CORE_T2.get();
            default -> ModItems.AMP_CORE_T3.get();
        };
        ItemStack amp = new ItemStack(ampCore);
        RuneGenerator.ensureGenerated(amp, band);
        weapon.set(ModDataComponents.WEAPON_SLOTS.get(),
                new WeaponSlots(new ItemStack(ModItems.CORE_SPHERE_SINGLE.get()), new ItemStack(levelCore), amp));
        if (!player.getInventory().add(weapon)) {
            player.drop(weapon, false);
        }
    }

    // ---------------------------------------------------------------
    // 测试 BOSS
    // ---------------------------------------------------------------

    public static void spawnBoss(ServerPlayer player, int tier, boolean maxVariant) {
        BalanceTestBossEntity boss = ModEntityTypes.BALANCE_TEST_BOSS.get().create(player.serverLevel());
        if (boss == null) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.test_boss_failed"), false);
            return;
        }
        double dps = MonsterStatBudget.referencePlayerDps(tier);
        double ehp = MonsterStatBudget.referencePlayerEhp(tier);
        double seconds = maxVariant
                ? GensokyouConfig.TEST_BOSS_MAX_SECONDS.get()
                : GensokyouConfig.TEST_BOSS_MIN_SECONDS.get();
        int hits = maxVariant
                ? GensokyouConfig.TEST_BOSS_MAX_HITS.get()
                : GensokyouConfig.TEST_BOSS_MIN_HITS.get();
        double hp = TestBossTuning.hp(dps, seconds);
        double damage = TestBossTuning.damage(ehp, hits);
        boss.configure(tier, maxVariant, (float) hp, (float) damage);
        boss.moveTo(player.getX(), player.getY() + 3.0D, player.getZ(), player.getYRot(), 0F);
        player.serverLevel().addFreshEntity(boss);
        boss.setTarget(player);
        player.displayClientMessage(Component.translatable("msg.gensokyou.test_boss_spawned",
                tier, maxVariant ? "max" : "min", (long) hp, (long) damage), false);
    }

    public static void clearBosses(ServerPlayer player) {
        List<BalanceTestBossEntity> bosses = player.serverLevel()
                .getEntitiesOfClass(BalanceTestBossEntity.class, player.getBoundingBox().inflate(160.0D));
        for (BalanceTestBossEntity boss : bosses) {
            boss.discard();
        }
        player.displayClientMessage(
                Component.translatable("msg.gensokyou.test_boss_cleared", bosses.size()), false);
    }

    // ---------------------------------------------------------------
    // 测试无限灵力
    // ---------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ModAttachments.isTestInfiniteSpirit(player)) {
            return;
        }
        SpiritPowerData data = ModAttachments.get(player);
        float max = PlayerAttributes.effectiveMaxSpirit(player);
        if (data.current() < max) {
            ModAttachments.set(player, data.withCurrent(max));
        }
    }
}
