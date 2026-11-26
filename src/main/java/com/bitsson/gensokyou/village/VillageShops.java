package com.bitsson.gensokyou.village;

import com.bitsson.gensokyou.entity.VillageNpcEntity;
import com.bitsson.gensokyou.event.GuideTierProgress;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VillageShops {
    public static void open(ServerPlayer player, VillageNpcEntity npc) {
        if (!npc.villageRole().shop() || !npc.isAlive() || player.level() != npc.level() || player.distanceToSqr(npc) > 64) return;
        var state = VillageServices.data(player);
        int tier = GuideTierProgress.worldTier(player);
        List<VillagePlayerData.Offer> stock = state.shops().get(npc.villageRole().key());
        if (stock == null) {
            var pool = VillageShopCatalog.candidates(player, npc.villageRole()).stream().filter(e -> e.offer().tier() <= tier).toList();
            stock = DailySelection.pick(pool, 5, VillageServices.seed(player, npc.villageRole().key())).stream().map(VillageShopCatalog.Entry::offer).toList();
            state = state.stock(npc.villageRole(), stock); VillageServices.store(player, state);
        }
        var merchant = new PersonalMerchant(player, npc, state.day(), tier, stock);
        if (merchant.getOffers().isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.village.no_stock"), false); return;
        }
        long day = state.day();
        var opened = player.openMenu(new SimpleMenuProvider((id, inventory, customer) ->
                new VillageTradeMenu(id, inventory, merchant, npc, player, day, tier), npc.getDisplayName()));
        if (opened.isPresent()) player.sendMerchantOffers(opened.getAsInt(), merchant.getOffers(), 1, 0, false, false);
    }
    public static boolean allowed(VillagePlayerData.Offer offer, int tier) {
        return offer.tier() <= tier && !DailyCommissions.turnInItems().contains(BuiltInRegistries.ITEM.getKey(offer.result().getItem()))
                && !VillageShopCatalog.excluded(offer.result().getItemHolder(), net.minecraft.core.registries.Registries.ITEM);
    }
    private static final class PersonalMerchant implements Merchant {
        private Player customer;
        private final ServerPlayer player;
        private final VillageNpcEntity npc;
        private final long day;
        private final MerchantOffers offers = new MerchantOffers();
        private final List<Integer> indices = new ArrayList<>();
        private PersonalMerchant(ServerPlayer player, VillageNpcEntity npc, long day, int tier, List<VillagePlayerData.Offer> stock) {
            this.customer = player; this.player = player; this.npc = npc; this.day = day;
            for (int n = 0; n < stock.size(); n++) {
                var entry = stock.get(n);
                if (!allowed(entry, tier)) continue;
                indices.add(n);
                offers.add(new MerchantOffer(new ItemCost(ModItems.YEN.get(), Math.min(64, entry.price())),
                        entry.price() > 64 ? Optional.of(new ItemCost(ModItems.YEN.get(), entry.price() - 64)) : Optional.empty(),
                        entry.result().copy(), entry.uses(), entry.maxUses(), 0, 0F));
            }
        }
        @Override public void setTradingPlayer(Player value) { customer = value; }
        @Override public Player getTradingPlayer() { return customer; }
        @Override public MerchantOffers getOffers() { return offers; }
        @Override public void overrideOffers(MerchantOffers values) { throw new UnsupportedOperationException("Server-owned daily stock"); }
        @Override public void notifyTrade(MerchantOffer offer) {
            int index = offers.indexOf(offer);
            if (index < 0) return;
            offer.increaseUses();
            var state = VillageServices.data(player);
            if (state.day() != day) throw new IllegalStateException("Expired village trade");
            var stock = new ArrayList<>(state.shops().get(npc.villageRole().key()));
            int saved = indices.get(index); stock.set(saved, stock.get(saved).used(offer.getUses()));
            VillageServices.store(player, state.stock(npc.villageRole(), stock));
            npc.playSound(SoundEvents.VILLAGER_YES, .6F, 1F);
        }
        @Override public void notifyTradeUpdated(ItemStack stack) {}
        @Override public int getVillagerXp() { return 0; }
        @Override public void overrideXp(int xp) {}
        @Override public boolean showProgressBar() { return false; }
        @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.VILLAGER_YES; }
        @Override public boolean isClientSide() { return false; }
    }
    private VillageShops() {}
}
