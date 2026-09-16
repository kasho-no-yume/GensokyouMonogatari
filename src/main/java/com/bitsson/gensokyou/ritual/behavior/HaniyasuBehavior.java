package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualMatch;

/**
 * 埴山姬神之壤（铲）：工具献祭行为。
 * 数据表 {@code data/gensokyou/ritual_loot/haniyasu_circle.json}。
 */
public class HaniyasuBehavior extends ToolSacrificeBehavior {

    @Override
    protected String langPrefix() {
        return "haniyasu";
    }

    @Override
    protected int accentColor() {
        return 0xFFBCAAA4;
    }
}