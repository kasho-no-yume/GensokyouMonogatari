package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Optional;

/**
 * 持久化的后端选择标记（migrate tasks 1.6「旧弹加载标记」的可自动验证部分）。
 *
 * <p>BOSS 实体把「本场战斗选了哪个判伤后端 + 当前符卡下标」写入自身 NBT。这样：
 * <ul>
 *   <li>重载后不会在 config 变化时半场热切换后端；</li>
 *   <li>旧存档缺键时返回 {@link Optional#empty()}，实体按当前 config 重新决定，行为不劣于本变更之前，
 *       且 MUST NOT 报错（对齐 {@code danmaku-age-continuity} 的「旧存档缺键回退」口径）。</li>
 * </ul>
 * 本标记不改变任何 legacy 弹幕实体自身的存档规则：旧弹继续按既有 NBT 加载。
 */
public record ProgramBackendState(boolean programMode, int cardIndex) {
    private static final String TAG = "GensokyouProgramBackend";
    private static final String MODE = "mode";
    private static final String CARD = "card";

    public ProgramBackendState {
        if (cardIndex < 0) throw new IllegalArgumentException("nonnegative card index required");
    }

    public void write(CompoundTag tag) {
        CompoundTag nested = new CompoundTag();
        nested.putBoolean(MODE, programMode);
        nested.putInt(CARD, cardIndex);
        tag.put(TAG, nested);
    }

    /** 缺键 / 旧存档 → 空；调用方据此回退到 config 决定，不报错。 */
    public static Optional<ProgramBackendState> read(CompoundTag tag) {
        if (!tag.contains(TAG, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag nested = tag.getCompound(TAG);
        if (!nested.contains(MODE, Tag.TAG_BYTE)) return Optional.empty();
        int card = nested.contains(CARD, Tag.TAG_INT) ? nested.getInt(CARD) : 0;
        return Optional.of(new ProgramBackendState(nested.getBoolean(MODE), Math.max(0, card)));
    }
}
