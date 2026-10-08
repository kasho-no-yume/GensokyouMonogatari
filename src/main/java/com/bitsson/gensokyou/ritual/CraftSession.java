package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.ritual.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class CraftSession extends RitualBehaviorState {

    private static final String TAG_CRAFT_PHASE = "CraftPhase";
    private static final String TAG_CRAFT_COLLECTED = "CraftCollected";
    private static final String TAG_CRAFT_COST = "CraftCost";
    private static final String TAG_CRAFT_FLIGHT_AGE = "CraftFlightAge";
    private static final String TAG_CRAFT_SESSION = "CraftSession";
    private static final String TAG_CRAFT_FLIGHT_IDS = "CraftFlightIds";
    private static final String TAG_CRAFT_RECIPE = "CraftRecipe";
    private static final String TAG_CRAFT_ID = "Id";

    private CraftPhase phase = CraftPhase.IDLE;
    private long sessionId;
    private @Nullable ResourceLocation recipeId;
    private long collected;
    /** 浼氳瘽瀹归噺鍙ｅ緞锛氶攣瀹氶厤鏂圭殑 spCost锛涚┖闂蹭负 0锛堜笉鍚姩涓嶇紦瀛樼伒鍔涳級銆?*/
    private long cost;
    private int ticks;
    private final List<Integer> flightIds = new ArrayList<>();

    public CraftPhase phase() {
        return phase;
    }

    public long sessionId() {
        return sessionId;
    }

    public @Nullable ResourceLocation recipeId() {
        return recipeId;
    }

    public long collected() {
        return collected;
    }

    public long cost() {
        return cost;
    }

    public int ticks() {
        return ticks;
    }

    public List<Integer> flightIds() {
        return List.copyOf(flightIds);
    }

    /** 鍚姩鏂颁細璇濓細鎹唬銆侀攣閰嶆柟銆佽繘鑱氱伒锛涜繑鍥炴柊浼氳瘽 id銆?*/
    public long begin(ResourceLocation recipe, long spCost) {
        sessionId++;
        recipeId = recipe;
        cost = spCost;
        collected = 0L;
        ticks = 0;
        phase = CraftPhase.PAYING;
        flightIds.clear();
        return sessionId;
    }

    public void addCollected(long amount) {
        collected += amount;
    }

    /** 鑱氱伒瓒抽 鈫?杩涘叆椋炶闃舵銆?*/
    public void enterFlight(List<Integer> entities) {
        flightIds.clear();
        flightIds.addAll(entities);
        phase = CraftPhase.FLIGHT;
        ticks = 0;
    }

    /** 椋炶璁℃椂鎺ㄨ繘涓€鏍笺€?*/
    public void advanceFlight() {
        ticks++;
    }

    /** 娓呴€€鍥炵┖闂诧紙姝ｅ父鏀跺熬/涓/鍙栨秷鍏辩敤锛涘凡鎶界伒鍔涗笉閫€锛夈€?*/
    public void clear() {
        phase = CraftPhase.IDLE;
        recipeId = null;
        collected = 0L;
        cost = 0L;
        ticks = 0;
        flightIds.clear();
    }

    /** 璇ヤ唬椋炶瀹炰綋鏄惁浠嶅湪浼氳瘽淇濇姢涓嬶紙瀹炰綋鏈嶅姟绔嚜寮冨垽鎹級銆?*/
    public boolean holdsFlight(long id) {
        return phase == CraftPhase.FLIGHT && sessionId == id;
    }

    @Override
    public boolean isEmpty() {
        return phase == CraftPhase.IDLE && flightIds.isEmpty()
                && collected == 0L && cost == 0L && sessionId == 0L;
    }

    public void save(CompoundTag tag) {
        if (phase == CraftPhase.IDLE && flightIds.isEmpty() && collected == 0L && cost == 0L) {
            return;
        }
        tag.putString(TAG_CRAFT_PHASE, phase.name());
        tag.putLong(TAG_CRAFT_SESSION, sessionId);
        tag.putLong(TAG_CRAFT_COLLECTED, collected);
        tag.putLong(TAG_CRAFT_COST, cost);
        tag.putInt(TAG_CRAFT_FLIGHT_AGE, ticks);
        if (recipeId != null) {
            tag.putString(TAG_CRAFT_RECIPE, recipeId.toString());
        }
        if (!flightIds.isEmpty()) {
            ListTag ids = new ListTag();
            for (int id : flightIds) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(TAG_CRAFT_ID, id);
                ids.add(entry);
            }
            tag.put(TAG_CRAFT_FLIGHT_IDS, ids);
        }
    }

    public void load(CompoundTag tag) {
        if (!tag.contains(TAG_CRAFT_PHASE)) {
            return;
        }
        try {
            phase = CraftPhase.valueOf(tag.getString(TAG_CRAFT_PHASE));
        } catch (IllegalArgumentException exception) {
            phase = CraftPhase.IDLE;
        }
        sessionId = tag.getLong(TAG_CRAFT_SESSION);
        collected = tag.getLong(TAG_CRAFT_COLLECTED);
        cost = tag.getLong(TAG_CRAFT_COST);
        ticks = tag.getInt(TAG_CRAFT_FLIGHT_AGE);
        recipeId = tag.contains(TAG_CRAFT_RECIPE)
                ? ResourceLocation.tryParse(tag.getString(TAG_CRAFT_RECIPE)) : null;
        flightIds.clear();
        for (var item : tag.getList(TAG_CRAFT_FLIGHT_IDS, CompoundTag.TAG_COMPOUND)) {
            flightIds.add(((CompoundTag) item).getInt(TAG_CRAFT_ID));
        }
    }

}
