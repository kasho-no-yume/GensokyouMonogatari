package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.item.BuilderBind;
import com.bitsson.gensokyou.item.BuilderSelection;
import com.bitsson.gensokyou.item.RitualBuilderItem;
import com.bitsson.gensokyou.network.BoundSupplyCountsPayload;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.ritual.behavior.WujinzangStorage;
import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 绑定无尽藏仓储计数推送：周期性把"玩家背包内构建器所需方块在绑定仓储中的数量子集"
 * 下发给客户端（tooltip / 菜单 / HUD 共用），变化才发、不可用发状态码。
 *
 * <p>只同步相关子集（当前选择与预览图案所需方块），不整发全仓储；间隔由 config 控制。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class BoundSupplySyncServer {

    private static int ticks;
    /** 每玩家上次推送的信号（核心坐标 → 签名），用于变化去重。 */
    private static final Map<UUID, Map<BlockPos, String>> LAST = new HashMap<>();

    private BoundSupplySyncServer() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int interval = GensokyouConfig.BUILDER_BIND_SYNC_INTERVAL_TICKS.get();
        if (interval <= 0 || ++ticks % interval != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                push(level, player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST.remove(player.getUUID());
        }
    }

    private static void push(ServerLevel level, ServerPlayer player) {
        Map<BlockPos, BuilderBind> binds = new LinkedHashMap<>();
        Map<BlockPos, Set<Item>> relevant = new LinkedHashMap<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof RitualBuilderItem)) {
                continue;
            }
            BuilderBind bind = RitualBuilderItem.bind(stack);
            if (bind == null) {
                continue;
            }
            BlockPos pos = bind.pos().immutable();
            binds.putIfAbsent(pos, bind);
            Set<Item> items = relevant.computeIfAbsent(pos, k -> new LinkedHashSet<>());
            BuilderSelection sel = stack.get(ModDataComponents.RITUAL_BUILDER_SELECTION.get());
            if (sel != null) {
                addRequirementItems(items, sel);
            }
        }
        if (binds.isEmpty()) {
            LAST.remove(player.getUUID());
            return;
        }
        // 预览图案（若存在）并入所有绑定集合，保证 HUD 缺口口径有料
        RitualPreviewState preview = player.getData(ModAttachments.RITUAL_PREVIEW.get());
        if (preview != null) {
            BuilderSelection psel = new BuilderSelection(preview.patternId(), preview.tier());
            for (Set<Item> items : relevant.values()) {
                addRequirementItems(items, psel);
            }
        }

        Map<BlockPos, String> sigs = new HashMap<>();
        Map<BlockPos, BoundSupplyCountsPayload> payloads = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BuilderBind> entry : binds.entrySet()) {
            BlockPos pos = entry.getKey();
            BuilderBind bind = entry.getValue();
            int status = BoundSupply.status(level, bind);
            List<BoundSupplyCountsPayload.Entry> entries = new ArrayList<>();
            if (status == BoundSupply.STATUS_AVAILABLE) {
                RitualMatch match = BoundSupply.wujinzangMatch(level, bind);
                if (match != null) {
                    Set<Item> want = relevant.getOrDefault(pos, Set.of());
                    for (WujinzangStorage.Agg agg : WujinzangStorage.aggregate(level, match)) {
                        if (want.contains(agg.key().getItem())) {
                            entries.add(new BoundSupplyCountsPayload.Entry(
                                    agg.key().copyWithCount(1), agg.count()));
                        }
                    }
                }
            }
            entries.sort(Comparator.comparing(
                    a -> BuiltInRegistries.ITEM.getKey(a.key().getItem()).toString()));
            String sig = status + "|" + entries.stream()
                    .map(a -> BuiltInRegistries.ITEM.getKey(a.key().getItem()) + ":" + a.count())
                    .collect(Collectors.joining(","));
            sigs.put(pos, sig);
            payloads.put(pos, new BoundSupplyCountsPayload(pos, status, List.copyOf(entries)));
        }

        Map<BlockPos, String> prev = LAST.get(player.getUUID());
        if (prev != null && prev.equals(sigs)) {
            return;
        }
        for (BoundSupplyCountsPayload payload : payloads.values()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        LAST.put(player.getUUID(), sigs);
    }

    private static void addRequirementItems(Set<Item> items, BuilderSelection selection) {
        RitualPatternLoader.byId(selection.patternId()).ifPresent(pattern -> {
            for (RitualBuilderPlacement.Requirement req
                    : RitualBuilderPlacement.requirements(pattern, selection.tier())) {
                if (!req.itemLess()) {
                    items.add(req.block().asItem());
                }
            }
        });
    }
}
