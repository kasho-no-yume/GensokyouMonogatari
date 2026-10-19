package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.author.CardProgram;
import java.util.*;

/**
 * 正式迁移内容的注册表（change {@code migrate-boss-cards-to-programs} 第 1 节）。
 *
 * <p>一张 {@link Card} = 一张正式符卡的程序版本：下标、名称 lang 键、起始血量占比、
 * 循环长度、编译产物与差异登记。{@link Deck} 是一个 BOSS 的整套内容（含可选独立底噪）。
 *
 * <p><b>下标与语言键刻意与旧 {@code BossCards} 完全一致</b>（1 起、同一
 * {@code spellcard.gensokyou.<owner>.<i>}）。HUD 只传下标、客户端本地反查名字，
 * 只要下标→键一致，迁移前后玩家看到的符卡名就不变；这是「不新增/不改语言资源」的前提。
 */
public final class BossProgramCards {
    private BossProgramCards() {}

    /**
     * 一张正式符卡。
     *
     * @param index       1 起的下标，与旧 {@code BossCards} 表一致
     * @param nameKey     lang 键，与旧表一致
     * @param hpFraction  起始血量占比；选择规则 = 「起始门槛已达成的最后一张」
     * @param cycleTicks  循环长度（tick）；换卡挂起到该边界
     * @param densityWaiver 旧表声明的密度豁免（仅记录用途，供 manifest 对照）
     * @param program     编译产物，可重复安装
     * @param notes       与旧实现的差异 / 未表达能力登记
     */
    public record Card(int index, String nameKey, double hpFraction, int cycleTicks,
                       double densityWaiver, CardProgram.Compiled program, List<String> notes) {
        public Card {
            if (index < 1 || nameKey == null || nameKey.isBlank()) throw new IllegalArgumentException("card index/nameKey required");
            if (!Double.isFinite(hpFraction) || hpFraction <= 0 || hpFraction > 1) throw new IllegalArgumentException("hpFraction in (0,1]");
            if (cycleTicks < 1) throw new IllegalArgumentException("cycleTicks required");
            Objects.requireNonNull(program);
            notes = List.copyOf(notes);
        }
    }

    /**
     * 一个 BOSS 的正式程序内容。
     *
     * @param ownerId    实体注册名，与 {@code BossCards} / lang 的 {@code entity.gensokyou.*} 同源
     * @param background 独立底噪程序；无底噪（如蓝）为 null
     * @param cards      按起始占比从高到低排列
     */
    public record Deck(String ownerId, CardProgram.Compiled background, List<Card> cards, List<String> notes) {
        public Deck {
            if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId required");
            cards = List.copyOf(cards);
            notes = List.copyOf(notes);
            if (cards.isEmpty()) throw new IllegalArgumentException("deck needs cards");
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).index() != i + 1) throw new IllegalArgumentException("card indices must be 1..n in order");
                if (cards.get(i).hpFraction() > (i == 0 ? 1.0001D : cards.get(i - 1).hpFraction()))
                    throw new IllegalArgumentException("hpFractions must be non-increasing");
            }
        }

        public Card card(int index) { return cards.get(index - 1); }

        /**
         * 「起始门槛已达成的最后一张」——与 {@code TrackRunner#cardFor} 同一条规则，
         * 保证迁移前后阶段边界无歧义（血量恰好等于门槛时算已进入该卡）。
         */
        public int select(double hpFraction) {
            int next = 1;
            for (Card card : cards) {
                if (hpFraction <= card.hpFraction() + 1.0E-6D) next = card.index();
                else break;
            }
            return next;
        }
    }

    // Cache immutable decks for HUD lookup without turning content errors into class-init errors.
    private static final Map<String,Deck> DECKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static Deck bigFairyDeck() { return DECKS.computeIfAbsent("big_fairy", id -> BigFairyPrograms.deck()); }
    private static Deck yamameDeck() { return DECKS.computeIfAbsent("yamame", id -> YamamePrograms.deck()); }
    private static Deck yakumoRanDeck() { return DECKS.computeIfAbsent("yakumo_ran", id -> YakumoRanPrograms.deck()); }

    /** 已正式迁移的 BOSS ownerId（不含被排除者）。 */
    public static List<String> migratedOwners() {
        return List.of("big_fairy", "yamame", "yakumo_ran");
    }

    /**
     * 明确排除迁移的 ownerId：露米娅/傩面。
     *
     * <p>tasks 1.5 由用户明确画线——露米娅不是正式符卡，后续整卡重做，迁移无意义。
     * 这里显式登记，避免「20 卡表」被自动补全成含傩面的 20 卡。
     */
    public static List<String> excludedOwners() {
        return List.of("nomen_mask");
    }

    /** 取某 BOSS 的正式内容；未迁移返回空。 */
    public static Optional<Deck> forOwner(String ownerId) {
        return switch (ownerId) {
            case "big_fairy" -> Optional.of(bigFairyDeck());
            case "yamame" -> Optional.of(yamameDeck());
            case "yakumo_ran" -> Optional.of(yakumoRanDeck());
            default -> Optional.empty();
        };
    }

    /** 全部正式内容（按迁移顺序）。构建失败会抛出，供测试与加载期自检暴露问题。 */
    public static List<Deck> all() {
        return List.of(bigFairyDeck(), yamameDeck(), yakumoRanDeck());
    }

    // --- 供内容模块共用的小工具 -------------------------------------------------

    /** 旧签名色盘的整数值 → {@code #RRGGBB}，避免手工抄色导致的长期漂移。 */
    public static String hex(int rgb) { return String.format("#%06X", 0xFFFFFF & rgb); }
}
