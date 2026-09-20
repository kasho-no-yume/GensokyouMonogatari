package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.menu.WujinzangTerminalMenu;
import com.bitsson.gensokyou.network.WujinzangRecipeFillPayload;
import com.bitsson.gensokyou.registry.ModMenus;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 无尽藏终端的 JEI 配方转移处理器：合成配方界面点 `+` 即把 9 个输入发往服务端，
 * 由服务端从无尽藏仓储（不足回退背包）取料填入终端的 3×3 合成格。
 *
 * <p>客户端只发"配方想要什么"，不做任何预校验——仓储内容不在客户端；缺料由服务端留空，
 * JEI 仍照常显示 `+`。
 */
public class WujinzangRecipeTransferHandler
        implements IRecipeTransferHandler<WujinzangTerminalMenu, RecipeHolder<CraftingRecipe>> {

    @Override
    public Class<? extends WujinzangTerminalMenu> getContainerClass() {
        return WujinzangTerminalMenu.class;
    }

    @Override
    public Optional<MenuType<WujinzangTerminalMenu>> getMenuType() {
        return Optional.of(ModMenus.WUJINZANG_TERMINAL.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(WujinzangTerminalMenu container,
                                               RecipeHolder<CraftingRecipe> recipe,
                                               IRecipeSlotsView recipeSlots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            return null;
        }
        List<ItemStack> ingredients = new ArrayList<>();
        for (IRecipeSlotView slot : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            ingredients.add(slot.getItemStacks().findFirst().orElse(ItemStack.EMPTY));
        }
        PacketDistributor.sendToServer(new WujinzangRecipeFillPayload(
                container.containerId, ingredients));
        return null;
    }
}
