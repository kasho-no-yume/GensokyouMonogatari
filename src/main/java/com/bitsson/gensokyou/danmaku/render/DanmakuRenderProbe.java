package com.bitsson.gensokyou.danmaku.render;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 弹幕渲染剖针。
 *
 * <p>三套观测：
 * <ol>
 *  <li>Minecraft Profiler 切片：{@code gensokyou_danmaku_body|glow|core}，
 *      进入 F3+P 饼图</li>
 *  <li>按层开关（{@code /danmaku layers}）：关掉辉光/亮核后即可判断 GPU 填率是否主因</li>
 *  <li>每帧计数：各层 draw 数、顶点数、getBuffer 数，并入 {@code /gs_boss danmaku}</li>
 * </ol>
 *
 * <p>计数每 `RenderLevelStageEvent.Stage.AFTER_PARTICLES` 快照为"上一帧"并清零——
 * 弹幕实体渲染发生在其前，故该点的计数正好对应这一帧。
 *
 * <p><b>读数口径</b>：各计数是<b>实际提交量</b>，不是"设计层数"。
 * 球弹（四边形几何）恒为<b>单层</b>——本体与外辉光由同一张贴图承载，
 * 不再另画外发光 / 亮核层——故球弹的 {@code glow} / {@code core} 计数<b>恒为 0</b>，
 * 这是<b>正确读数</b>，不是"被密度降级"。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class DanmakuRenderProbe {

    private DanmakuRenderProbe() {
    }

    public static boolean bodyEnabled = true;
    public static boolean glowEnabled = true;
    public static boolean coreEnabled = true;

    /**
     * 球弹恒为单通绘制（本体与外辉光在同一张贴图里），故不存在密度降级。
     *
     * <p>本常量保留为「单通是设计」的事实来源；{@code effectiveGlow()} /
     * {@code effectiveCore()} 读的是<b>显式调试开关</b>，球弹路径恒不调用它们。
     */
    public static final boolean ALWAYS_LOD = true;

    /** 屏幕载弹密度达到阈值？保留为调试用；effective* 闸使用 ALWAYS_LOD。 */
    public static volatile boolean dense = true;
    /** 密度阈值（渲染实体数）。 */
    public static int denseThreshold = 400;
    private static int denseWarmup = 0;
    private static int denseCool = 0;

    public static int bodyDraws;
    public static int glowDraws;
    public static int coreDraws;
    public static long vertexCount;
    public static int bufferCalls;

    public static int lastBodyDraws;
    public static int lastGlowDraws;
    public static int lastCoreDraws;
    public static long lastVertexCount;
    public static int lastBufferCalls;

    /** 弹幕顶点计数（每次调用一次）。 */
    public static void countVertex() {
        vertexCount++;
    }

    public static void countBuffer() {
        bufferCalls++;
    }

    public static void countBody() {
        bodyDraws++;
    }

    public static void countGlow() {
        glowDraws++;
    }

    public static void countCore() {
        coreDraws++;
    }

    public static void pushBody() {
        Minecraft.getInstance().getProfiler().push("gensokyou_danmaku_body");
    }

    public static void pushGlow() {
        Minecraft.getInstance().getProfiler().push("gensokyou_danmaku_glow");
    }

    public static void pushCore() {
        Minecraft.getInstance().getProfiler().push("gensokyou_danmaku_core");
    }

    public static void pop() {
        Minecraft.getInstance().getProfiler().pop();
    }

    public static void enableBody(boolean v) {
        bodyEnabled = v;
    }

    public static void enableGlow(boolean v) {
        glowEnabled = v;
    }

    public static void enableCore(boolean v) {
        coreEnabled = v;
    }

    /** AFTER_PARTICLES：这一帧已走过实体渲染，计数落档+清零。 */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        lastBodyDraws = bodyDraws;
        lastGlowDraws = glowDraws;
        lastCoreDraws = coreDraws;
        lastVertexCount = vertexCount;
        lastBufferCalls = bufferCalls;
        bodyDraws = 0;
        glowDraws = 0;
        coreDraws = 0;
        vertexCount = 0;
        bufferCalls = 0;
        updateDense();
    }

    /** 进阈值后进入 dense，冷点低于后退 — 加滞回，避免卡边缘频闪。 */
    private static void updateDense() {
        // 球弹恒单通：probe 行里的 dense 字段跟着 ALWAYS_LOD 走，不做冷暖切换。
        dense = ALWAYS_LOD;
    }

    /**
     * 是否绘制外发光附加层。
     *
     * <p><b>球弹路径不读本闸</b>——它的辉光在贴图里，MUST NOT 另加一层（填率会翻倍）。
     * 本闸只对模型类几何（分层实现）有意义。
     */
    public static boolean effectiveGlow() {
        return glowEnabled && !dense;
    }

    /** 同 {@link #effectiveGlow()}：球弹路径不读本闸。 */
    public static boolean effectiveCore() {
        return coreEnabled && !dense;
    }

    public static boolean effectiveBody() {
        return bodyEnabled;
    }

    /**
     * 一行摘要，挂进 {@code /gs_boss danmaku}。
     *
     * <p>{@code quads} 是本帧实际提交的 quad 总数（三层之和），即"真实绘制量"。
     * 球弹恒为单层，故 {@code quads == body}。
     */
    public static String summary() {
        return String.format(
                "renderProbe[body=%d glow=%d core=%d quads=%d verts=%d getBuffer=%d layerMask=body:%s,glow:%s,core:%s dense:%s]",
                lastBodyDraws, lastGlowDraws, lastCoreDraws,
                lastBodyDraws + lastGlowDraws + lastCoreDraws,
                lastVertexCount, lastBufferCalls,
                bodyEnabled, glowEnabled, coreEnabled, dense);
    }
}
