package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.ritual.ClientRitualData;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualLootTable;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualSmeltRule;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JEI 插件（grace-ux-and-jei-tabs）：仪式配方按仪式分页（静态登记 + 兜底页签，方案 C——
 * JEI 19.44 无运行时 addCategories），全部数据由 ritual_recipes 派生；结构页签已删
 * （结构查看唯一入口 = 仪式构建器）。热重载经 {@link #syncFromLoader} 逐页签增删卡片、
 * 按空收敛显隐。
 *
 * <p><b>生命周期约定</b>：JEI 把运行时视为可丢弃的会话态——进入世界、客户端资源重载、
 * 重连三条路径都会重建 {@code RecipeManagerInternal}，连同全部配方与隐藏集一并丢弃。本类的
 * 同步基线只在单个运行时内有效，由 {@link #onRuntimeUnavailable()} 清空、并由
 * {@link #checkRuntime()} 的身份闩锁兜底，二者共同保证基线绝不跨会话复用（否则二次进入存档
 * 会向空管理器推送 0 张卡，页签从侧栏整体消失）。
 *
 * <p><b>数据来源</b>：四个同步方法一律读 {@link com.bitsson.gensokyou.client.ritual.ClientRitualData}
 * （服务端下发的快照 + 磁盘回退），不直读仅在逻辑服务端加载的数据加载器——后者在专用服务器
 * 客户端上恒为空，会使页签空白。
 */
@JeiPlugin
public class GensokyouJeiPlugin implements IModPlugin {

    /** 专属页签清单：新配方仪式要独立页签在此加一行（未登记的落兜底页签）。 */
    private static final List<ResourceLocation> DEDICATED_TABS = List.of(
            RitualBehaviors.ZAOHUA,
            RitualBehaviors.KAMI_NO_MEGUMI);

    /** 献祭工具仪式：各自一页签，页签内按工具材质翻页（数据源 ritual_loot）。 */
    private static final List<ResourceLocation> SACRIFICE_TABS = List.of(
            RitualBehaviors.OYAMATSUMI,
            RitualBehaviors.KUKUNOCHI,
            RitualBehaviors.HANIYASU,
            RitualBehaviors.KAYA_NO_HIME);

    /** 煅炉仪式：各自一页签，卡面直接列出「矿石 + 灵炭配比 → 矿物」（数据源 ritual_smelt_recipes）。 */
    private static final List<ResourceLocation> SMELT_TABS = List.of(
            RitualBehaviors.KANAYAMAHIKO);
    private static final RecipeType<RitualRecipeCardWrapper> FALLBACK_TYPE =
            RitualRecipeCategory.typeFor(null);

    static boolean hasDedicatedTab(ResourceLocation patternId) {
        return DEDICATED_TABS.contains(patternId);
    }

    private static volatile IJeiRuntime runtime;
    private static volatile IJeiRuntime lastSyncedRuntime;
    private static volatile Map<ResourceLocation, RitualRecipeCardWrapper> syncedRecipes = Map.of();
    private static volatile Map<ResourceLocation, List<RitualLootCardWrapper>> syncedLoot = Map.of();
    private static volatile int syncedLootSignature = Integer.MIN_VALUE;
    private static volatile List<WatatsumiLootCardWrapper> syncedWatatsumi = List.of();
    private static volatile Map<ResourceLocation, List<RitualSmeltCardWrapper>> syncedSmelt = Map.of();

    /**
     * 丢弃全部同步基线。基线只在单个 JEI 运行时内有效——JEI 每次进入世界、资源重载或重连都会
     * 重建 {@code RecipeManagerInternal}（连同隐藏集一并丢弃），故跨运行时复用基线会让差分误判
     * "无变化"，向空管理器推送 0 张卡。基线值全部取空，使随后的同步走全量推送路径。
     */
    private static void resetBaselines() {
        syncedRecipes = Map.of();
        syncedLoot = Map.of();
        syncedLootSignature = Integer.MIN_VALUE;
        syncedWatatsumi = List.of();
        syncedSmelt = Map.of();
    }

    /**
     * 取当前运行时并校准身份闩锁：运行时实例一经更换即说明配方库已被重建，基线随之作废。
     * 与 {@link #onRuntimeUnavailable()} 互补——前者是"被通知时清空"，本方法是"发现不一致时清空"。
     *
     * @return 可用运行时；未就绪时为 null（调用方须直接返回）
     */
    private static IJeiRuntime checkRuntime() {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return null;
        }
        if (rt != lastSyncedRuntime) {
            resetBaselines();
            lastSyncedRuntime = rt;
        }
        return rt;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Gensokyou.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        List<RitualRecipeCategory> categories = new ArrayList<>();
        for (ResourceLocation patternId : DEDICATED_TABS) {
            categories.add(new RitualRecipeCategory(guiHelper,
                    RitualRecipeCategory.typeFor(patternId), patternId, tabIcon(guiHelper, patternId)));
        }
        categories.add(new RitualRecipeCategory(guiHelper, FALLBACK_TYPE, null,
                tabIcon(guiHelper, null)));
        registration.addRecipeCategories(categories.toArray(new RitualRecipeCategory[0]));

        List<RitualLootCategory> lootCategories = new ArrayList<>();
        for (ResourceLocation patternId : SACRIFICE_TABS) {
            lootCategories.add(new RitualLootCategory(guiHelper, patternId,
                    tabIcon(guiHelper, null)));
        }
        registration.addRecipeCategories(lootCategories.toArray(new RitualLootCategory[0]));

        registration.addRecipeCategories(new WatatsumiLootCategory(guiHelper, RitualBehaviors.WATATSUMI,
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                        new ItemStack(Items.FISHING_ROD))));

        List<RitualSmeltCategory> smeltCategories = new ArrayList<>();
        for (ResourceLocation patternId : SMELT_TABS) {
            smeltCategories.add(new RitualSmeltCategory(guiHelper, patternId,
                    guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                            new ItemStack(Items.FURNACE))));
        }
        registration.addRecipeCategories(smeltCategories.toArray(new RitualSmeltCategory[0]));
    }

    private static IDrawable tabIcon(IGuiHelper guiHelper, @Nullable ResourceLocation patternId) {
        ItemStack icon = RitualBehaviors.ZAOHUA.equals(patternId)
                ? new ItemStack(ModItems.SPELLCARD_STAR.get())
                : RitualBehaviors.KAMI_NO_MEGUMI.equals(patternId)
                        ? new ItemStack(ModItems.SPIRIT_CORES.get(0).get())
                        : new ItemStack(ModItems.RITUAL_CORE_ITEM.get());
        return guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, icon);
    }

    /** 无尽藏终端：JEI 合成配方 `+` 一键从仓储取料填入终端合成格。 */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new WujinzangRecipeTransferHandler(),
                RecipeTypes.CRAFTING);
    }

    /**
     * 依 {@link ClientRitualData} 当前内容刷新全部页签。数据源统一走服务端快照（含磁盘回退），
     * 不直读仅在逻辑服务端加载的数据加载器——后者在专用服务器客户端上恒为空，会使页签空白。
     *
     * <p>由两个真事件驱动，无每 tick 轮询：JEI 运行时建立（{@link #onRuntimeAvailable}）
     * 与服务端快照到达（{@code ClientPayloadHandler.handleRitualDataSync}）。稳态下四个同步
     * 方法各自早退，为零操作。
     */
    public static void resyncAll() {
        if (runtime == null) {
            return;
        }
        syncFromLoader(ClientRitualData.recipesAll());
        syncLoot(ClientRitualData.lootsAll());
        syncWatatsumi();
        syncSmelt(ClientRitualData.smeltsAll());
    }

    /**
     * JEI 每次进入世界/资源重载/重连都重建整个配方库，此时必须重新全量推送。
     * 身份闩锁（{@link #checkRuntime()}）保证此处换上的新 runtime 会让四个同步方法全部走全量路径。
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        resyncAll();
    }

    /**
     * JEI 运行时不可用（玩家登出/退出世界、/reload、重连）：丢弃运行时引用与全部同步基线。
     * JEI 三条通往空配方库的路径均先经 {@code JeiStarter.stop()} 无条件回调本方法，故此处
     * 的清理必然早于任何重新填充；配合身份闩锁即封死"基线跨会话复用"这一故障。
     */
    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        lastSyncedRuntime = null;
        resetBaselines();
    }

    /**
     * 煅炉配方卡同步：按煅炉增删卡片；无内容（数据未载入）时隐藏页签。
     * 数据源为服务端下发的仪式快照（{@link com.bitsson.gensokyou.client.ritual.ClientRitualData}），
     * 与指导书 smelt_page 完全同源，故书里与 JEI 里的配比必然一致。
     */
    static void syncSmelt(List<RitualSmeltRule> rules) {
        IJeiRuntime rt = checkRuntime();
        if (rt == null) {
            return;
        }
        Map<ResourceLocation, List<RitualSmeltCardWrapper>> desired = new LinkedHashMap<>();
        for (ResourceLocation patternId : SMELT_TABS) {
            List<RitualSmeltCardWrapper> cards = new ArrayList<>();
            for (RitualSmeltRule rule : rules) {
                if (rule.patternId().equals(patternId)) {
                    cards.add(RitualSmeltCardWrapper.of(rule));
                }
            }
            desired.put(patternId, List.copyOf(cards));
        }
        if (desired.equals(syncedSmelt)) {
            return;
        }
        IRecipeManager manager = rt.getRecipeManager();
        for (ResourceLocation patternId : SMELT_TABS) {
            RecipeType<RitualSmeltCardWrapper> type = RitualSmeltCategory.typeFor(patternId);
            List<RitualSmeltCardWrapper> old = syncedSmelt.getOrDefault(patternId, List.of());
            List<RitualSmeltCardWrapper> neu = desired.getOrDefault(patternId, List.of());
            if (old.equals(neu)) {
                continue;
            }
            if (!old.isEmpty()) {
                manager.hideRecipes(type, old);
            }
            if (neu.isEmpty()) {
                manager.hideRecipeCategory(type);
            } else {
                manager.unhideRecipeCategory(type);
                manager.addRecipes(type, neu);
            }
        }
        syncedSmelt = Map.copyOf(desired);
    }

    /** 献祭权重卡同步：按仪式增删卡片；无内容（数据未载入）时隐藏页签。 */
    static void syncLoot(List<RitualLootTable> tables) {
        IJeiRuntime rt = checkRuntime();
        if (rt == null) {
            return;
        }
        int signature = tables.hashCode();
        if (signature == syncedLootSignature) {
            return;
        }
        Map<ResourceLocation, List<RitualLootCardWrapper>> desired = new LinkedHashMap<>();
        for (ResourceLocation patternId : SACRIFICE_TABS) {
            List<RitualLootCardWrapper> cards = new ArrayList<>();
            for (RitualLootTable table : tables) {
                if (!table.patternId().equals(patternId)) {
                    continue;
                }
                for (RitualLootTable.TierTable tierTable : table.tables()) {
                    cards.add(RitualLootCardWrapper.of(table, tierTable.tier()));
                }
            }
            desired.put(patternId, cards);
        }
        if (desired.equals(syncedLoot)) {
            syncedLootSignature = signature;
            return;
        }
        IRecipeManager manager = rt.getRecipeManager();
        for (ResourceLocation patternId : SACRIFICE_TABS) {
            RecipeType<RitualLootCardWrapper> type = RitualLootCategory.typeFor(patternId);
            List<RitualLootCardWrapper> old = syncedLoot.getOrDefault(patternId, List.of());
            List<RitualLootCardWrapper> neu = desired.getOrDefault(patternId, List.of());
            if (old.equals(neu)) {
                continue;
            }
            if (!old.isEmpty()) {
                manager.hideRecipes(type, old);
            }
            if (neu.isEmpty()) {
                manager.hideRecipeCategory(type);
            } else {
                manager.unhideRecipeCategory(type);
                manager.addRecipes(type, neu);
            }
        }
        syncedLoot = Map.copyOf(desired);
        syncedLootSignature = signature;
    }

    /** 绵津见卡同步：固定 3 张（等级 0/1/2）；特产池数据变化即刷新。 */
    static void syncWatatsumi() {
        IJeiRuntime rt = checkRuntime();
        if (rt == null) {
            return;
        }
        List<WatatsumiLootCardWrapper> desired = List.of(
                WatatsumiLootCardWrapper.of(0),
                WatatsumiLootCardWrapper.of(1),
                WatatsumiLootCardWrapper.of(2));
        if (desired.equals(syncedWatatsumi)) {
            return;
        }
        IRecipeManager manager = rt.getRecipeManager();
        RecipeType<WatatsumiLootCardWrapper> type =
                WatatsumiLootCategory.typeFor(RitualBehaviors.WATATSUMI);
        if (!syncedWatatsumi.isEmpty()) {
            manager.hideRecipes(type, syncedWatatsumi);
        }
        manager.unhideRecipeCategory(type);
        manager.addRecipes(type, desired);
        syncedWatatsumi = desired;
    }

    /** 配方集变化即逐页签增删卡片（稳态零操作，由 {@link #resyncAll()} 在真事件上触发）。 */
    static void syncFromLoader(List<RitualRecipe> current) {
        IJeiRuntime rt = checkRuntime();
        if (rt == null) {
            return;
        }
        Map<ResourceLocation, RitualRecipeCardWrapper> previous = syncedRecipes;
        Map<ResourceLocation, RitualRecipeCardWrapper> desired = new LinkedHashMap<>();
        for (RitualRecipe recipe : current) {
            RitualRecipeCardWrapper old = previous.get(recipe.id());
            desired.put(recipe.id(), old != null && old.definition().equals(recipe)
                    ? old : new RitualRecipeCardWrapper(recipe));
        }
        if (desired.size() == previous.size() && desired.equals(previous)) {
            return; // 稳态：无任何页签/卡片变化
        }
        IRecipeManager manager = rt.getRecipeManager();
        Map<RecipeType<RitualRecipeCardWrapper>, List<RitualRecipeCardWrapper>> keep =
                new HashMap<>();
        Map<RecipeType<RitualRecipeCardWrapper>, List<RitualRecipeCardWrapper>> drop =
                new HashMap<>();
        for (RitualRecipeCardWrapper card : desired.values()) {
            RitualRecipeCardWrapper old = previous.get(card.definition().id());
            if (old == null || old != card) {
                keep.computeIfAbsent(typeOf(card), k -> new ArrayList<>()).add(card);
            }
        }
        for (RitualRecipeCardWrapper old : previous.values()) {
            if (desired.get(old.definition().id()) != old) {
                drop.computeIfAbsent(typeOf(old), k -> new ArrayList<>()).add(old);
            }
        }
        for (Map.Entry<RecipeType<RitualRecipeCardWrapper>, List<RitualRecipeCardWrapper>> entry
                : keep.entrySet()) {
            manager.unhideRecipeCategory(entry.getKey());
            manager.addRecipes(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<RecipeType<RitualRecipeCardWrapper>, List<RitualRecipeCardWrapper>> entry
                : drop.entrySet()) {
            manager.hideRecipes(entry.getKey(), entry.getValue());
        }
        // 空页签不占侧栏（兜底页签常态下即此状态）
        for (RecipeType<RitualRecipeCardWrapper> type : allTabTypes()) {
            boolean empty = desired.values().stream().noneMatch(card -> typeOf(card) == type);
            if (empty) {
                manager.hideRecipeCategory(type);
            }
        }
        syncedRecipes = Map.copyOf(desired);
    }

    private static RecipeType<RitualRecipeCardWrapper> typeOf(RitualRecipeCardWrapper card) {
        ResourceLocation patternId = card.definition().patternId();
        return RitualRecipeCategory.typeFor(hasDedicatedTab(patternId) ? patternId : null);
    }

    private static List<RecipeType<RitualRecipeCardWrapper>> allTabTypes() {
        List<RecipeType<RitualRecipeCardWrapper>> types = new ArrayList<>();
        for (ResourceLocation patternId : DEDICATED_TABS) {
            types.add(RitualRecipeCategory.typeFor(patternId));
        }
        types.add(FALLBACK_TYPE);
        return types;
    }
}
