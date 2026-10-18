package com.bitsson.gensokyou.danmaku.program.content;

import java.util.*;

/**
 * 正式迁移 manifest 的代码权威（tasks 1.7）。
 *
 * <p>人工文档 {@code openspec/changes/migrate-boss-cards-to-programs/migration-manifest.md}
 * 与这里的条目一一对应；测试导出同一份数据，避免「表格写了但代码没有」。
 *
 * <p>{@code legacyObserved}/{@code newObserved} 只登记可离线复现的声明差异；地形、生存模式
 * 命中与多人可读性属于实机验收，本 manifest 明确标为未执行。
 */
public final class BossProgramManifest {
    private BossProgramManifest() {}

    /**
     * @param owner       BOSS 注册名
     * @param index       1 起符卡下标；底噪为 0
     * @param nameKey     lang 键；底噪为空串
     * @param hpFraction  起始血量占比；底噪为 1.0
     * @param cycleTicks  循环长度
     * @param programId   程序定义 id
     * @param background  是否为独立底噪
     * @param notes       与旧实现的差异 / 未表达能力 / 未执行项
     */
    public record Entry(String owner, int index, String nameKey, double hpFraction, int cycleTicks,
                        String programId, boolean background, List<String> notes) {
        public Entry { notes = List.copyOf(notes); }
    }

    /** 全部正式条目：三个 BOSS 的底噪（如有）+ 15 张符卡。 */
    public static List<Entry> entries() {
        List<Entry> rows = new ArrayList<>();
        for (var deck : BossProgramCards.all()) {
            if (deck.background() != null) {
                rows.add(new Entry(deck.ownerId(), 0, "", 1.0D, 0,
                        deck.background().definition().id(), true, deck.notes()));
            }
            for (var card : deck.cards()) {
                rows.add(new Entry(deck.ownerId(), card.index(), card.nameKey(), card.hpFraction(),
                        card.cycleTicks(), card.program().definition().id(), false, card.notes()));
            }
        }
        return List.copyOf(rows);
    }

    /** 正式迁移的 15 张卡（不含底噪）。 */
    public static long migratedCardCount() {
        return entries().stream().filter(e -> !e.background()).count();
    }

    /** 被排除的旧卡数：露米娅/傩面五卡整体不迁移。 */
    public static int excludedLegacyCardCount() {
        return 5;
    }

    /**
     * 旧 20 卡表与实现/内容映射之间的冲突，回写为迁移基线。
     * 每项 = {范围, 旧声明, 迁移基线, 依据}。
     */
    public static List<String[]> sheetConflicts() {
        return List.of(
                new String[]{"yakumo_ran.8", "表格共通行：终符 .70 大玉", ".40 普通弹、无大玉",
                        "content-mapping §4 补充：按卡 8 详细构成（.40）为权威迁移值"},
                new String[]{"big_fairy.3", "旧轨道：雨 60 格高处、首空气面", "40 格盒内下落、无世界采样",
                        "作者入口未暴露 FIRST_AIR_BELOW/GROUND_BELOW；登记为差异，不伪称等价"},
                new String[]{"yakumo_ran.4", "旧要求激活前走廊方块净空校验", "只保证几何物理断口 2.64",
                        "作者入口未暴露 corridor；运行期仍由方块碰撞裁剪光束"},
                new String[]{"yamame.1/3", "旧外层调度只放周期 0 相位", "周期内显式多拍（10/13 拍）",
                        "content-mapping §3 要求修复子拍；密度相较旧实现上升"},
                new String[]{"nomen_mask.1-5", "旧表 20 卡含傩面五卡", "不迁移",
                        "tasks 1.5：露米娅非正式符卡，后续整卡重做"});
    }

    /** 明确未执行的验收项（不得伪造记录）。 */
    public static List<String> unexecutedVerifications() {
        return List.of(
                "实机地形：雨/光柱、激光断口在真实方块地形中的表现（未执行）",
                "生存模式命中与伤害吞没（创造/和平/吸收）实机确认（未执行）",
                "多人（2/5 人）可读性与帧率实机矩阵（未执行；自动化只覆盖权威数量与网络投影）",
                "远程客户端高延迟/断网下的观感（未执行）");
    }
}
