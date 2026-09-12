package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.ritual.RitualPattern;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从已加载 {@link RitualPattern} 构造 {@link RitualDiffCapture} 所需视图/地基层（运行期注册表访问）。 */
public final class RitualEditorViews {

    private RitualEditorViews() {
    }

    /** palette 谓词 → 字符串值视图（EXACT=注册表 id，TAG=#id，AIR/IGNORE 归一）。 */
    public static RitualDiffCapture.PatternView viewOf(RitualPattern pattern) {
        Map<Character, String> values = new LinkedHashMap<>();
        for (Map.Entry<Character, RitualPattern.Predicate> e : pattern.palette().entrySet()) {
            RitualPattern.Predicate predicate = e.getValue();
            String value = switch (predicate.kind()) {
                case EXACT -> BuiltInRegistries.BLOCK.getKey(predicate.block()).toString();
                case TAG -> "#" + predicate.tag().location();
                case AIR -> "minecraft:air";
                case IGNORE -> "_ignore";
            };
            values.put(e.getKey(), value);
        }
        return new RitualDiffCapture.PatternView(pattern.anchorKey(), values);
    }

    /** 捕获阶级 N 的地基 = 阶级号严格小于 N 的最高累积切片；最低阶则地基仅锚点格。 */
    public static List<RitualPattern.BlockEntry> groundOf(RitualPattern pattern, int levelNumber) {
        RitualPattern.LevelSlice previous = null;
        for (RitualPattern.LevelSlice slice : pattern.levels()) { // loader 已升序
            if (slice.level() < levelNumber) {
                previous = slice;
            } else {
                break;
            }
        }
        if (previous != null) {
            return previous.blocks();
        }
        List<RitualPattern.BlockEntry> onlyAnchor = new ArrayList<>();
        onlyAnchor.add(new RitualPattern.BlockEntry(pattern.anchorKey(), 0, 0, 0, null));
        return onlyAnchor;
    }
}
