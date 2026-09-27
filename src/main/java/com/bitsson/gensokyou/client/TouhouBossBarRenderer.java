package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.TouhouBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 东方 BOSS 的<b>咒符条</b>血条（touhou-boss-bar）：取消原版血条并自绘一套固定造型的咒符。
 *
 * <h2>为什么不需要 mixin</h2>
 * {@code BossHealthOverlay.render} 对每根 bar 都会触发一个<b>可取消</b>的 NeoForge 事件：
 * <pre>
 *   var event = ClientHooks.onCustomizeBossEventProgress(g, window, bar, x, y, 10 + lineHeight);
 *   if (!event.isCanceled()) { drawBar(...); drawString(name); }
 *   j += event.getIncrement();          // 取消后仍照常推进，故行高仍由我们控制
 * </pre>
 * 事件被取消时，原版的 182×5 血条与原版名字<b>一起</b>跳过，而行高推进仍会执行。
 * 于是"自绘条 + 自控行高"是免费的——不需要 mixin、不需要新 payload、不需要 accessor。
 *
 * <h2>怎么判别"东方 BOSS"</h2>
 * {@code LerpingBossEvent extends BossEvent}，而 {@code BossEvent.getId()} 给出的是服务端
 * 写下这根 bar 时用的 UUID，也就是<b>实体 UUID</b>。BOSS 血条只对能观察到该 BOSS 的玩家
 * 呈现，故该实体在客户端必然已加载，于是判别是纯客户端的一次查表：
 * <pre>
 *   level.getEntity(evt.getId()) instanceof &lt;东方 BOSS&gt;
 * </pre>
 * 刻意<b>不</b>用"名字前缀约定"或"占用某个 BossBarColor/Overlay"当标记——那两种做法在
 * 新增实体或与别的模组共用颜色时会静默误判。实体查不到时<b>不干预</b>，让原版自己画。
 *
 * <h2>造型</h2>
 * 一套固定造型通用于全部东方 BOSS，<b>不逐 BOSS 定制</b>：朱红符首/符尾、米白符纸底、
 * 朱砂血条、米白掉血残影、燕尾撕边、右下朱印。后续新增东方 BOSS 自动套用。
 * 残影的平滑由 {@code LerpingBossEvent.getProgress()} 自带的 100ms 插值提供，无额外状态。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class TouhouBossBarRenderer {

    private TouhouBossBarRenderer() {
    }

    /**
     * 掉血残影的按 UUID 记忆表：血条当前值 + 残影还差多少才追上。
     *
     * <p><b>必须按 UUID 记</b>，不能按"上一根画的 bar"记——多根 bar 同帧绘制时后者会串味。
     * 残影延迟 0 后残影与本体重合，故此时可安全清条目。
     */
    private static final Map<UUID, Ghost> GHOSTS = new HashMap<>();

    private static final class Ghost {
        /** 残影当前停留的血量。 */
        float health = 1.0F;
        /** 上一次观测到的本体血量（用于识别"这一帧真的又掉了一口"）。 */
        float lastHealth = 1.0F;
        /** 本体掉血的时刻；残影在它之后仍原地停留 {@code ghostDelayMs}。 */
        long changedAtMs = System.currentTimeMillis();
    }

    /** 残影每帧向本体收敛的步长（约 0.02 → 50 帧走完一整条）。 */
    private static final float GHOST_CATCHUP_PER_FRAME = 0.02F;

    /** 东方 BOSS 类型判别。见类注释"怎么判别"。逐 BOSS implements {@link TouhouBoss} 即可。 */
    public static boolean isTouhouBoss(LerpingBossEvent event) {
        if (Minecraft.getInstance().level == null) {
            return false;
        }
        // ⚠️ Level 只有 getEntity(int)，没有 UUID 版本；血条给的却是 UUID。
        // 唯一能走通的路径是扫 entitiesForRendering() 比对 UUID —— 它正是渲染器本帧要画的
        // 实体集合，故扫到即"看得见"，语义与血条可见性一致。BOSS 血条通常只有个位数，
        // 每帧扫几次数百个实体完全可以接受；反向的"维护 UUID→id 表"要额外处理实体卸载，
        // 反而更容易在 id 复用时错判。
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity.getUUID().equals(event.getId())) {
                return entity instanceof TouhouBoss;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bar = event.getBossEvent();
        if (!isTouhouBoss(bar)) {
            return; // 不取消：原版照画自己的
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = event.getX();
        int y = event.getY();
        // 抬高行高，容得下符首 + 撕边 + 朱印（默认是 10 + lineHeight）。
        event.setIncrement(Math.max(event.getIncrement(),
                GensokyouConfig.TALISMAN_BAR_ROW_HEIGHT.get()));
        draw(g, bar, x, y);
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- 绘制

    private static void draw(GuiGraphics g, LerpingBossEvent bar, int x, int y) {
        Font font = Minecraft.getInstance().font;
        int width = GensokyouConfig.TALISMAN_BAR_WIDTH.get();
        int bodyH = GensokyouConfig.TALISMAN_BAR_BODY_HEIGHT.get();
        int frame = GensokyouConfig.TALISMAN_BAR_COLOR_FRAME.get();
        int track = GensokyouConfig.TALISMAN_BAR_COLOR_TRACK.get();
        int fill = GensokyouConfig.TALISMAN_BAR_COLOR_FILL.get();
        int ghostColor = GensokyouConfig.TALISMAN_BAR_COLOR_GHOST.get();

        Component name = bar.getName();
        // 名字画在符纸之上（原版是画在条上方，这里上移到符首里，读作"符上书名"）
        int nameY = y - font.lineHeight + 1;
        g.drawString(font, name, x + width / 2 - font.width(name) / 2, nameY, frame, false);

        int bodyY = y;
        // 符纸底（米白边 + 暗轨底）
        fill(g, x, bodyY, width, bodyH, track);
        // 掉血残影：白底，画在填充<b>之后</b>（否则会被完全盖住）
        float health = advanceGhost(bar, font);
        int ghostW = Mth.floor(width * Mth.clamp(health, 0.0F, 1.0F));
        fill(g, x, bodyY, ghostW, bodyH, ghostColor);
        // 当前血量
        int fillW = Mth.floor(width * Mth.clamp(bar.getProgress(), 0.0F, 1.0F));
        fill(g, x, bodyY, fillW, bodyH, fill);
        // 符首 / 符尾：上下两条朱红横边，把血条包成"符"
        fill(g, x, bodyY - 2, width, 2, frame);
        fill(g, x, bodyY + bodyH, width, 2, frame);
        // 燕尾撕边：底边下方的三角齿，读作撕开的符纸
        drawTornEdge(g, x, bodyY + bodyH + 2, width, bodyH, frame);
        // 右下朱印
        int seal = Mth.ceil(GensokyouConfig.TALISMAN_BAR_SEAL_SIZE.get());
        if (seal > 0) {
            fill(g, x + width - seal, bodyY + bodyH + 2, seal, seal, frame);
        }
    }

    /**
     * 推进掉血残影：本体掉血当刻残影<b>原地不动</b>，延迟期满后再线性追上。
     *
     * <p>与 {@code LerpingBossEvent.getProgress()} 的分工：后者给的是本体自身的 100ms
     * 平滑，本方法额外加一段"残影滞留"，两者叠加才读得出"刚刚被打了一大口"。
     *
     * <p>条目在追上后立即移除，故该 Map 的规模是"当前在场且刚掉过血的 bar 数"，不随时间增长。
     */
    private static float advanceGhost(LerpingBossEvent bar, Font font) {
        float health = Mth.clamp(bar.getProgress(), 0.0F, 1.0F);
        Ghost ghost = GHOSTS.get(bar.getId());
        if (ghost == null) {
            ghost = new Ghost();
            ghost.health = health;
            ghost.lastHealth = health;
            GHOSTS.put(bar.getId(), ghost);
            return health;
        }
        long now = System.currentTimeMillis();
        // ⚠️ 只有"本帧真的又掉了一口"才重置滞留窗口。写成"当前低于残影就重置"会在
        // 残影追赶的每一帧都刷新计时器，于是延迟永远走不完、残影永久停在旧血量。
        if (health < ghost.lastHealth) {
            ghost.changedAtMs = now;
        }
        ghost.lastHealth = health;
        // 残影永不低于本体（否则会出现"白条比血还多"的倒挂读数）
        ghost.health = Math.max(ghost.health, health);
        if (now - ghost.changedAtMs >= GensokyouConfig.TALISMAN_BAR_GHOST_DELAY_MS.get()) {
            ghost.health = Math.max(health, ghost.health - GHOST_CATCHUP_PER_FRAME);
        }
        if (Math.abs(ghost.health - health) < 0.002F) {
            GHOSTS.remove(bar.getId());
        }
        return ghost.health;
    }

    /** 燕尾撕边：沿宽度均分若干三角齿，齿高取血条高的一半。 */
    private static void drawTornEdge(GuiGraphics g, int x, int y, int width, int bodyH, int color) {
        int teeth = GensokyouConfig.TALISMAN_BAR_TORN_EDGE.get();
        if (teeth <= 0 || width <= 0 || bodyH <= 0) {
            return;
        }
        int step = Math.max(1, width / teeth);
        int depth = Math.max(1, bodyH / 2);
        for (int i = 0; i * step < width; i++) {
            int w = Math.min(step, width - i * step);
            int rows = Math.max(1, depth * w / step);
            fill(g, x + i * step, y, w, rows, color);
        }
    }

    /** 实心矩形。颜色按 0xRRGGBB 存于 config，故补 alpha。 */
    private static void fill(GuiGraphics g, int x, int y, int w, int h, int rgb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, 0xFF000000 | (rgb & 0xFFFFFF));
    }
}
