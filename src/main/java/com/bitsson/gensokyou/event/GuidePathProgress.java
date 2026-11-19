package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualCoreRegistry;
import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 指导书「成神之路」12 步进度的服务端授予与旧档兜底。
 *
 * <p>每步对应一个隐形 advancement {@code gensokyou:guide/path/step_NN_done}，其 criterion 名
 * 与数据文件一致（原版触发器步用触发器自带的名字，代码授予步统一为 {@code done}）。
 * 授予必须在动作发生的那一刻完成，才能让"提前完成"的步骤在父链解锁时直接显示为已完成。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GuidePathProgress {

    /** 代码授予步骤使用的 criterion 名（与 step_04/07/10 的 advancement JSON 一致）。 */
    private static final String CRITERION_CODE = "done";
    private static final String CRITERION_INVENTORY = "has_item";
    private static final String CRITERION_DIMENSION = "entered";
    private static final int FIRST_STEP = 1;
    private static final int LAST_STEP = 12;

    private GuidePathProgress() {
    }

    private static String path(int step) {
        return "guide/path/step_" + String.format("%02d", step) + "_done";
    }

    private static String criterion(int step) {
        return switch (step) {
            case 4, 7, 10 -> CRITERION_CODE;
            case 12 -> CRITERION_DIMENSION;
            default -> CRITERION_INVENTORY;
        };
    }

    /** 授予某一步。advancement 缺失时安全跳过（与 {@link GuideTierProgress} 同模式）。 */
    public static void award(ServerPlayer player, int step) {
        MinecraftServer server = player.getServer();
        if (server == null || step < FIRST_STEP || step > LAST_STEP) {
            return;
        }
        AdvancementHolder holder = server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, path(step)));
        if (holder != null) {
            player.getAdvancements().award(holder, criterion(step));
        }
    }

    /** 仪式搭建成功时调用：仅迦具土炎祭计入第 4 步。 */
    public static void onRitualBuilt(ServerPlayer player, ResourceLocation patternId) {
        if (RitualBehaviors.KAGUTSUICHI.equals(patternId)) {
            award(player, 4);
        }
    }

    /** 神恩进阶成功时调用：1 阶记第 7 步，2 阶及以上同时记第 7、10 步。 */
    public static void onGraceAdvanced(ServerPlayer player, int newTier) {
        if (newTier >= 1) {
            award(player, 7);
        }
        if (newTier >= 2) {
            award(player, 10);
        }
    }

    /**
     * 旧档兜底：每 20 tick 按当前状态补授可由状态判定的步骤。
     * 搭建仪式、BOSS 击杀不可由状态可靠判定，不做补授。
     */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (has(player, ModItems.RITUAL_BUILDER.get())) {
            award(player, 1);
        }
        if (has(player, ModItems.RITUAL_CORE_ITEM.get())) {
            award(player, 2);
        }
        if (has(player, ModItems.SPIRIT_CORES.get(0).get())) {
            award(player, 3);
        }
        if (hasFormedKagutsuchiNear(player)) {
            award(player, 4);
        }
        if (hasAny(player, ModItems.CINNABAR.get(), ModItems.ROUGH_CINNABAR_ORE.get(),
                ModItems.REFINED_CINNABAR.get(), ModItems.SPIRIT_IRON_ORE.get(),
                ModItems.ROUGH_SPIRIT_IRON_ORE.get(), ModItems.SPIRIT_IRON.get(),
                ModItems.STAR_SILVER_ORE.get(), ModItems.ROUGH_STAR_SILVER_ORE.get(),
                ModItems.STAR_SILVER.get(), ModItems.ONI_STONE.get(), ModItems.SPIRIT_SOIL.get(),
                ModItems.PORCELAIN_CLAY.get(), ModItems.PORCELAIN.get(), ModItems.HIGAN_SOIL.get(),
                ModItems.MOON_SAND.get(), ModItems.SACRED_WOOD.get(), ModItems.MAGIC_WOOD.get(),
                ModItems.ETERNAL_WOOD.get(), ModItems.SANZU_FLASK.get(), ModItems.SPIRIT_FISH.get(),
                ModItems.MERMAID_SCALE.get(), ModItems.TIDE_CRYSTAL.get(), ModItems.DRAGON_SCALE.get(),
                ModItems.SPIRIT_HERB.get(), ModItems.GENTIAN.get(), ModItems.HIGANBANA.get(),
                ModItems.MAGIC_MUSHROOM.get())) {
            award(player, 5);
        }
        if (hasAny(player, ModItems.REFINED_CINNABAR.get(), ModItems.SPIRIT_IRON.get(),
                ModItems.STAR_SILVER.get())) {
            award(player, 6);
        }
        if (ModAttachments.get(player).temperLevel() >= 1) {
            award(player, 7);
        }
        if (ModAttachments.get(player).temperLevel() >= 2) {
            award(player, 10);
        }
        if (has(player, ModItems.DANMAKU_WEAPON.get())) {
            award(player, 8);
        }
        if (AchievementAwards.has(player, "big_fairy")) {
            award(player, 9);
        }
        if (GuideTierProgress.worldTier(player) >= 3) {
            award(player, 12);
        }
    }

    /** 附近（48 格内）存在已成型的迦具土炎祭——手工摆放与构建器搭建都算。 */
    private static boolean hasFormedKagutsuchiNear(ServerPlayer player) {
        return RitualCoreRegistry.formedOfPattern(player.serverLevel(), RitualBehaviors.KAGUTSUICHI)
                .stream()
                .anyMatch(core -> core.getBlockPos().closerThan(player.blockPosition(), 48.0D));
    }

    private static boolean has(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item) > 0;
    }

    private static boolean hasAny(ServerPlayer player, Item... items) {
        for (Item item : items) {
            if (has(player, item)) {
                return true;
            }
        }
        return false;
    }
}
