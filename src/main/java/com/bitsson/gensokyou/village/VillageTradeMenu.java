package com.bitsson.gensokyou.village;

import com.bitsson.gensokyou.entity.VillageNpcEntity;
import com.bitsson.gensokyou.event.GuideTierProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;

/** Uses the vanilla merchant protocol/screen, with validation before either ordinary or shift-click purchases. */
public final class VillageTradeMenu extends MerchantMenu {
    private final VillageNpcEntity npc;
    private final ServerPlayer customer;
    private final long day;
    private final int tier;
    public VillageTradeMenu(int id, Inventory inventory, Merchant merchant, VillageNpcEntity npc,
                            ServerPlayer customer, long day, int tier) {
        super(id, inventory, merchant); this.npc = npc; this.customer = customer; this.day = day; this.tier = tier;
    }
    public VillageNpcEntity npc() { return npc; }
    @Override public boolean stillValid(Player player) {
        if (customer != player || !player.isAlive() || player.isSpectator() || !npc.isAlive()
                || player.level() != npc.level() || player.distanceToSqr(npc) > 64
                || VillageServices.data(customer).day() != day || GuideTierProgress.worldTier(customer) != tier) return false;
        var stock = VillageServices.data(customer).shops().get(npc.villageRole().key());
        return stock != null && getOffers().stream().allMatch(o -> stock.stream().anyMatch(s ->
                ItemStack.isSameItemSameComponents(s.result(), o.getResult()) && VillageShops.allowed(s, tier)));
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) { customer.closeContainer(); return; }
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player)) return ItemStack.EMPTY;
        if (index != 2) return super.quickMoveStack(player, index);
        // Vanilla's result branch casts Merchant to Entity for its sound; personal merchants are not entities.
        var slot = slots.get(2);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var remaining = slot.getItem(); var original = remaining.copy();
        if (!moveItemStackTo(remaining, 3, 39, true)) return ItemStack.EMPTY;
        slot.onQuickCraft(remaining, original);
        if (remaining.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (remaining.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, remaining);
        return original;
    }
}
