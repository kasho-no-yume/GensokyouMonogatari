package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.SkillSyncPayload;
import com.bitsson.gensokyou.ritual.RitualPreviewState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Gensokyou.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SkillStateData>> SKILL_STATE =
            ATTACHMENTS.register("skill_state", () -> AttachmentType
                    .<SkillStateData>builder(SkillStateData::initial)
                    .serialize(SkillStateData.CODEC)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritPowerData>> SPIRIT_POWER =
            ATTACHMENTS.register("spirit_power", () -> AttachmentType
                    .<SpiritPowerData>builder(SpiritPowerData::initial)
                    .serialize(SpiritPowerData.CODEC)
                    .copyOnDeath()
                    .build());

    /** 玩家属性套件容器（player-attribute-suite）：持久层入档、死亡保留；旧档缺=空容器取基准。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.bitsson.gensokyou.spirit.attr.PlayerAttributesData>> PLAYER_ATTRIBUTES =
            ATTACHMENTS.register("player_attributes", () -> AttachmentType
                    .<com.bitsson.gensokyou.spirit.attr.PlayerAttributesData>builder(com.bitsson.gensokyou.spirit.attr.PlayerAttributesData::empty)
                    .serialize(com.bitsson.gensokyou.spirit.attr.PlayerAttributesData.CODEC)
                    .copyOnDeath()
                    .build());

    /** 灵力汲取限速账本：会话级 transient（无 serialize/copyOnDeath）。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.bitsson.gensokyou.spirit.attr.LedgerData>> SPIRIT_LEECH_LEDGER =
            ATTACHMENTS.register("spirit_leech_ledger", () -> AttachmentType
                    .<com.bitsson.gensokyou.spirit.attr.LedgerData>builder(com.bitsson.gensokyou.spirit.attr.LedgerData::initial)
                    .build());

    /** 测试无限灵力标记（add-balance-test-harness）：会话级 transient。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> TEST_INFINITE_SPIRIT =
            ATTACHMENTS.register("test_infinite_spirit", () -> AttachmentType
                    .<Boolean>builder(() -> Boolean.FALSE)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NpcOffenseData>> NPC_OFFENSE =
            ATTACHMENTS.register("npc_offense", () -> AttachmentType
                    .<NpcOffenseData>builder(NpcOffenseData::initial)
                    .serialize(NpcOffenseData.CODEC)
                    .build());

    /** 构建器预览态：会话级 transient（无 serialize/copyOnDeath），默认 null = 无预览。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RitualPreviewState>> RITUAL_PREVIEW =
            ATTACHMENTS.register("ritual_preview", () -> AttachmentType
                    .<RitualPreviewState>builder(() -> null)
                    .build());

    /** 编辑杖力建预览态：会话级 transient，默认 null = 无预览。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.bitsson.gensokyou.ritual.editor.EditorPreviewState>> RITUAL_EDITOR_PREVIEW =
            ATTACHMENTS.register("ritual_editor_preview", () -> AttachmentType
                    .<com.bitsson.gensokyou.ritual.editor.EditorPreviewState>builder(() -> null)
                    .build());

    /** 《幻想乡物语》序言已读标记：玩家持久化、死亡保留。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GUIDEBOOK_READ =
            ATTACHMENTS.register("guidebook_read", () -> AttachmentType
                    .<Boolean>builder(() -> false)
                    .serialize(com.mojang.serialization.Codec.BOOL)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }

    public static SpiritPowerData get(ServerPlayer player) {
        return player.getData(SPIRIT_POWER.get());
    }

    public static void set(ServerPlayer player, SpiritPowerData data) {
        player.setData(SPIRIT_POWER.get(), data);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        com.bitsson.gensokyou.spirit.grace.GraceFlight.syncGraceState(player);
    }

    public static SkillStateData skills(ServerPlayer player) {
        return player.getData(SKILL_STATE.get());
    }

    /** 《幻想乡物语》序言是否已读。 */
    public static boolean hasReadGuidebook(ServerPlayer player) {
        return player.getData(GUIDEBOOK_READ.get());
    }

    /** 标记《幻想乡物语》序言已读。 */
    public static void markGuidebookRead(ServerPlayer player) {
        player.setData(GUIDEBOOK_READ.get(), true);
    }

    /** playerSpiritDamage 统一读取入口（伤害公式唯一数据来源）。 */
    public static float spiritDamage(ServerPlayer player) {
        return get(player).spiritDamage();
    }

    /** 测试无限灵力（add-balance-test-harness）。 */
    public static boolean isTestInfiniteSpirit(ServerPlayer player) {
        return player.getData(TEST_INFINITE_SPIRIT.get());
    }

    public static void setTestInfiniteSpirit(ServerPlayer player, boolean value) {
        player.setData(TEST_INFINITE_SPIRIT.get(), value);
    }

    public static void setSkills(ServerPlayer player, SkillStateData data) {
        player.setData(SKILL_STATE.get(), data);
        syncSkills(player);
    }

    public static void syncSkills(ServerPlayer player) {
        SkillStateData state = skills(player);
        long now = player.level().getGameTime();
        boolean[] learned = new boolean[SkillStateData.MAX_SLOTS];
        int[] remaining = new int[SkillStateData.MAX_SLOTS];
        String[] equipped = new String[SkillStateData.MAX_SLOTS];
        for (int i = 0; i < SkillStateData.MAX_SLOTS; i++) {
            equipped[i] = state.equippedCard(i) == null ? SkillStateData.EMPTY : state.equippedCard(i);
            learned[i] = !equipped[i].isEmpty() && state.hasLearned(equipped[i]);
            remaining[i] = (int) Math.max(0, state.cooldownUntil(i) - now);
        }
        PacketDistributor.sendToPlayer(player, new SkillSyncPayload(learned, remaining, equipped));
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.bitsson.gensokyou.spirit.attr.PlayerAttributes.refreshBridged(player);
            sync(player);
            syncSkills(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 死亡规则：当前灵力清零由 Clone 处理；此处仅强制刷新客户端
            com.bitsson.gensokyou.spirit.attr.PlayerAttributes.refreshBridged(player);
            sync(player);
            syncSkills(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.bitsson.gensokyou.spirit.attr.PlayerAttributes.refreshBridged(player);
            sync(player);
            syncSkills(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        SpiritPowerData old = event.getOriginal().getData(SPIRIT_POWER.get());
        if (event.isWasDeath()) {
            // 死亡规则：仅当前灵力清零；上限/阶级/台账/惯性开关全部保留
            event.getEntity().setData(SPIRIT_POWER.get(),
                    new SpiritPowerData(0F, old.max(), old.temperLevel(), 0F, old.spiritDamage(),
                            old.graceLedger(), 0F, old.flightInertia()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !player.isDeadOrDying()
                && player.tickCount % 20 == 0) {
            tickRegen(player);
        }
    }

    private static void tickRegen(ServerPlayer player) {
        SpiritPowerData data = get(player);
        if (data.current() >= com.bitsson.gensokyou.spirit.attr.PlayerAttributes.effectiveMaxSpirit(player)) {
            return;
        }
        double ratePerSecond = com.bitsson.gensokyou.spirit.attr.PlayerAttributes.regenPerSecond(player);
        float buffer = data.regenBuffer() + (float) ratePerSecond;
        float whole = (float) Math.floor(buffer);
        if (whole > 0F) {
            set(player, data.withAddedCurrent(whole).withRegenBuffer(buffer - whole));
        } else {
            set(player, data.withRegenBuffer(buffer));
        }
    }
}
