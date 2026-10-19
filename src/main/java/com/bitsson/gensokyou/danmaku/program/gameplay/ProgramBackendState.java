package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Optional;

/**
 * 兼容迁移期的后端标记与当前符卡下标。
 *
 * <p>正式内容始终使用程序战斗。旧 legacy 标记不再阻止迁移；其卡号不用于强制恢复新程序卡，
 * 由当前血量选择阶段。旧程序标记仍可恢复原卡号，完整运行态由 ProgramSessionStore 承载。
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

    /** 缺键 / 旧存档 → 空；正式战斗按当前血量安装程序，不报错。 */
    public static Optional<ProgramBackendState> read(CompoundTag tag) {
        if (!tag.contains(TAG, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag nested = tag.getCompound(TAG);
        if (!nested.contains(MODE, Tag.TAG_BYTE)) return Optional.empty();
        int card = nested.contains(CARD, Tag.TAG_INT) ? nested.getInt(CARD) : 0;
        return Optional.of(new ProgramBackendState(nested.getBoolean(MODE), Math.max(0, card)));
    }
}
