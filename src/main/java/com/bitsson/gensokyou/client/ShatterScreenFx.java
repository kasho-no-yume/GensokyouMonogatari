package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.block.entity.SukimaBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 结界崩解的<b>屏幕级与镜头级</b>演出导演（纯客户端）。
 *
 * <p>世界内的演出（光球、光柱、烟环）在 {@code SukimaPortalRenderer} 里；
 * 这里负责那些<b>作用于玩家视听</b>的部分：屏幕蓝黑渐晕、FOV 冲击、镜头晃动。
 * 二者共用同一条绝对锚点，故<b>零新增网络包</b>——客户端从
 * {@code fxStartGameTime} 自算时间轴，屏幕效果只是这条时间轴的另一个消费者。
 *
 * <h2>强度按距离衰减，对所有玩家生效</h2>
 * 强度只取决于<b>玩家到最近一处正在崩解的门的距离</b>，与"是谁触发的"无关：
 * 你自己放的、队友放的、远远看见的，都按同一曲线衰减。
 * {@link #RANGE} 格外强度恒为 0。
 * <pre>
 *   f(d) = (1 − d / 100)^1.5      d = 到眼中心的距离（格）
 *   d=0 → 1.00    d=20 → 0.72    d=50 → 0.35    d=80 → 0.09    d≥100 → 0
 * </pre>
 *
 * <h2>数据来源：BER 逐帧上报 + 短暂保留</h2>
 * 门体只在<b>进入视锥</b>时才渲染，故由 {@code SukimaPortalRenderer} 每帧上报，
 * 这里保留 {@link #RETAIN_TICKS} tick。后果（已知取舍）：门在视野外时效果会
 * 在约 1 秒内淡出而不是立刻消失——这既省掉了"每帧遍历全世界方块实体"的代价，
 * 读感上也对（爆炸余波本就应该在转头后仍有余韵）。
 * 代价是<b>背对着门时不会震</b>，这是有意的。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ShatterScreenFx {

    private ShatterScreenFx() {
    }

    // ---------------------------------------------------------------- 参数

    /** 强度归零距离（格）。用户要求"100 格开外完全无效果"。 */
    public static final float RANGE = 100.0F;
    /**
     * 满强度平台半径（格）：<b>贴着门就是满效果</b>，不是"削弱版"。
     *
     * <p>没有平台的话，"离门越近越强"会退化成"离门越近越弱"——因为衰减曲线在
     * 近处本来就在 0.9 以上，1.0 和 0.97 的差别观众读不出来，只会觉得"怎么这么淡"。
     */
    public static final float PLATEAU = 6.0F;
    /** 衰减指数：越大则远处的效果掉得越快。1.6 兼顾"近处满效果"与"远处仍看得见"。 */
    private static final float FALLOFF_EXP = 1.6F;
    /** 上报后保留时长（tick）：覆盖"转身看一眼再转回来"。 */
    private static final double RETAIN_TICKS = 50.0D;
    /** 爆炸包络的衰减常数（tick）。 */
    private static final double BURST_DECAY = 7.0D;
    /** 爆炸包络的有效长度（tick）：超过就当作结束。 */
    private static final int BURST_LEN = 40;
    /**
     * 镜头 roll 幅度系数（度）。满强度时峰值 ≈ 0.78 × 20 ≈ <b>15.6°</b>。
     *
     * <p>标定依据：用户定的标准是"<b>可能让玩家感到眩晕</b>"，不是"明显但不晕"。
     * 参照系——把显示器固定住、左右转 15°，画面里所有水平线都会明显倾斜，
     * 加上 3~6 Hz 的往复，这就已经是会让人不适的量级了。
     * 上一版 7.2（峰值 5.6°）实测反馈"完全没感觉出在晃"。
     */
    private static final float ROLL_DEGREES = 20.0F;
    /** 镜头 pitch 幅度系数（度）。满强度时峰值 ≈ 0.78 × 14 ≈ 10.9°。 */
    private static final float PITCH_DEGREES = 14.0F;

    // ---------------------------------------------------------------- 上报

    /** 一次"某处正在崩解"的观测。 */
    private record Report(Vec3 eye, double gameTime, int chargeEnd, int elapsed) {
    }

    private static final List<Report> REPORTS = new ArrayList<>(4);
    private static final List<Double> STAMPS = new ArrayList<>(4);

    /**
     * 由 {@code SukimaPortalRenderer} 每帧调用，上报一处正在崩解的门。
     *
     * @param now  当前游戏时间（含小数 partial tick）
     */
    public static void report(SukimaBlockEntity portal, Vec3 eye, int elapsed, int chargeEnd, double now) {
        // 同位置重复上报时替换而不是堆积，否则转身一次能积出上百条
        for (int i = 0; i < REPORTS.size(); i++) {
            if (REPORTS.get(i).eye().equals(eye)) {
                REPORTS.set(i, new Report(eye, now, chargeEnd, elapsed));
                STAMPS.set(i, now);
                return;
            }
        }
        REPORTS.add(new Report(eye, now, chargeEnd, elapsed));
        STAMPS.add(now);
    }

    // ---------------------------------------------------------------- 强度

    /** 清掉过期上报。独立暴露是为了让测试能直接驱动它。 */
    static void prune(double now) {
        for (int i = REPORTS.size() - 1; i >= 0; i--) {
            if (now - STAMPS.get(i) > RETAIN_TICKS) {
                REPORTS.remove(i);
                STAMPS.remove(i);
            }
        }
    }

    /**
     * 距离衰减：{@link #PLATEAU} 以内恒为 1，之后线性降到 {@link #RANGE} 处为 0。
     *
     * <pre>
     *   f(d) = 1                              d ≤ 6 格（满效果）
     *   f(d) = (1 − (d−6)/94) ^ 1.6           d &gt; 6
     *   d=6 → 1.00   d=20 → 0.77   d=50 → 0.38   d=80 → 0.08   d≥100 → 0
     * </pre>
     */
    public static float falloff(double distance) {
        if (distance >= RANGE) {
            return 0.0F;
        }
        if (distance <= PLATEAU) {
            return 1.0F;
        }
        float t = (float) ((distance - PLATEAU) / (RANGE - PLATEAU));
        return (float) Math.pow(1.0D - t, FALLOFF_EXP);
    }

    /**
     * 蓄能段强度 0..1（已乘距离衰减）。
     *
     * <p>用 {@code p^1.2} 而不是 {@code p^1.8}：后者在蓄能中段（p=0.5）只有 0.29，
     * 再乘上"爆得开但视觉冲击弱"的距离衰减后，整段蓄能期实际只有标称的 1/4 强度
     * —— 实机读作"完全没感觉"。1.2 让中段到 0.43，重量仍然压在最后一段。
     */
    public static float chargeIntensity(double distance, int elapsed, int chargeEnd) {
        if (elapsed < 0 || chargeEnd <= 0 || elapsed >= chargeEnd) {
            return 0.0F;
        }
        float p = (float) elapsed / chargeEnd;
        return falloff(distance) * (float) Math.pow(p, 1.2D);
    }

    /** 爆炸包络强度 0..1（已乘距离衰减）：爆炸当刻为 1，按 {@code e^(−s/7)} 衰减。 */
    public static float burstIntensity(double distance, int elapsed, int chargeEnd) {
        int since = elapsed - chargeEnd;
        if (since < 0 || since > BURST_LEN) {
            return 0.0F;
        }
        return falloff(distance) * (float) Math.exp(-since / BURST_DECAY);
    }

    /**
     * 扫描当前所有上报，取出本帧的三个强度。
     *
     * <p>多个门同时崩解时取<b>各自的最大值</b>而不是相加：两场仪式叠成两倍黑屏
     * 会直接看不清路，而"最猛的那一场"读感更对。
     *
     * @return {@code {charge, burst, distanceOfTheStrongest}}
     */
    public static float[] sample(double now) {
        prune(now);
        float charge = 0.0F;
        float burst = 0.0F;
        float chargeDist = Float.MAX_VALUE;
        float burstDist = Float.MAX_VALUE;
        Camera3 c = camera();
        if (c == null) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }
        for (Report r : REPORTS) {
            double d = c.x() - r.eye().x;
            double e = c.y() - r.eye().y;
            double f = c.z() - r.eye().z();
            double dist = Math.sqrt(d * d + e * e + f * f);
            float a = chargeIntensity(dist, r.elapsed(), r.chargeEnd());
            if (a > charge) {
                charge = a;
                chargeDist = (float) dist;
            }
            float b = burstIntensity(dist, r.elapsed(), r.chargeEnd());
            if (b > burst) {
                burst = b;
                burstDist = (float) dist;
            }
        }
        return new float[]{charge, burst, Math.min(chargeDist, burstDist)};
    }

    private record Camera3(float x, float y, float z) {
    }

    private static Camera3 camera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) {
            return null;
        }
        Vec3 p = mc.gameRenderer.getMainCamera().getPosition();
        return new Camera3((float) p.x, (float) p.y, (float) p.z);
    }

    // ---------------------------------------------------------------- 事件：屏幕蓝黑渐晕

    /** 屏幕染色层 id。 */
    private static final ResourceLocation SCREEN_LAYER =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "shatter_screen");

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // 排在所有原版层之上 ⇒ 血条/物品栏也会被染色，这正是"屏幕变了"的读感
        event.registerAboveAll(SCREEN_LAYER, ShatterScreenFx::drawScreenTint);
    }

    private static void drawScreenTint(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        // 走<b>平滑后</b>的包络：直接读 20Hz 的原始包络会让染色一格一格跳，
        // 和已经平滑的镜头旋转对不上（用户会读作"闪烁"而不是"屏幕变色"）。
        advance();
        float total = Math.max(envCharge, envBurst);
        if (total <= 0.002F) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        // 顶部与底部各压一道更重的渐晕，中间留出仪式区域。
        // 标定（满强度贴脸）：角落 ≈ 0.85 + 0.34，画面中央 ≈ 0.34。
        // 上一版是 0.32 / 0.16，且蓄能中段包络只有 0.28 ⇒ 实际顶部仅 9%、中央 4.5%，
        // 实机读作"完全没有压暗"（用户反馈）。这里按"看得见"重新标定。
        float vign = total * 0.85F;
        int edge = argb(vign, 0x08, 0x10, 0x28);
        int clear = argb(0.0F, 0x08, 0x10, 0x28);
        g.fillGradient(0, 0, w, (int) (h * 0.45F), edge, clear);
        g.fillGradient(0, (int) (h * 0.55F), w, h, clear, edge);
        // 平铺一层冷蓝，把整个画面往蓝里压
        g.fill(0, 0, w, h, argb(total * 0.34F, 0x18, 0x2A, 0x58));
    }

    private static int argb(float alpha, int r, int g, int b) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- 连续时钟

    /**
     * 连续真实时钟（秒）。<b>与游戏 tick 无关</b>。
     *
     * <p>为什么不用 {@code level.getGameTime()}：它是<b>整 tick</b>，20Hz。
     * 60fps 下同一 tick 内连续 3 帧取到同一个值、然后跳一下 ⇒ 晃动读作
     * <b>20 级台阶</b>而不是平滑运动（用户反馈："不是很平滑，是直接 set 的吗"）。
     * 同理它也不能带 partialTick 混用：上报用的是
     * {@code getGameTime() + partialTick}（含小数），包络与振荡器必须分家。
     *
     * <p><b>分工</b>：<b>包络</b>（什么时候在蓄能 / 什么时候爆炸）必须来自游戏时间，
     * 因为它要与仪式时间轴同步；<b>振荡</b>（怎么抖）必须来自连续时间，
     * 因为它只是视觉噪声，与任何游戏逻辑无关。
     */
    private static double realSeconds() {
        return System.nanoTime() * 1.0E-9D;
    }

    /** 上一次推进的时刻；同一帧内被多个事件调用时只推进一次。 */
    private static double lastAdvance = Double.NaN;
    /** 平滑后的蓄能包络（0..1）。 */
    private static float envCharge;
    /** 平滑后的爆炸包络（0..1）。 */
    private static float envBurst;
    /** 本帧的镜头晃动角（度），已含包络、未乘用户旋钮。 */
    private static float shakeRoll;
    private static float shakePitch;

    /** 起振时间常数（秒）：爆炸当刻必须<b>立刻</b>顶上去，不许爬坡。 */
    static final float TAU_ATTACK = 0.020F;
    /** 衰减时间常数（秒）：包络退去后要拖一会儿余韵，不许硬切。 */
    static final float TAU_RELEASE = 0.320F;

    /**
     * 每帧推进一次包络与振荡器。同帧内重复调用是廉价 no-op。
     *
     * <p>包络用<b>单极点平滑</b>而非直接赋值：爆炸当刻强度会从 0 瞬间跳到 1，
     * 直接赋值会让镜头角度在两帧之间从 0° 突跳到 15°，读作"卡了一下"而不是"被砸中"。
     */
    private static void advance() {
        double now = realSeconds();
        if (!Double.isNaN(lastAdvance) && now - lastAdvance < 1.0E-4D) {
            return;
        }
        double dt = Double.isNaN(lastAdvance) ? 0.0D : Math.min(0.1D, now - lastAdvance);
        lastAdvance = now;

        float[] s = envelope();
        envCharge = approach(envCharge, s[0], dt, 0.10F, TAU_RELEASE);
        envBurst = approach(envBurst, s[1], dt, TAU_ATTACK, 0.22F);
        float amp = Math.max(envCharge, envBurst);
        if (amp <= 0.002F) {
            shakeRoll = 0.0F;
            shakePitch = 0.0F;
            return;
        }
        // 三个八度的值噪声。选它而不是"几个正弦相加"：正弦和读作<b>摆动</b>（规律往复），
        // 值噪声读作<b>震动</b>（不规则）。且它是 t 的<b>纯函数</b> ⇒ 任意帧率下逐位一致，
        // 再用 smoothstep 插值 ⇒ C1 连续，没有折角。
        float o1 = valueNoise(now * 1.9D, 11L);
        float o2 = valueNoise(now * 4.7D, 23L);
        float o3 = valueNoise(now * 10.3D, 37L);
        shakeRoll = (o1 * 0.58F + o2 * 0.30F + o3 * 0.12F) * amp * ROLL_DEGREES;
        shakePitch = (o1 * 0.34F + o2 * 0.44F + o3 * 0.22F) * amp * PITCH_DEGREES;
    }

    /**
     * 单极点指数趋近，<b>与 dt 无关</b>（用 {@code e^{-dt/τ}} 而不是 {@code dt/τ}）。
     *
     * <p>后者在 30fps 与 144fps 下收敛速度差近 5 倍——那正是"换台机器手感就变了"。
     */
    private static float approach(float cur, float target, double dt, float tauUp, float tauDown) {
        float tau = target > cur ? tauUp : tauDown;
        float k = 1.0F - (float) Math.exp(-dt / tau);
        return cur + (target - cur) * k;
    }

    /**
     * 一维值噪声，返回 {@code [-1, 1]}，C1 连续。
     *
     * <p>整数格上取哈希值、格内用 {@code smoothstep} 插值。频率 f ⇒ 每秒约 f 个"抖动周期"，
     * 所以 {@code t * 1.9} 就是 ~1.9 Hz 的低频晃，{@code t * 10.3} 是 ~10 Hz 的高频颤。
     */
    static float valueNoise(double t, long seed) {
        double fl = Math.floor(t);
        long i = (long) fl;
        double f = t - fl;
        double u = f * f * (3.0D - 2.0D * f);
        float a = hash01(i, seed);
        float b = hash01(i + 1L, seed);
        return (a + (b - a) * (float) u) * 2.0F - 1.0F;
    }

    private static float hash01(long i, long seed) {
        long h = i * 0x9E3779B97F4A7C15L + seed * 0x165667B1L;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 40) / 16777216.0F;
    }

    /** 未平滑的包络（供测试与调试）。 */
    private static float[] envelope() {
        float now = gameTime();
        if (now < 0.0F) {
            return new float[]{0.0F, 0.0F};
        }
        float[] s = sample(now);
        return new float[]{s[0], s[1]};
    }

    // ---------------------------------------------------------------- 事件：FOV 冲击

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        advance();
        float charge = envCharge;
        float burst = envBurst;
        if (charge <= 0.002F && burst <= 0.002F) {
            return;
        }
        // FOV 是"modifier"不是绝对值：最终 FOV ≈ base × (1 + modifier)。
        // 真·镜头晃动上线后**刻意把 FOV 压小**：用户反馈"你的晃好像是前后拉伸 fov"——
        // 那时因为 mixin 没加载、只剩 FOV 在动。旋转一旦真的生效，FOV 只该做补充。
        float fov = event.getNewFovModifier();
        // 蓄能末段的预兆：缓慢外扩
        fov += 0.035F * charge;
        // 爆炸当刻：先猛冲出去再收回（70° 基准视野下约 +7°）
        fov += 0.100F * burst;
        // 高频颤：走与镜头晃动同一套<b>连续时钟 + 值噪声</b>，否则 FOV 这一路
        // 仍然是 20Hz 台阶，与平滑的旋转对不上，看起来又像"前后拉伸"。
        float amp = total(charge, burst);
        float tremor = valueNoise(realSeconds() * 8.9D, 53L);
        fov += 0.010F * amp * tremor;
        event.setNewFovModifier(fov);
    }

    private static float total(float charge, float burst) {
        return Math.max(charge, burst);
    }

    // ---------------------------------------------------------------- 镜头晃动（供 mixin 读取）

    /**
     * 本帧的镜头晃动角度（度），{@code {roll, pitch}}。
     *
     * <p><b>刻意不在此直接改矩阵</b>：改相机矩阵必须用 mixin，事件拿不到它。
     * 具体障碍（都查过源码，不是推测）：
     * <ul>
     *   <li>{@code RenderLevelStageEvent.AFTER_SKY} 传的 poseStack 是 <b>null</b>；</li>
     *   <li>{@code AFTER_ENTITIES} / {@code AFTER_BLOCK_ENTITIES} 都在<b>地形之后</b>
     *       （{@code LevelRenderer} 第 1052 / 1124 行），只能让世界<b>一半</b>晃动；</li>
     *   <li>改 {@code Camera} 对象也<b>来不及</b>：{@code GameRenderer.renderLevel} 在调用
     *       {@code LevelRenderer.renderLevel} <b>之前</b>就把 {@code camera.rotation()}
     *       的逆旋转烘进了 {@code frustumMatrix}。</li>
     * </ul>
     * 于是 {@code LevelRendererShakeMixin} 在 {@code renderLevel} 开头原地旋转
     * {@code frustumMatrix}——它是<b>纯旋转、无平移</b>（见 mixin 注释），右乘小角度
     * 等价于转相机，于是天空/地形/实体/粒子一起晃，而 HUD 与手持物不晃。
     */
    public static float[] shakeDegrees() {
        advance();
        // 用户手感调节旋钮：改配置即可，不用重编译（一次 12 秒）
        float tune = GensokyouConfig.FX_SHATTER_SHAKE_SCALE.get().floatValue();
        return new float[]{shakeRoll * tune, shakePitch * tune};
    }

    // ---------------------------------------------------------------- 工具

    /** 当前游戏时间（整 tick）；取不到时返回 -1 表示"别做事"。只用于<b>包络</b>。 */
    private static float gameTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return -1.0F;
        }
        return (float) (mc.level.getGameTime() % 100000L);
    }

    /** 仅供测试：清空全部上报与平滑状态。 */
    static void reset() {
        REPORTS.clear();
        STAMPS.clear();
        envCharge = 0.0F;
        envBurst = 0.0F;
        shakeRoll = 0.0F;
        shakePitch = 0.0F;
        lastAdvance = Double.NaN;
    }

    static int reportCount() {
        return REPORTS.size();
    }
}
