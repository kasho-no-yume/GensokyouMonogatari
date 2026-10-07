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
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class DanmakuRenderProbe {

    private DanmakuRenderProbe() {
    }

    public static boolean bodyEnabled = true;
    public static boolean glowEnabled = true;
    public static boolean coreEnabled = true;

    /** 当前版本永远用 LOD 贴图单通绘制，导向与全量 body draw 对齐。 */
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
        // 永久 LOD：probe 行里的 dense 字段跟着 ALWAYS_LOD 走，不做冷暖切换。
        dense = ALWAYS_LOD;
    }

    /** dense 时渲染器跳过 glow 和 core 效果层。 */
    public static boolean effectiveGlow() {
        return glowEnabled && !dense;
    }

    public static boolean effectiveCore() {
        return coreEnabled && !dense;
    }

    public static boolean effectiveBody() {
        return bodyEnabled;
    }

    /** 一行摘要，挂进 `/gs_boss danmaku`。 */
    public static String summary() {
        return String.format(
                "renderProbe[body=%d glow=%d core=%d verts=%d getBuffer=%d layerMask=body:%s,glow:%s,core:%s dense:%s]",
                lastBodyDraws, lastGlowDraws, lastCoreDraws, lastVertexCount, lastBufferCalls,
                bodyEnabled, glowEnabled, coreEnabled, dense);
    }
}
