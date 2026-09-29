package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 忘川灯坛结构内蜡烛位表（pattern 中谓词恰为 {@code minecraft:candle} 的 palette key）。
 *
 * <p>与 {@link RitualPedestals} 同构：服务端 {@link #positions} 吃 {@link RitualMatch}（世界坐标），
 * 客户端 {@link #offsets} 吃已同步的 pattern 切片（相对偏移），二者<b>共用同一段谓词筛选与同一个
 * {@code (y, z, x)} 规范排序</b>，故两侧的位表<b>逐项同序</b>。
 *
 * <p><b>跨端位序是关键不变量。</b>忘川把"哪些蜡烛点亮"压进 {@code RitualRenderState.movingMask}
 * 的一个 {@code long}（第 i 位 = 规范序第 i 根点亮），服务端用它决定改写哪根方块、客户端用它决定
 * 高亮哪根。两侧只要有一处改了排序键、或筛选口径不一致，位掩码就会指向<b>错误的蜡烛</b>——
 * 现象是"服务端熄灭的那根"与"客户端标红的那根"不是同一根，且极难定位。因此：
 * <ul>
 *   <li>排序键 MUST 保持 {@code (y, z, x)}（与 {@link RitualPedestals} 一致，也是 pattern 展开序）；</li>
 *   <li>服务端按绝对坐标排、客户端按相对偏移排，但锚点是同一常量，排序结果因此逐项相同；</li>
 *   <li>pattern 已四重对称展开，{@code RitualMatcher#orient} 只做置换与符号翻转，故位序与仪式朝向无关。</li>
 * </ul>
 *
 * <p>蜡烛总数 16 / 32 / 64，恰为 1 / 2 / 3 个位掩码所需宽度（见 {@code BousenBehavior.fullMask}）。
 */
public final class BousenLanterns {

    private BousenLanterns() {
    }

    /** 谓词恰为 {@code minecraft:candle} 的 palette key。 */
    public static boolean isCandle(RitualPattern.Predicate predicate) {
        return predicate.kind() == RitualPattern.Kind.EXACT && predicate.block() == Blocks.CANDLE;
    }

    /** 全部蜡烛世界坐标（规范序 {@code y, z, x}；跨 key 天然互斥，无重复）。 */
    public static List<BlockPos> positions(RitualMatch match) {
        return RitualPatternLoader.byId(match.patternId())
                .map(pattern -> collect(match, pattern))
                .orElse(List.of());
    }

    /**
     * 某 pattern 某等级的蜡烛<b>相对锚点</b>偏移（规范序 {@code y, z, x}）。
     *
     * <p><b>客户端本地推导专用</b>：结构匹配是服务端行为，客户端拿不到 {@link RitualMatch}；
     * 但 pattern JSON 已随数据通道全量下发、且四重展开与规范排序都在加载期完成，故"某阶有哪些蜡烛、
     * 在哪"是 {@code (patternId, tier)} 的纯函数。与 {@link RitualPedestals#offsets} 共用同一份
     * 筛选与排序代码，故两侧位表与顺序<b>结构上</b>一致。
     *
     * <p>返回空列表表示该阶不存在该 pattern、或该阶一根都没有。调用方 MUST 把空列表当作
     * 「不绘制」而非「绘制 0 条的错误几何」。
     */
    public static List<BlockPos> offsets(RitualPattern pattern, int tier) {
        List<BlockPos> out = new ArrayList<>();
        for (var entry : pattern.palette().entrySet()) {
            if (!isCandle(entry.getValue())) {
                continue;
            }
            for (RitualPattern.LevelSlice slice : pattern.levels()) {
                if (slice.level() != tier) {
                    continue;
                }
                for (RitualPattern.BlockEntry block : slice.blocks()) {
                    if (block.key() == entry.getKey()) {
                        out.add(new BlockPos(block.x(), block.y(), block.z()));
                    }
                }
            }
        }
        sort(out);
        return out;
    }

    private static List<BlockPos> collect(RitualMatch match, RitualPattern pattern) {
        List<BlockPos> out = new ArrayList<>();
        for (var entry : pattern.palette().entrySet()) {
            if (isCandle(entry.getValue())) {
                out.addAll(match.positionsOf(entry.getKey()));
            }
        }
        sort(out);
        return out;
    }

    /** 规范序 = 层自下而上、z 自北向南、x 自西向东。两侧 MUST 共用此实现。 */
    private static void sort(List<BlockPos> out) {
        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
    }
}
