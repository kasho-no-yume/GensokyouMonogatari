package com.bitsson.gensokyou.worldgen.human_village;

import java.util.LinkedHashMap;
import java.util.Map;

final class VillageTestPlans {
    private static final Map<Long,HumanVillagePlan> PLANS=new LinkedHashMap<>();
    static synchronized HumanVillagePlan get(long seed) { return PLANS.computeIfAbsent(seed,HumanVillagePlanner::plan); }
    static final long[] SEEDS={0,1,42,-8712,Long.MAX_VALUE};
}
