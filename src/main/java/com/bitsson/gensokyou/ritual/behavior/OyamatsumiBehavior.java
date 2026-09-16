package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualMatch;

/**
 * 大山祇神之座（镐）：工具献祭行为。
 * 数据表 {@code data/gensokyou/ritual_loot/oyamatsumi_circle.json}。
 */
public class OyamatsumiBehavior extends ToolSacrificeBehavior {

    @Override
    protected String langPrefix() {
        return "oyamatsumi";
    }

    @Override
    protected int accentColor() {
        return 0xFF9E9E9E;
    }
}