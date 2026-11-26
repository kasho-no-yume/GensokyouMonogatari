package com.bitsson.gensokyou.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Personal receipts live outside quest reload epochs; a restart or /reload never grants a fresh daily quota. */
public record VillagePlayerData(long day, List<String> introduced, Map<String, List<Offer>> shops,
                                List<ResourceLocation> commissions, List<ResourceLocation> accepted) {
    public static final Codec<VillagePlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(VillagePlayerData::day),
            Codec.STRING.listOf().optionalFieldOf("introduced", List.of()).forGetter(VillagePlayerData::introduced),
            Codec.unboundedMap(Codec.STRING, Offer.CODEC.listOf()).optionalFieldOf("shops", Map.of()).forGetter(VillagePlayerData::shops),
            ResourceLocation.CODEC.listOf().optionalFieldOf("commissions", List.of()).forGetter(VillagePlayerData::commissions),
            ResourceLocation.CODEC.listOf().optionalFieldOf("accepted", List.of()).forGetter(VillagePlayerData::accepted)
    ).apply(i, VillagePlayerData::new));
    public VillagePlayerData {
        introduced = List.copyOf(introduced); commissions = List.copyOf(commissions); accepted = List.copyOf(accepted);
        var copy = new LinkedHashMap<String, List<Offer>>(); shops.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        shops = Map.copyOf(copy);
    }
    public static VillagePlayerData empty() { return new VillagePlayerData(-1, List.of(), Map.of(), List.of(), List.of()); }
    public VillagePlayerData forDay(long current) {
        return current > day ? new VillagePlayerData(current, introduced, Map.of(), List.of(), List.of()) : this;
    }
    public boolean knows(VillageRole role) { return introduced.contains(role.key()); }
    public VillagePlayerData introduce(VillageRole role) {
        if (knows(role)) return this;
        var next = new ArrayList<>(introduced); next.add(role.key());
        return new VillagePlayerData(day, next, shops, commissions, accepted);
    }
    public VillagePlayerData stock(VillageRole role, List<Offer> offers) {
        var next = new LinkedHashMap<>(shops); next.put(role.key(), List.copyOf(offers));
        return new VillagePlayerData(day, introduced, next, commissions, accepted);
    }
    public VillagePlayerData publish(List<ResourceLocation> ids) { return new VillagePlayerData(day, introduced, shops, ids, accepted); }
    public VillagePlayerData accept(ResourceLocation id) {
        if (accepted.contains(id)) return this;
        var next = new ArrayList<>(accepted); next.add(id);
        return new VillagePlayerData(day, introduced, shops, commissions, next);
    }
    public record Offer(ItemStack result, int tier, int price, int maxUses, int uses) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("result").forGetter(Offer::result),
                Codec.intRange(0, 5).fieldOf("tier").forGetter(Offer::tier),
                Codec.intRange(1, 128).fieldOf("price").forGetter(Offer::price),
                Codec.intRange(1, 64).fieldOf("max_uses").forGetter(Offer::maxUses),
                Codec.intRange(0, 64).optionalFieldOf("uses", 0).forGetter(Offer::uses)
        ).apply(i, Offer::new));
        public Offer { result = result.copy(); }
        public Offer used(int count) { return new Offer(result, tier, price, maxUses, Math.min(maxUses, count)); }
    }
}
