package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.GensokyoDebugMapClient;
import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import com.bitsson.gensokyou.worldgen.GensokyoLayoutDensity;
import com.bitsson.gensokyou.worldgen.GensokyoMapImage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import javax.annotation.Nullable;
import java.util.List;

/** Deterministic, chunk-free overview map for creative world-generation checks. */
public final class GensokyoDebugMapItem extends MapItem {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Gensokyou.id("gensokyo"));
    private static final String LAYOUT_VERSION_KEY = "gensokyou_layout_version";
    private static final int LAYOUT_VERSION = 2;

    public GensokyoDebugMapItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int itemSlot, boolean isSelected) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player) || !player.hasInfiniteMaterials()) {
            return;
        }
        boolean held = isSelected || player.getOffhandItem() == stack;
        if (held && (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getInt(LAYOUT_VERSION_KEY) != LAYOUT_VERSION || getSavedData(stack, level) == null)) {
            // Locked pixels are synchronized and saved exactly like a filled map. The layout's
            // world scale is handled separately from vanilla's terrain sampling scale.
            MapItemSavedData map = MapItemSavedData.createFresh(0, 0, (byte) 4, false, false, DIMENSION).locked();
            ServerLevel gensokyo = serverLevel.getServer().getLevel(DIMENSION);
            if (gensokyo == null) {
                return;
            }
            GensokyoLayout layout = GensokyoLayoutDensity.layoutOf(gensokyo.getChunkSource().randomState().sampler().continentalness());
            map.colors = GensokyoMapImage.buildBaseColors(layout);
            MapId id = level.getFreeMapId();
            level.setMapData(id, map);
            stack.set(DataComponents.MAP_ID, id);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(LAYOUT_VERSION_KEY, LAYOUT_VERSION));
        }
        super.inventoryTick(stack, level, entity, itemSlot, isSelected);
    }

    @Override
    @Nullable
    protected MapItemSavedData getCustomMapData(ItemStack stack, Level level) {
        MapItemSavedData data = super.getCustomMapData(stack, level);
        return level.isClientSide && data != null ? GensokyoDebugMapClient.forViewer(data) : data;
    }

    @Override
    @Nullable
    public Packet<?> getUpdatePacket(ItemStack stack, Level level, Player player) {
        return player.hasInfiniteMaterials() ? super.getUpdatePacket(stack, level, player) : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return player.hasInfiniteMaterials() ? InteractionResultHolder.pass(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // The overview uses its own world scale, so vanilla banner positions would be incorrect.
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (GensokyoLayout.Region region : GensokyoLayout.Region.values()) {
            tooltip.add(Component.literal("■ ").withColor(GensokyoMapImage.legendRgb(region))
                    .append(Component.translatable("biome.gensokyou." + region.biomePath()).withColor(0xAAAAAA)));
        }
        tooltip.add(Component.literal("■ ").withColor(GensokyoMapImage.seaLegendRgb())
                .append(Component.translatable("biome.gensokyou.sanzu_river").withColor(0xAAAAAA)));
    }
}
