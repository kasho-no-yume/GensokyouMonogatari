package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 「旧弹加载标记」的可自动验证部分：后端选择标记的 NBT 往返与旧存档缺键回退。
 * 实机旧存档续行仍属未执行的实机项。
 */
class ProgramBackendStateTest {
    @Test void roundTripsModeAndCard() {
        var tag = new CompoundTag();
        new ProgramBackendState(true, 4).write(tag);
        var back = ProgramBackendState.read(tag).orElseThrow();
        assertTrue(back.programMode());
        assertEquals(4, back.cardIndex());
    }

    @Test void legacySaveWithoutKeyDegradesWithoutError() {
        var legacy = new CompoundTag();
        legacy.putDouble("AnchorX", 1.0D);
        legacy.putString("BossSpawnRoll", "old");
        assertEquals(java.util.Optional.empty(), ProgramBackendState.read(legacy),
                "缺键的旧存档回退到 config 决定，不报错");
    }

    @Test void modeOnlyTagReadsWithZeroCard() {
        var tag = new CompoundTag();
        var nested = new CompoundTag();
        nested.putBoolean("mode", false);
        tag.put("GensokyouProgramBackend", nested);
        var back = ProgramBackendState.read(tag).orElseThrow();
        assertFalse(back.programMode());
        assertEquals(0, back.cardIndex());
    }
}
