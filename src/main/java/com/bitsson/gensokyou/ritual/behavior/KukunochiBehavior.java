package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualMatch;

/**
 * 久久能智神庭（斧）：工具献祭行为。
 * 数据表 {@code data/gensokyou/ritual_loot/kukunochi_circle.json}。
 */
public class KukunochiBehavior extends ToolSacrificeBehavior {

    @Override
    protected String langPrefix() {
        return "kukunochi";
    }

    @Override
    protected int accentColor() {
        return 0xFF8D6E63;
    }
}