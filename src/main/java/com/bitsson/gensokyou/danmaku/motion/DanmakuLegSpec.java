package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * 段式运动的<b>声明侧</b>数据 —— 一拍里那些弹的段表与种子。
 *
 * <p><b>为什么需要一个「声明」类型而不是直接用 {@link DanmakuLegMotion}</b>：
 * {@code DanmakuLegMotion} 在构造期就把段方向<b>解出并缓存</b>，而它解出时需要的
 * 极轴（发射方向）在<b>生成那一刻</b>才知道。符卡表是<b>纯声明</b>的，
 * 不该在构建期就依赖世界坐标 —— 所以表里放本类型，翻译层在发射时才
 * {@link #toMotion 构造}出 {@code DanmakuLegMotion}。
 *
 * <p><b>它是逐「拍」而非逐「发」</b>：同一拍发出的所有弹共用一份段表与种子。
 * 原因是「每批一个种子 + 批内第几枚」需要实例索引，而那属于
 * {@code danmaku-track-scope} 的门槛范围（见本变更的「显式不做」）。
 * 同一拍共用段表不损害形态：位置仍是各自累加，于是这些弹会
 * <b>各自从自己的出生点出发、沿同一套方向 schedule 走</b> —— 读作一片同步转向的弹幕。
 *
 * <p><b>段数是「至少 1」而非「至少 2」</b>：1 段的 {@code SEED} 形态就是
 * 「朝一个随机方向匀速直飞」，那是最常见的一档随机需求。
 */
public record DanmakuLegSpec(Kind kind, int[] packedLegs, int[] seeds, int seedCount) {

    /** 段类型。与 {@link DanmakuLegMotion.Kind} 同名同义，此处独立以便未来分化。 */
    public enum Kind {
        /** 方向恒为发射方向 —— 不变向。 */
        FIXED,
        /** 方向 = f(种子, 段号)，构造期解出。稳态零带宽。 */
        SEED,
        /** 方向指向实体 —— 结构性盲区，由服务端在段起始年龄下发一次快照。 */
        TARGET
    }

    /** 段数与类型打包成一个字节，供实体侧的单一 accessor 使用。 */
    public int packedLegCountAndKind() {
        return DanmakuLegMotion.packLegCountAndKind(packedLegs.length, toMotionKind());
    }

    /** 翻译成运行时形态。{@code launchDir} 是发射方向，作 {@code FIXED} 的极轴。 */
    public DanmakuLegMotion toMotion(@Nullable Vec3 launchDir) {
        return DanmakuLegMotion.fromSpec(packedLegs.length, packedLegs, randomState(),
                launchDir, toMotionKind());
    }

    public DanmakuRandomState randomState() {
        return DanmakuRandomState.of(seeds, seedCount);
    }

    private DanmakuLegMotion.Kind toMotionKind() {
        return switch (kind) {
            case FIXED -> DanmakuLegMotion.Kind.FIXED;
            case SEED -> DanmakuLegMotion.Kind.SEED;
            case TARGET -> DanmakuLegMotion.Kind.TARGET;
        };
    }

    /**
     * 便捷构造：一串「时长 + 速率」的段，种子给定。
     *
     * <p>这是符卡表里最常用的形态 —— 段数少、方向随机、时长与速率是内容决定的。
     */
    public static DanmakuLegSpec seeded(Kind kind, int seedCount, int[] seeds,
                                        double[] durations, double[] speeds) {
        int count = Math.min(Math.min(durations.length, speeds.length),
                DanmakuLegMotion.MAX_LEGS);
        int[] packed = new int[count];
        for (int i = 0; i < count; i++) {
            packed[i] = DanmakuLegMotion.pack((int) durations[i], speeds[i]);
        }
        return new DanmakuLegSpec(kind, packed,
                Arrays.copyOf(seeds, Math.min(seeds.length, DanmakuRandomState.MAX_SEEDS)),
                seedCount);
    }

    /** 单段匀速直线 —— 「只抽一次方向」的最小形态。 */
    public static DanmakuLegSpec straight(int durationTicks, double speed) {
        return seeded(Kind.FIXED, 0, new int[0],
                new double[]{durationTicks}, new double[]{speed});
    }
}