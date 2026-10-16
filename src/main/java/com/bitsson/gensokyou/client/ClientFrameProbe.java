package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 客户端帧时记录器（性能基线专用，默认关）。
 *
 * <p><b>为什么需要它。</b>服务端那半边的逐 tick 成本由 {@code netprof.NetProfile} 的
 * {@code tickcost} 行提供；客户端没有任何等价物——{@code /gs_boss danmaku} 的
 * {@code renderProbe} 与 {@code clock} 是 {@code Dist.CLIENT} 静态量，在专用服务端上恒为
 * 0 / WARMING（见 {@code BossDebugCommands} 的注释），而 F3 的瞬时 FPS 既不是样本也拿不到 p95。
 *
 * <p><b>开关。</b>只有系统属性 {@code gensokyou.frameProbe=true} 时才工作，对应 run config
 * {@code clientProfile}（任务 {@code runClientProfile}）。普通 {@code runClient} 不受影响。
 *
 * <p><b>为什么挂在 {@link RenderFrameEvent.Post} 而不是
 * {@code RenderLevelStageEvent.AFTER_PARTICLES}。</b>第一版挂在后者并按
 * {@code RenderLevelStageEvent#getRenderTick()} 去重，那是<b>错的</b>：
 * {@code LevelRenderer.ticks} 是在 {@code LevelRenderer.tick()}（客户端游戏 tick，20/s）
 * 里自增的，不是每帧自增。于是"同一 renderTick 只记一次"变成了"每个游戏 tick 只记一帧"，
 * 录出来的永远是 20 行/秒、间隔恒为 50 ms——**与真实帧率无关**，看起来还特别像"客户端被限速"。
 * {@link RenderFrameEvent.Post} 在 {@code Minecraft.runTick} 里紧接渲染之后触发一次，
 * 是每帧一次的确切钩子。
 *
 * <p><b>帧时取自游戏自己的读数</b>（{@code Minecraft#getFrameTimeNs()}）：它是
 * {@code Util.getNanos()} 在帧首尾之差，<b>包含</b>等待帧率上限/V_sync 的时间，
 * 也就是玩家实际感受到的"交付帧时"。注意该值在 {@code fireRenderFramePost} <b>之后</b>
 * 才赋值，所以本行写下的其实是<b>上一帧</b>的时长——逐帧序列因此整体错位一行，
 * 对中位数/p95 无影响，但读单行时要知道。
 *
 * <p><b>另记同帧弹幕绘制计数</b>：{@code DanmakuRenderProbe} 在 AFTER_PARTICLES 落档并清零，
 * 而 AFTER_PARTICLES 在本钩子之前，故 {@code last*} 正是这一帧的计数。读它们不额外做事，
 * 于是"这一帧贵是因为弹幕多"可以直接在同一张表里看出来。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientFrameProbe {

    /** 系统属性名。只有值为 {@code true} 时才记录。 */
    public static final String ENABLE_PROPERTY = "gensokyou.frameProbe";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "false"));

    /** 攒够 1 秒才 flush：逐帧 flush 本身会扰动帧时。 */
    private static final long FLUSH_INTERVAL_NANOS = 1_000_000_000L;

    private static BufferedWriter writer;
    private static Path file;
    private static boolean openFailed;
    private static long rows;
    private static long lastFlushNanos;

    private ClientFrameProbe() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static Path file() {
        return file;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || openFailed) {
            return;
        }
        if (!ensureOpen()) {
            return;
        }
        write(Minecraft.getInstance().getFrameTimeNs());
    }

    private static boolean ensureOpen() {
        if (writer != null) {
            return true;
        }
        Path dir = FMLPaths.GAMEDIR.get().resolve("frame_profile");
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve("frames-" + LocalDateTime.now().format(STAMP) + ".csv");
            writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
            file = target;
            writer.write("# wall_ms,frame_ns,body,glow,core,verts,getBuffer\n");
            writer.flush();
            Gensokyou.LOGGER.info("[frame-probe] writing {}", target.toAbsolutePath());
            return true;
        } catch (IOException exc) {
            Gensokyou.LOGGER.error("[frame-probe] cannot open frame log", exc);
            openFailed = true;
            return false;
        }
    }

    private static void write(long frameNanos) {
        try {
            writer.write(Long.toString(System.currentTimeMillis()));
            writer.write(',');
            writer.write(Long.toString(frameNanos));
            writer.write(',');
            writer.write(Integer.toString(DanmakuRenderProbe.lastBodyDraws));
            writer.write(',');
            writer.write(Integer.toString(DanmakuRenderProbe.lastGlowDraws));
            writer.write(',');
            writer.write(Integer.toString(DanmakuRenderProbe.lastCoreDraws));
            writer.write(',');
            writer.write(Long.toString(DanmakuRenderProbe.lastVertexCount));
            writer.write(',');
            writer.write(Integer.toString(DanmakuRenderProbe.lastBufferCalls));
            writer.write('\n');
            rows++;
            long now = System.nanoTime();
            if (now - lastFlushNanos >= FLUSH_INTERVAL_NANOS) {
                lastFlushNanos = now;
                writer.flush();
            }
        } catch (IOException exc) {
            // 逐帧记录不许把游戏带崩：报一次，然后放弃。
            Gensokyou.LOGGER.error("[frame-probe] write failed after {} rows, giving up", rows, exc);
            openFailed = true;
            try {
                writer.close();
            } catch (IOException ignored) {
                // best effort
            }
            writer = null;
        }
    }
}
