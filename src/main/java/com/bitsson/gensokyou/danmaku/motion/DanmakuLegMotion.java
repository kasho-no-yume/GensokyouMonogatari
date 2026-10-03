package com.bitsson.gensokyou.danmaku.motion;

import com.bitsson.gensokyou.network.DanmakuWire;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * 「分段变向」运动形态：一条弹在一生中按段表改变方向与速率。
 *
 * <pre>
 * 段 0: 时长 20 tick  速率 0.5   方向 dir(seed₀)     T₀ = 0
 * 段 1: 时长 20 tick  速率 0.0   方向 dir(seed₁)     T₁ = 20
 * 段 2: 时长 40 tick  速率 0.3   方向 dir(seed₂)     T₂ = 40
 * </pre>
 *
 * <h2>纪律：方向在构造期求值一次，每 tick 路径不读种子</h2>
 *
 * <p>本类<b>允许</b>使用 {@code sin}/{@code cos}，因为它们只在
 * {@link #fromSpec} 里被调用一次，结果存进 {@link #cachedDirections}。
 * <b>每 tick 路径（{@link #segmentAt} / {@link #speedAt} / {@link #directionAt}）
 * MUST NOT 读 {@link DanmakuRandomState}。</b>
 *
 * <p>这条纪律是本变更的核心，违反它的后果比它修复的问题更糟：
 * 若某端在每 tick 路径上多抽一次（或某个 early-return 分支在两端走向不同），
 * 从此<b>永久分叉</b>，而校准通道只比位置、位置分叉要累积到肉眼可见才超容差
 * —— 即静默分叉。这与 {@code danmaku-timeline-sync} 立下的
 * 「速率估计绝不能吸收真失步」是同一类错误的两个实例。
 *
 * <p>之所以不用「常量池扫描」来守这条纪律：本类合法地含 {@code sin}/{@code cos}，
 * 扫描会误报。真正的验证是<b>轨迹比对</b> —— 构造 → 跑 200 tick 记录轨迹 →
 * 改种子 → 重建 → 再跑 200 tick ⇒ 断言两条轨迹逐位相同
 * （见 {@code DanmakuLegMotionContractTest}）。
 *
 * <h2>方向是绝对世界方向，不是相对偏转</h2>
 *
 * <p>相对偏转（「相对刚才往左偏 30°」）依赖累积状态，于是整条轨迹不再是
 * {@code f(年龄, 段表, 种子)}，重载不可复算，且运动档位会掉进
 * {@code INCREMENTAL}（读档冻结 + 事件化）。绝对方向没有这个问题，
 * 代价是符卡作者要自己给世界朝向 —— 而这本来就该由内容决定。
 *
 * @see DanmakuRandomState 种子来源
 */
public final class DanmakuLegMotion {

    /** 段数定长 8 —— 与 accessor 预算一一对应。 */
    public static final int MAX_LEGS = 8;

    /**
     * 速率定标：实际格/tick = 打包值 / 该常量。
     *
     * <p><b>直接引用 {@link DanmakuWire#VELOCITY_SCALE} 而非复制一份</b>：
     * 定标值一旦漂移，打包侧与解包侧用不同的常数会让速度差一个倍数，
     * 而症状是「弹看起来慢了一点」—— 极难归因。
     * 量化步长 1/4096 ⇒ 往返误差上界半个步长（约 1.2e-4 格/tick）。
     */
    public static final double VELOCITY_SCALE = DanmakuWire.VELOCITY_SCALE;

    /**
     * 段类型的来源。
     *
     * <p>类型是<b>整条运动</b>的属性而非逐段的：accessor 预算里没有逐段类型位，
     * 而逐段类型会让 18 个 accessor 变成 26 个。
     */
    public enum Kind {
        /** 方向恒为发射方向（段表写死方向）——「不变向」这一最简形态的基线。 */
        FIXED,
        /** 方向 = f(种子, 段号)，构造期解出。稳态零带宽。 */
        SEED,
        /**
         * 方向指向实体 —— 结构性盲区，只能由服务端在段起始年龄下发一次快照。
         *
         * <p>见 {@link #requiresServerDecisionAt} 与
         * {@link #applyAuthoritativeDirection}。
         */
        TARGET
    }

    private final Kind kind;
    private final int legCount;
    private final int[] packedLegs;
    /** 段起始年龄的前缀和，长度 {@code legCount}，构造期算好。 */
    private final int[] segmentStart;
    /** 构造期解出的每段方向。TARGET 段在收到快照前为 null。 */
    private final Vec3[] cachedDirections;

    private DanmakuLegMotion(Kind kind, int legCount, int[] packedLegs,
                              int[] segmentStart, Vec3[] cachedDirections) {
        this.kind = kind;
        this.legCount = legCount;
        this.packedLegs = packedLegs;
        this.segmentStart = segmentStart;
        this.cachedDirections = cachedDirections;
    }

    /**
     * 构造一条段式运动。
     *
     * <p><b>本方法是唯一读取种子的地方</b>，也是唯一允许调用 {@code sin}/{@code cos}
     * 的地方（对 {@link Kind#SEED}）。构造返回后，种子不再被任何路径读取。
     *
     * @param legCount   段数，夹到 {@code [1, MAX_LEGS]}；1 段即恒速直线这一最简形态
     * @param packedLegs 每段一个打包值 {@code (时长 << 16) | (速率 & 0xFFFF)}
     * @param random     种子来源
     * @param launchDir  发射方向，仅 {@link Kind#FIXED} 使用
     * @param kind       段类型
     */
    public static DanmakuLegMotion fromSpec(int legCount, int[] packedLegs,
                                            DanmakuRandomState random,
                                            Vec3 launchDir, Kind kind) {
        int count = Math.max(1, Math.min(MAX_LEGS, legCount));
        int[] packed = new int[MAX_LEGS];
        if (packedLegs != null) {
            System.arraycopy(packedLegs, 0, packed, 0,
                    Math.min(count, Math.min(MAX_LEGS, packedLegs.length)));
        }

        int[] starts = new int[count];
        int cursor = 0;
        for (int i = 0; i < count; i++) {
            starts[i] = cursor;
            cursor += durationOf(packed[i]);
        }

        Vec3[] directions = new Vec3[MAX_LEGS];
        DanmakuRandomState seeds = random == null ? DanmakuRandomState.empty() : random;
        for (int i = 0; i < count; i++) {
            directions[i] = switch (kind) {
                case FIXED -> launchDir == null ? new Vec3(0.0D, 0.0D, 1.0D) : launchDir;
                // 只有这里读种子，且只在构造期
                case SEED -> directionFromSeed(seeds.at(i), launchDir);
                // TARGET 段的方向依赖发射之后才发生的事实，构造期无从得知。
                // 留 null，由 applyAuthoritativeDirection 在段起始年龄装入。
                case TARGET -> null;
            };
        }
        return new DanmakuLegMotion(kind, count, packed, starts, directions);
    }

    /**
     * 段数 1 的最简形态：恒速直线。
     *
     * <p>刻意提供这个便捷入口：它让「段式运动退化为直线」成为一行可断言的事实，
     * 而不必在测试里凑一个只有一段的段表。
     */
    public static DanmakuLegMotion straight(double speed, int durationTicks, Vec3 launchDir) {
        return fromSpec(1, new int[]{pack(durationTicks, speed)}, DanmakuRandomState.empty(),
                launchDir, Kind.FIXED);
    }

    /** 打包单段参数：{@code (时长 << 16) | (速率 & 0xFFFF)}。速率按 {@link #VELOCITY_SCALE} 定标。 */
    public static int pack(int durationTicks, double speed) {
        int duration = Math.max(0, Math.min(0xFFFF, durationTicks));
        int scaled = (int) Math.max(0.0D, Math.min(0xFFFFD, Math.round(speed * VELOCITY_SCALE)));
        return (duration << 16) | (scaled & 0xFFFF);
    }

    /** 从种子与发射方向解出一个绝对世界方向（均匀球面分布）。 */
    private static Vec3 directionFromSeed(int seed, @Nullable Vec3 launchDir) {
        // 均匀球面：两个独立均匀量 → 余弦的均匀分布。
        // 用法向量的哈希避免 int 的高位/低位相关性。
        long mixed = seed * 0x9E3779B97F4A7C15L + 0x165667B19E3779F9L;
        mixed ^= (mixed >>> 33);
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= (mixed >>> 33);

        double cosTheta = ((mixed >>> 11) & 0xFFFFF) / (double) 0x100000 - 1.0D;
        double sinTheta = Math.sqrt(Math.max(0.0D, 1.0D - cosTheta * cosTheta));
        double phi = ((mixed >>> 32) & 0xFFFFF) / (double) 0x100000 * Math.PI * 2.0D;

        Vec3 sampled = new Vec3(sinTheta * Math.cos(phi), cosTheta, sinTheta * Math.sin(phi));
        if (launchDir == null) {
            return sampled;
        }
        // 以发射方向为极轴展开，使相邻段的转向「相对发射方向」看起来自然，
        // 同时仍然是<b>绝对</b>方向（不依赖任何累积状态）。
        Vec3 axis = launchDir.normalize();
        Vec3 helper = Math.abs(axis.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = helper.cross(axis).normalize();
        Vec3 up = axis.cross(right);
        return right.scale(sampled.x).add(axis.scale(sampled.y)).add(up.scale(sampled.z)).normalize();
    }

    private static int durationOf(int packed) {
        return (packed >>> 16) & 0xFFFF;
    }

    /**
     * 年龄落在哪一段 —— 年龄的纯函数，构造后不变。
     *
     * <p>越界一律夹紧：{@code age < 0} 归第 0 段，超出总时长归最后一段 ——
     * 段运动没有「寿命到了就没方向」的语义，终止由寿命判据负责。
     */
    public int segmentAt(int age) {
        if (age <= 0) {
            return 0;
        }
        for (int i = legCount - 1; i >= 0; i--) {
            if (age >= segmentStart[i]) {
                return i;
            }
        }
        return 0;
    }

    /** 该段的速率（格/tick），年龄的纯函数。 */
    public double speedAt(int age) {
        return (packedLegs[segmentAt(age)] & 0xFFFF) / VELOCITY_SCALE;
    }

    /** 该段的绝对世界方向。年龄的纯函数；只读构造期缓存，MUST NOT 读种子。 */
    public Vec3 directionAt(int age) {
        Vec3 direction = cachedDirections[segmentAt(age)];
        // TARGET 段尚未收到快照时保持上一段方向（双端一致的降级路径），
        // 而不是自行求解 —— 见 requiresServerDecisionAt。
        return direction != null ? direction : lastKnownDirection(segmentAt(age));
    }

    private Vec3 lastKnownDirection(int segment) {
        for (int i = segment; i >= 0; i--) {
            if (cachedDirections[i] != null) {
                return cachedDirections[i];
            }
        }
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    /** 段 {@code index} 的时长（tick）。 */
    public int durationAt(int index) {
        if (index < 0 || index >= legCount) {
            return 0;
        }
        return durationOf(packedLegs[index]);
    }

    /** 段 {@code index} 的起始年龄。 */
    public int segmentStartAt(int index) {
        if (index < 0 || index >= legCount) {
            return 0;
        }
        return segmentStart[index];
    }

    /** 段 {@code index} 的打包参数；越界返回 0。供实体侧回写同步数据用。 */
    public int packedLegAt(int index) {
        if (index < 0 || index >= legCount) {
            return 0;
        }
        return packedLegs[index];
    }

    /** 段数，范围 {@code [1, MAX_LEGS]}。 */
    public int legCount() {
        return legCount;
    }

    /** 段类型。 */
    public Kind kind() {
        return kind;
    }

    /** 全部段跑完的总时长（tick）。 */
    public int totalDuration() {
        int last = legCount - 1;
        return legCount == 0 ? 0 : segmentStart[last] + durationAt(last);
    }

    /**
     * 该年龄所在的段是否<b>还缺</b>服务端权威方向。
     *
     * <p>只有 {@link Kind#TARGET} 会返回 true，且只在快照尚未装入时为 true ——
     * 已收到过就不该重复请求，否则同一条环会退化成每拍一包。
     * 推送<b>时机</b>由 {@link #isSegmentStart} 决定，两者分工不同。
     */
    public boolean requiresServerDecisionAt(int age) {
        return kind == Kind.TARGET && age >= 0
                && cachedDirections[segmentAt(age)] == null;
    }

    /**
     * 判断某个年龄是否<b>正好落在</b>段起始处（推送时机用）。
     *
     * <p>与 {@link #requiresServerDecisionAt} 分开：前者问「这一段需不需要方向」，
     * 后者问「此刻是不是该推」。推送 MUST 只在段起始处发生，否则 48 拍的环
     * 会退化成每拍一包。
     */
    public boolean isSegmentStart(int age) {
        if (kind != Kind.TARGET || age < 0) {
            return false;
        }
        int segment = segmentAt(age);
        return segmentStart[segment] == age;
    }

    /**
     * 装入服务端下发的权威方向（{@link Kind#TARGET} 专用）。
     *
     * <p>客户端 MUST NOT 自行求解该方向；未收到时 {@link #directionAt} 沿用上一段方向，
     * 这是两端一致的降级路径。
     */
    public void applyAuthoritativeDirection(int segment, Vec3 direction) {
        if (kind == Kind.TARGET && segment >= 0 && segment < legCount && direction != null) {
            cachedDirections[segment] = direction.normalize();
        }
    }

    /**
     * 把「段数 + 段类型」打包进一个字节。
     *
     * <p>段数用低 4 位（{@code MAX_LEGS = 8} 用不满 4 位）、段类型用高 2 位 ——
     * 三种类型刚好塞得下。这样形态与段数共用<b>一个</b> accessor，
     * 保住 18 个 accessor 的预算（逐段类型会需要额外 8 个位）。
     */
    public static int packLegCountAndKind(int legCount, Kind kind) {
        int clamped = Math.max(0, Math.min(MAX_LEGS, legCount));
        int kindBits = Math.max(0, Math.min(3, kind.ordinal()));
        return (kindBits << 4) | clamped;
    }

    /** 解出打包字节里的段数。 */
    public static int legCountOf(int packed) {
        return Math.max(0, Math.min(MAX_LEGS, packed & 0xF));
    }

    /** 解出打包字节里的段类型；越界值退化为 {@link Kind#FIXED}。 */
    public static Kind kindOf(int packed) {
        int bits = (packed >>> 4) & 0x3;
        Kind[] values = Kind.values();
        return bits < values.length ? values[bits] : Kind.FIXED;
    }

    @Override
    public String toString() {
        return "DanmakuLegMotion[" + kind + ", legs=" + legCount
                + ", starts=" + Arrays.toString(Arrays.copyOf(segmentStart, legCount))
                + ", durations=" + durations() + "]";
    }

    private int[] durations() {
        int[] out = new int[legCount];
        for (int i = 0; i < legCount; i++) {
            out[i] = durationAt(i);
        }
        return out;
    }
}