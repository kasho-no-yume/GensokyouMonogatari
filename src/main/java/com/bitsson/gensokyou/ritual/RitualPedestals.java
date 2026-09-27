package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 结构内祭品台枚举：pattern 的台面 key 不保证统一（加具土命 P，八方归元 a/b/c/d），
 * 以 palette 绑定 #gensokyou:ritual_pedestals 的全部 key 反查为准。
 */
public final class RitualPedestals {

    private static final ResourceLocation PEDESTAL_TAG = Gensokyou.id("ritual_pedestals");

    private RitualPedestals() {
    }

    /** 全部台位（规范序 y,z,x；跨 key 天然互斥，无重复）。 */
    public static List<BlockPos> positions(RitualMatch match) {
        return RitualPatternLoader.byId(match.patternId())
                .map(pattern -> collect(pattern, match))
                .orElse(List.of());
    }

    /**
     * 某 pattern 某阶级的祭品台<b>相对锚点</b>偏移（规范序 y,z,x）。
     *
     * <p><b>客户端本地推导专用</b>：客户端拿不到 {@link RitualMatch}（结构匹配是服务端行为，
     * {@code activeMatch} 只在服务端赋值），但仪式 pattern JSON 是随
     * {@code RitualDataSyncPayload} 全量下发的，且 {@link RitualPatternLoader} 的四重展开与
     * {@code (y,z,x)} 规范排序都在<b>加载期</b>完成——因此「某 pattern 某阶有哪些台位、在哪」
     * 是 {@code (patternId, tier)} 的纯函数，客户端零包可得。
     *
     * <p>与 {@link #positions(RitualMatch)} 共用同一份筛选与排序代码，故两侧的台位集合与
     * 顺序<b>结构上</b>一致——客户端按索引取台位不会与服务端错位。
     * {@link RitualMatcher#orient} 只做置换与符号翻转，而 pattern 已四重对称展开，
     * 故偏移集合与仪式朝向无关。
     *
     * <p>返回空列表表示：该阶不存在该 pattern、或该阶一台都没有。调用方 MUST 把空列表当作
     * 「不绘制」而非「绘制 0 条的错误几何」。
     */
    public static List<BlockPos> offsets(RitualPattern pattern, int tier) {
        List<BlockPos> out = new ArrayList<>();
        for (var entry : pattern.palette().entrySet()) {
            if (!isPedestal(entry.getValue())) {
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
        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
        return out;
    }

    /** 该调色板谓词是否绑定祭品台标签。 */
    public static boolean isPedestal(RitualPattern.Predicate predicate) {
        return predicate.kind() == RitualPattern.Kind.TAG
                && predicate.tag() != null
                && predicate.tag().location().equals(PEDESTAL_TAG);
    }

    private static List<BlockPos> collect(RitualPattern pattern, RitualMatch match) {
        List<BlockPos> out = new ArrayList<>();
        for (var entry : pattern.palette().entrySet()) {
            if (isPedestal(entry.getValue())) {
                out.addAll(match.positionsOf(entry.getKey()));
            }
        }
        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
        return out;
    }
}

