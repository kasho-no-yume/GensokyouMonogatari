package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualOfferings;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** S2C：仪式界面全量信息（开界面与状态变更后推送）。 */
public record RitualInfoPayload(long pos, String patternId, int tier, boolean enabled,
                                boolean toggleable, int stored, int capacity,
                                List<Entry> entries, List<Action> actions,
                                List<RecipeInfo> recipes, String activeRecipeId,
                                String statusKey) implements CustomPacketPayload {

    public record Entry(String itemId, int count, boolean satisfied) {
    }

    /** 行为注入的自定义操作按钮（labelKey 客户端本地化）。 */
    public record Action(int id, String labelKey) {
    }

    /** 可用配方条目（✗ 时 missingText 为服务端预解析的缺项摘要）。 */
    public record RecipeInfo(String recipeId, boolean satisfied, String missingText) {
    }

    public static final Type<RitualInfoPayload> TYPE =
            new Type<>(Gensokyou.id("ritual_info"));

    public static final StreamCodec<FriendlyByteBuf, RitualInfoPayload> STREAM_CODEC =
            StreamCodec.ofMember(RitualInfoPayload::write, RitualInfoPayload::read);

    private static void write(RitualInfoPayload payload, FriendlyByteBuf buf) {
        buf.writeLong(payload.pos);
        buf.writeUtf(payload.patternId);
        buf.writeVarInt(payload.tier);
        buf.writeBoolean(payload.enabled);
        buf.writeBoolean(payload.toggleable);
        buf.writeVarInt(payload.stored);
        buf.writeVarInt(payload.capacity);
        buf.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buf.writeUtf(entry.itemId());
            buf.writeVarInt(entry.count());
            buf.writeBoolean(entry.satisfied());
        }
        buf.writeVarInt(payload.actions.size());
        for (Action action : payload.actions) {
            buf.writeVarInt(action.id());
            buf.writeUtf(action.labelKey());
        }
        buf.writeVarInt(payload.recipes.size());
        for (RecipeInfo recipe : payload.recipes) {
            buf.writeUtf(recipe.recipeId());
            buf.writeBoolean(recipe.satisfied());
            buf.writeUtf(recipe.missingText());
        }
        buf.writeUtf(payload.activeRecipeId);
        buf.writeUtf(payload.statusKey);
    }

    private static RitualInfoPayload read(FriendlyByteBuf buf) {
        long pos = buf.readLong();
        String patternId = buf.readUtf();
        int tier = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        boolean toggleable = buf.readBoolean();
        int stored = buf.readVarInt();
        int capacity = buf.readVarInt();
        int entryCount = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(new Entry(buf.readUtf(), buf.readVarInt(), buf.readBoolean()));
        }
        int actionCount = buf.readVarInt();
        List<Action> actions = new ArrayList<>(actionCount);
        for (int i = 0; i < actionCount; i++) {
            actions.add(new Action(buf.readVarInt(), buf.readUtf()));
        }
        int recipeCount = buf.readVarInt();
        List<RecipeInfo> recipes = new ArrayList<>(recipeCount);
        for (int i = 0; i < recipeCount; i++) {
            recipes.add(new RecipeInfo(buf.readUtf(), buf.readBoolean(), buf.readUtf()));
        }
        String activeRecipeId = buf.readUtf();
        String statusKey = buf.readUtf();
        return new RitualInfoPayload(pos, patternId, tier, enabled, toggleable,
                stored, capacity, entries, actions, recipes, activeRecipeId, statusKey);
    }

    /** 服务端快照：由核心 BE 当前态 + 祭品门槛实时校验组装。 */
    public static RitualInfoPayload snapshot(ServerLevel level, BlockPos pos,
                                             RitualCoreBlockEntity core, String statusKey) {
        RitualMatch match = core.activeMatch();
        List<Entry> entries = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        List<RecipeInfo> recipes = new ArrayList<>();
        String patternId = "";
        int tier = 0;
        boolean toggleable = false;
        if (match != null) {
            patternId = match.patternId().toString();
            tier = match.level();
            Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(match.patternId());
            if (patternOpt.isPresent()) {
                RitualPattern pattern = patternOpt.get();
                toggleable = pattern.toggleable();
                RitualOfferings.Result result = RitualOfferings.check(pattern, match, level);
                for (RitualOfferings.SlotStatus status : result.slots()) {
                    ItemStack rep = status.requirement().item().representative();
                    String itemId = rep.isEmpty()
                            ? "" : BuiltInRegistries.ITEM.getKey(rep.getItem()).toString();
                    entries.add(new Entry(itemId, status.requirement().count(), status.satisfied()));
                }
            }
            RitualBehaviors.get(match.patternId()).ifPresent(behavior ->
                    behavior.uiActions(level, pos, match, core).forEach(action ->
                            actions.add(new Action(action.id(), action.labelKey()))));

            // 可用配方清单：等级过滤 + 逐条干跑匹配（✗ 附缺项/多余摘要）
            List<RitualRecipe> available = RitualRecipeLoader.forPattern(match.patternId()).stream()
                    .filter(r -> r.minTier() <= match.level())
                    .toList();
            RitualRecipeLoader.warnIfPatternMissing(match.patternId(), true);
            for (RitualRecipe recipe : available) {
                boolean satisfied = RitualRecipeMatcher.match(recipe, match, level).isPresent();
                String missing = satisfied ? "" : describeMismatch(recipe, level, match);
                recipes.add(new RecipeInfo(recipe.id().toString(), satisfied, missing));
            }
        }
        return new RitualInfoPayload(pos.asLong(), patternId, tier, core.isEnabled(), toggleable,
                core.getStored(), core.getCapacity(), List.copyOf(entries), List.copyOf(actions),
                List.copyOf(recipes),
                core.activeRecipeId() == null ? "" : core.activeRecipeId().toString(),
                statusKey);
    }

    /** ✗ 摘要：优先报缺失原料，其次报多余物品。 */
    private static String describeMismatch(RitualRecipe recipe, Level level, RitualMatch match) {
        var pools = RitualRecipeMatcher.collectPools(match, level);
        for (RitualRecipe.Ingredient ingredient : recipe.ingredients()) {
            int have = pools.stream().filter(pool -> ingredient.matches(pool.stack()))
                    .mapToInt(pool -> pool.stack().getCount()).sum();
            if (have < ingredient.count()) {
                ItemStack rep = ingredient.tag() != null
                        ? firstTagItem(ingredient.tag())
                        : new ItemStack(ingredient.item());
                String name = rep.isEmpty() ? "?" : rep.getHoverName().getString();
                return Component.translatable("msg.gensokyou.ritual_missing_ingredient",
                        ingredient.count() - have, name).getString();
            }
        }
        return Component.translatable("msg.gensokyou.ritual_extra_items").getString();
    }

    private static ItemStack firstTagItem(net.minecraft.tags.TagKey<Item> tag) {
        Optional<net.minecraft.core.HolderSet.Named<Item>> holders = BuiltInRegistries.ITEM.getTag(tag);
        if (holders.isPresent()) {
            for (var holder : holders.get()) {
                return new ItemStack(holder.value());
            }
        }
        return ItemStack.EMPTY;
    }

    public BlockPos blockPos() {
        return BlockPos.of(pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
