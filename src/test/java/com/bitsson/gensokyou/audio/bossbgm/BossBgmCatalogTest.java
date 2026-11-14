package com.bitsson.gensokyou.audio.bossbgm;

import com.bitsson.gensokyou.audio.SoundIds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 曲目目录 ↔ {@code sounds.json} ↔ 声音事件注册表 ↔ 音频文件 的四方一致性。
 *
 * <p>与 {@code AudioBootstrap} 的加载期校验走同一个 {@link BossBgms#validate()}：
 * 加载期把问题逐条记 ERROR（不打断游戏），这里把同一批问题变成 CI 的红。
 */
class BossBgmCatalogTest {

    @Test
    void catalogAndResourcesAreConsistent() {
        List<String> problems = BossBgms.validate();
        assertTrue(problems.isEmpty(),
                "BGM 目录与资源不一致：\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyTierHasATrackAndOutOfRangeFallsBackToOne() {
        for (int tier = 1; tier <= 5; tier++) {
            BgmTrack track = BossBgms.forTier(tier);
            assertEquals(tier, track.tier(), "阶级 " + tier + " 必须解析到同名曲目");
            assertTrue(track.volume() > 0.0F && track.volume() <= 1.0F);
        }
        assertEquals(BgmTrack.TIER1, BossBgms.forTier(0));
        assertEquals(BgmTrack.TIER1, BossBgms.forTier(-3));
        assertEquals(BgmTrack.TIER1, BossBgms.forTier(9));
    }

    @Test
    void musicRegistrationListMatchesCatalog() {
        assertEquals(SoundIds.MUSIC_NAMES, BossBgms.musicEventPaths(),
                "SoundIds.MUSIC_NAMES 必须与目录逐项一致（顺序也算）");
        assertEquals(new LinkedHashSet<>(SoundIds.MUSIC_NAMES).size(),
                SoundIds.MUSIC_NAMES.size(), "音乐事件 id 不得重复");
    }

    @Test
    void everyRosterBossResolvesToARegisteredEvent() {
        List<String> paths = BossBgms.musicEventPaths();
        for (BossBgms.BossEntry entry : BossBgms.roster()) {
            assertTrue(paths.contains(entry.eventPath()),
                    "名册事件未进入音乐清单: " + entry.eventPath());
            assertNotNull(BossBgms.trackForEvent(entry.eventId()),
                    "名册事件没有对应曲目: " + entry.eventId());
            assertEquals(BgmTrack.forTier(entry.tier()),
                    BossBgms.trackForEvent(entry.eventId()),
                    "名册事件的曲目必须等于该 BOSS 的阶级曲目");
            assertTrue(entry.tier() >= 1 && entry.tier() <= 5);
        }
    }

    @Test
    void rosterEntriesAreDistinctAndExclusionsCarryReasons() {
        assertEquals(BossBgms.roster().size(), BossBgms.rosterEntityIds().size(),
                "名册里实体 id 不得重复");
        for (BossBgms.Excluded excluded : BossBgms.excludedEntries()) {
            assertTrue(excluded.reason() != null && !excluded.reason().isBlank(),
                    "豁免条目必须写明理由: " + excluded.className());
        }
    }

    @Test
    void unknownBossFallsBackToItsTierEvent() {
        var stranger = com.bitsson.gensokyou.Gensokyou.id("some_future_boss");
        assertEquals(BgmTrack.TIER3.eventId(), BossBgms.eventFor(stranger, 3),
                "名册外 BOSS 必须回落到阶级事件（否则资源包无法整阶替换）");
        assertNull(BossBgms.trackForEvent(com.bitsson.gensokyou.Gensokyou.id("bgm/nope")));
    }

    @Test
    void testBossesAreExcluded() {
        assertTrue(BossBgms.excluded(com.bitsson.gensokyou.Gensokyou.id("blood_bar_probe_t3")));
        assertTrue(BossBgms.excluded(com.bitsson.gensokyou.Gensokyou.id("blood_bar_probe_t4")));
        assertTrue(BossBgms.excluded(com.bitsson.gensokyou.Gensokyou.id("balance_test_boss")));
        assertTrue(!BossBgms.excluded(com.bitsson.gensokyou.Gensokyou.id("flandre")));
    }
}
