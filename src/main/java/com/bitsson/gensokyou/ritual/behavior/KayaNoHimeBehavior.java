package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualMatch;

/**
 * 草野姬神之亭（锄）：工具献祭行为。
 * 数据表 {@code data/gensokyou/ritual_loot/kaya_no_hime_circle.json}。
 */
public class KayaNoHimeBehavior extends ToolSacrificeBehavior {

    @Override
    protected String langPrefix() {
        return "kaya_no_hime";
    }

    @Override
    protected int accentColor() {
        return 0xFF81C784;
    }
}