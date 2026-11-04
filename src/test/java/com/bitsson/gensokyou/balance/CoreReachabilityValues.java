package com.bitsson.gensokyou.balance;

import com.bitsson.gensokyou.item.weapon.RuneAffix;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 生成词条 → 预算核验数值映射（spirit_cost_pct 取绝对值）；测试共用。 */
final class CoreReachabilityValues {

    static Map<String, Double> valuesOf(List<RuneAffix> rolled) {
        Map<String, Double> out = new HashMap<>();
        for (RuneAffix a : rolled) {
            double v = a.value();
            if (a.affixId().equals("spirit_cost_pct")) {
                v = Math.abs(v);
            }
            out.put(a.affixId(), v);
        }
        return out;
    }

    private CoreReachabilityValues() {
    }
}
