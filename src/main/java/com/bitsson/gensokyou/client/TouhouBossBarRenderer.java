package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.config.GensokyouClientConfig;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.TouhouBoss;
import com.bitsson.gensokyou.registry.TierPalette;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * 东方 BOSS 的<b>咒符条</b>血条（touhou-boss-bar）：取消原版血条并自绘一套咒符造型。
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
 * <h2>造型按阶级选</h2>
 * {@code frame_1..5} 五套边框，由 {@link TouhouBoss#bossTier()} 索引；<b>同一阶共用一套</b>，
 * 不逐 BOSS 个体定制。条身取该阶品阶色（{@link TierPalette}，全模组单一色源）。
 *
 * <p><b>五阶差异是「主题」而非「亮度」</b>：每一阶是一套独立的装饰意象，
 * 主题色与 {@link TierPalette} 同色相（1 绿竹 / 2 蓝水纹 / 3 金箔流光 / 4 彼岸花 / 5 紫境界裂痕），
 * 阶越高纹样越密、越华丽。边框色**画死在像素里**，不参与运行期染色；色相来源仍是
 * {@link TierPalette}（改色时五张图与色板一起改）。
 *
 * <h2>符卡名在血条下方</h2>
 * 血条本体高度<b>不因符卡行而变</b>；符卡行是独立的第二行，只把高度计入
 * {@code setIncrement()}——不计入的话下一根血条会直接画在它上面，而
 * {@code setIncrement()} 是 NeoForge 给的唯一正规扩展点（去 {@code RenderGuiEvent.Post}
 * 重算全部 y 等于把原版的 {@code screenHeight/3} 截断逻辑整个复制一遍）。
 *
 * <h2>贴图与画法（物理像素全分辨率 + 上方花丛）</h2>
 * 边框贴图是 <b>{@value #FRAME_TEX_W}x{@value #FRAME_TEX_H}</b>，即逻辑尺寸
 * 182x(24+{@link #TOP_BAND}) 在 <b>1920x1080 / guiScale=4</b> 下的物理像素数
 * （{@value #TEXEL_SCALE} 倍）。屏上血条本体仍占 182x24 逻辑像素，上方再多 12 行花丛；
 * 默认分辨率下每个 texel 恰好落在一个物理像素上，<b>没有任何缩放</b>。
 *
 * <p><b>为什么不用 1 texel = 1 逻辑像素</b>：182x24 只有 4368 个 texel，端头柱仅 22x20，
 * 扩散模型的细节在屏上被放大 4 倍后读起来仍是「大色块」。本项目明确接受这一例外：
 * 血条是 HUD 上最大的单件美术，按物理像素产出才体现模型的优势。代价是玩家把 guiScale
 * 调到 4 以下时会被降采样（1080p 默认即 4，属预期取值）。
 *
 * <p>美术由 Qwen-Image 扩散模型绘制（整条符纸 + 上方花丛），结构由
 * {@code tools/boss_bar_art.py} 收口时钉死：血槽行范围、血槽左右内缩
 * （{@link #TRACK_INSET}）、血槽区透明。收口**不重绘**血槽边线（保留 AI 原画），
 * 只把边线一圈的 alpha 钉成不透明，避免缩放产生脏边。
 *
 * <p>绘制序 <b>条身先、边框后</b>——两端符柱的纸边与朱印都悬在条身<b>外面</b>，
 * 压在上面才不会被填充盖掉。
 *
 * <p>残影的平滑由 {@code LerpingBossEvent.getProgress()} 自带的 100ms 插值 + 本类额外加的
 * "残影滞留"叠加提供，无额外状态。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class TouhouBossBarRenderer {

    // ---------------------------------------------------------------- 贴图规格
    // 与 tools/boss_bar_art.py 一一对应，改一处 MUST 改另一处。

    /**
     * 贴图相对逻辑坐标的倍率：1920x1080 下 MC 的 auto GUI scale = 4，
     * 即 1 个逻辑像素 = 4x4 物理像素。边框贴图按物理像素 1:1 产出。
     */
    private static final int TEXEL_SCALE = 4;

    /** 符纸上方的花丛带高度（逻辑像素）。血条本体位置不变，花丛向上长。 */
    private static final int TOP_BAND = 12;

    /**
     * 边框贴图尺寸 = 逻辑尺寸 182x(24+{@link #TOP_BAND}) x {@link #TEXEL_SCALE}
     * = <b>728x144 物理像素</b>。
     *
     * <p>默认 1080p / guiScale=4 时，728 texel 恰好铺满 182 逻辑像素的 728 个物理像素，
     * 九宫格中段源宽（{@code 728 - 2*240 == 248 texel}）与目标宽（62 逻辑 = 248 物理）
     * <b>完全重合</b>，屏上零放大。只有玩家把 config 宽度改成非 182 时才拉伸中段。
     *
     * <p>三段切：端头 {@link #FRAME_CAP_TEX} texel / 中段 {@link #FRAME_MID_TEX} texel / 端头；
     * 目标侧端头是 {@link #FRAME_CAP} 逻辑像素。
     */
    private static final int FRAME_TEX_W = 182 * TEXEL_SCALE;
    private static final int FRAME_TEX_H = (24 + TOP_BAND) * TEXEL_SCALE;
    private static final int FRAME_CAP_TEX = 60 * TEXEL_SCALE;
    private static final int FRAME_MID_TEX = FRAME_TEX_W - 2 * FRAME_CAP_TEX;
    private static final int FRAME_CAP = 60;
    /** 边框贴图的**逻辑**高度（= 贴图 texel 高 / 倍率）。 */
    private static final int FRAME_LOGICAL_H = FRAME_TEX_H / TEXEL_SCALE;

    /**
     * 边框贴图里<b>透明血槽</b>（= 血条条身区）的 texel 行范围，闭区间。
     *
     * <p>纵向分区（逻辑 36 行）：0..13 花丛带 / 14..17 符纸上边距 / 18..19 顶边 /
     * 20..27 血槽 / 28..29 底边 / 30..33 下边距 / 34..35 透明。
     *
     * <p>以下是旧版符纸贴图的默认窗口；透明原画导入时由 tracks.json 提供逐阶窗口。
     * <b>旧版 5 阶共用同一个窗口</b>：窗口位置若随阶变化，条身高度就会变成
     * 阶的函数，于是 config 的 bodyHeight、多血条堆叠预算、`screenHeight/3` 截断判定
     * 全都要跟着分叉。逐阶差异只放在窗口之外的装饰里。
     */
    private static final int WIN_ROW_TOP_TEX = (TOP_BAND + 8) * TEXEL_SCALE;
    private static final int WIN_ROW_BOTTOM_TEX = (TOP_BAND + 16) * TEXEL_SCALE - 1;
    /** 窗口高（逻辑像素）。文本路径的条身高度恒等于此值。 */
    private static final int BODY_H = (WIN_ROW_BOTTOM_TEX - WIN_ROW_TOP_TEX + 1) / TEXEL_SCALE;

    /**
     * 血槽左右各内缩几列，<b>5 阶 MUST 同一值</b>。
     *
     * <p>两端的装饰柱（符首 / 符尾）占 {@code 0..21} 与 {@code 160..181}，
     * 条身只画中间 {@code 22..159}。内缩若随阶变，<b>填充长度就成了阶的函数</b>
     * （同一血量在高阶显得更短），那是读数错误而不是装饰。
     */
    private static final int TRACK_INSET = 22;


    /** 条身贴图尺寸（灰度，被 tint）。32 texel 高 = 条身 8 逻辑行在 guiScale=4 下的物理像素数；横向每行同色，拉伸无损。 */
    private static final int SEG_TEX_W = 64;
    private static final int SEG_TEX_H = 32;

    private static final ResourceLocation SEGMENT =
            Gensokyou.id("textures/gui/boss_bar/segment.png");

    /**
     * 液面前缘（灵力收口）：8x32 的近白月牙，运行期乘**提亮后的阶色**。
     *
     * <p>为什么单独一张：条身贴图的最大亮度只有 0.86，乘法染色又只能压暗；
     * 想让「血量前端」比条身更亮，只能另给一张近白的高光贴图并乘一个提亮色。
     */
    private static final ResourceLocation SEGMENT_EDGE =
            Gensokyou.id("textures/gui/boss_bar/segment_edge.png");
    private static final int EDGE_TEX_W = 8;
    private static final int EDGE_TEX_H = 32;

    /** 下标 = 阶级-1。越界在 {@link #clampTier} 里夹掉。 */
    private static final ResourceLocation[] FRAMES = {
            Gensokyou.id("textures/gui/boss_bar/frame_1.png"),
            Gensokyou.id("textures/gui/boss_bar/frame_2.png"),
            Gensokyou.id("textures/gui/boss_bar/frame_3.png"),
            Gensokyou.id("textures/gui/boss_bar/frame_4.png"),
            Gensokyou.id("textures/gui/boss_bar/frame_5.png"),
    };

    /** 符卡行高度（像素）。计入 {@code setIncrement()}，不计入血条本体。
     *  <p>行高预算：符卡字在 {@code underY + 4}，字形实占 8 行 → 到 {@code underY + 12}；
     *  下一根血条的名字在其 {@code y - 21}，故卡行需要 11 才能保证 1px 不叠。 */
    private static final int CARD_LINE_H = 11;

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

    /**
     * 实体离开世界时清它的 HUD 记忆。
     *
     * <p>两张表都按实体索引，实体没了就必须清——否则：
     * <ul>
     *   <li>{@link #GHOSTS} 按 UUID 记，永不增长（BOSS 死了但残影条目留下）；
     *   <li>{@link ClientSpellCardNames} 按 entityId 记，而 <b>entityId 会被回收</b>，
     *       残留条目在 id 被分给另一只实体时可能被误读。
     * </ul>
     * 后者真正的错读窗口不存在——新实体的 {@code StartTracking} 补发会直接覆盖旧值，
     * 但那依赖补发一定发生，故这里仍按「必须清」处理，不拿正确性赌时序。
     */
    @SubscribeEvent
    public static void onEntityLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        GHOSTS.remove(entity.getUUID());
        ClientSpellCardNames.remove(entity.getId());
        BossPhaseTransition.forget(entity.getId());
    }

    /**
     * 离开存档 / 重进世界时清空全部 HUD 记忆。
     *
     * <p><b>为什么 {@link #onEntityLeave} 不够</b>：它按实体逐个清，但退出世界时客户端
     * 整个 {@code ClientLevel} 被丢弃，<b>不会</b>为每个实体触发一次 leave 事件。
     * 于是两张静态表跨存档残留：
     * <ul>
     *   <li>{@link ClientSpellCardNames} 按 entityId 记，而 <b>entityId 每个世界从 1 重新分配</b>
     *       —— 上一个世界的条目会被下一个世界的同号实体命中，显示<b>别的 BOSS 的符卡名</b>。
     *   <li>{@link #GHOSTS} 按 UUID 记；读档回来的 BOSS 是<b>同一个 UUID</b>，于是旧残影值
     *       被继承下来，而残影有「永不低于本体」的下限保护，读数会偏高。
     * </ul>
     * 两者都是<b>静默</b>出错：血条照常画，只是画的东西不对。
     */
    @SubscribeEvent
    public static void onLoggingOut(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        GHOSTS.clear();
        ClientSpellCardNames.clear();
        BossPhaseTransition.clear();
    }

    /**
     * 这根 bar 对应的东方 BOSS 实体；不是东方 BOSS（或实体查不到）时返回 null。
     *
     * <p>见类注释"怎么判别"。⚠️ Level 只有 {@code getEntity(int)}，没有 UUID 版本，
     * 而血条给的却是 UUID——唯一能走通的路径是扫 {@code entitiesForRendering()} 比对
     * UUID。它正是渲染器本帧要画的实体集合，故扫到即"看得见"，语义与血条可见性一致。
     * BOSS 血条通常只有个位数，每帧扫几次数百个实体完全可以接受；反向的
     * "维护 UUID→id 表"要额外处理实体卸载，反而更容易在 id 复用时错判。
     */
    private static Entity touhouBossOf(LerpingBossEvent event) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity.getUUID().equals(event.getId()) && entity instanceof TouhouBoss) {
                return entity;
            }
        }
        return null;
    }

    /**
     * 阶级夹取到 1~{@code #FRAMES.length}。
     *
     * <p>一个纯装饰的数值 MUST NOT 有能力把游戏打崩，故越界回落到 1 而不是抛异常。
     */
    private static int clampTier(int tier) {
        return tier < 1 || tier > FRAMES.length ? 1 : tier;
    }

    /**
     * 某阶的边框贴图是否可用（懒查 + 缓存）。
     *
     * <p><b>为什么探 classpath 而不是问 {@code TextureManager}</b>：贴图管理器在文件缺失时
     * 会造一个 {@code SimpleTexture} 占位、加载失败后显示缺失纹理，从外面看它与
     * 「已加载」的那个对象<b>类型一样</b>，没法区分。直接问 classpath 反而是句实话：
     * 「这个文件在不在」。
     *
     * <p><b>为什么还要读尺寸</b>：贴图尺寸是渲染契约的一部分（{@link #FRAME_TEX_W}x
     * {@link #FRAME_TEX_H} texel 内的列/行位置全部被坐标写死）。仓库里留过旧版
     * 182x24（逻辑像素）的图，只更新一部分 assets 时「文件在但尺寸不对」不会报错、
     * 只会画成一团 —— 故尺寸不符 MUST 视同缺失，回落 {@link #drawLegacy}，并打日志。
     *
     * <p>存在性判定的目的：正式美术逐张替换时，缺的那一阶回落成老画法，<b>不崩也不空</b>。
     */
    private static boolean framePresent(int tier) {
        Boolean cached = FRAME_PRESENT[tier - 1];
        if (cached != null) {
            return cached;
        }
        String path = "/assets/gensokyou/textures/gui/boss_bar/frame_" + tier + ".png";
        boolean present = false;
        String why = "missing";
        try (java.io.InputStream in =
                     TouhouBossBarRenderer.class.getResourceAsStream(path)) {
            if (in != null) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
                present = img != null
                        && img.getWidth() == FRAME_TEX_W && img.getHeight() == FRAME_TEX_H;
                why = img == null ? "unreadable"
                        : (present ? "ok" : "wrong size " + img.getWidth() + "x" + img.getHeight());
            }
        } catch (java.io.IOException e) {
            why = "io error: " + e.getMessage();
        }
        FRAME_PRESENT[tier - 1] = present;
        Gensokyou.LOGGER.info("[boss-bar] frame_{}.png {} ({}) -> {}", tier,
                present ? "found" : "UNUSABLE", why,
                present ? "texture path" : "fallback fill()");
        return present;
    }

    private static final Boolean[] FRAME_PRESENT = new Boolean[FRAMES.length];

    /** Optional artwork-specific track bounds, in logical coordinates. */
    private static final int[][] ART_TRACKS = loadArtTracks();

    private static int[][] loadArtTracks() {
        int[][] tracks = new int[FRAMES.length][];
        try (var in = TouhouBossBarRenderer.class.getResourceAsStream(
                "/assets/gensokyou/textures/gui/boss_bar/tracks.json")) {
            if (in != null) {
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                        in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (int tier = 1; tier <= tracks.length; tier++) {
                    var entry = json.getAsJsonArray(Integer.toString(tier));
                    if (entry == null || entry.size() != 4) continue;
                    int[] rect = new int[4];
                    for (int i = 0; i < rect.length; i++) rect[i] = entry.get(i).getAsInt();
                    if (rect[0] >= 0 && rect[0] < rect[2] && rect[2] <= 182
                            && rect[1] >= 0 && rect[1] < rect[3] && rect[3] <= FRAME_LOGICAL_H) {
                        tracks[tier - 1] = rect;
                    }
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            Gensokyou.LOGGER.warn("[boss-bar] Cannot read artwork track bounds", e);
        }
        return tracks;
    }

    private static int frameX(int sourceX, int width) {
        if (sourceX <= FRAME_CAP) return sourceX;
        if (sourceX >= 182 - FRAME_CAP) return width - (182 - sourceX);
        return FRAME_CAP + Math.round((sourceX - FRAME_CAP)
                * Math.max(0, width - 2 * FRAME_CAP) / 62.0F);
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bar = event.getBossEvent();
        Entity boss = touhouBossOf(bar);
        if (boss == null) {
            return; // 不取消：原版照画自己的
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = event.getX();
        int y = event.getY();
        // 符卡名在<b>行高决策之前</b>定：缺键 / 无卡时它是 empty，行高才回落。
        // 顺序反了就会「留出一行的空位却不画」——比不显示更难读。
        Optional<Component> cardName = spellCardLine(boss);
        int required = cardName.isPresent() ? rowHeightWithCard() : rowHeightNoCard();
        // 行高取 config 与实际所需的最大值：config 是下限，保证任何取值下都不重叠
        event.setIncrement(Math.max(event.getIncrement(),
                Math.max(BalanceNumbers.TALISMAN_BAR_ROW_HEIGHT, required)));
        draw(g, bar, boss, x, y, cardName.orElse(null));
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- 绘制

    private static void draw(GuiGraphics g, LerpingBossEvent bar, Entity boss, int x, int y,
                             Component cardName) {
        Font font = Minecraft.getInstance().font;
        int width = GensokyouClientConfig.BOSS_BAR_WIDTH.get();
        int tier = clampTier(((TouhouBoss) boss).bossTier());
        int tierColor = TierPalette.rgb(tier);

        // 贴图向上多出 TOP_BAND 行花丛：为了让**条身本体**停在原来的屏幕位置，
        // 整个边框从 y - TOP_BAND 开始画，而不是从 y。
        int frameY = y - TOP_BAND;

        // 名字画在花丛上方（贴图区之外），颜色跟品阶色主题
        int nameY = frameY - font.lineHeight + 1;
        g.drawString(font, bar.getName(), x + width / 2 - font.width(bar.getName()) / 2, nameY,
                tierColor, false);

        int bodyY = frameY + WIN_ROW_TOP_TEX / TEXEL_SCALE;
        int bodyW = Math.max(0, width - 2 * TRACK_INSET);
        int bodyX = x + TRACK_INSET;
        int bodyHeight = BODY_H;
        int[] artTrack = ART_TRACKS[tier - 1];
        if (artTrack != null) {
            bodyX = x + frameX(artTrack[0], width);
            bodyW = Math.max(0, frameX(artTrack[2], width) - frameX(artTrack[0], width));
            bodyY = frameY + artTrack[1];
            bodyHeight = artTrack[3] - artTrack[1];
        }
        float health = advanceGhost(bar);
        float progress = Mth.clamp(bar.getProgress(), 0.0F, 1.0F);

        // 贴图路径与降级路径的条身高度不同，故「边框下沿」也随之为之。
        boolean textured = framePresent(tier);
        int underY;
        if (!textured) {
            int legacyH = BalanceNumbers.TALISMAN_BAR_BODY_HEIGHT;
            drawLegacy(g, x, bodyY, width, legacyH, health, progress);
            underY = bodyY + legacyH + 2;
        } else {
            // 条身三层：凹轨底（暗、带内阴影）→ 残影（半透明）→ 当前灵力（阶色）
            // 三层都用同一张 64x32 的纵向剖面：槽读作「凹进去的轨」，而不是一条色带。
            tintedBlit(g, SEGMENT, bodyX, bodyY, bodyW, bodyHeight,
                    0, 0, SEG_TEX_W, SEG_TEX_H, SEG_TEX_W, SEG_TEX_H,
                    opaque(trackColor(tier)));
            int ghostW = Mth.floor(bodyW * Mth.clamp(health, 0.0F, 1.0F));
            if (ghostW > 0) {
                tintedBlit(g, SEGMENT, bodyX, bodyY, ghostW, bodyHeight,
                        0, 0, SEG_TEX_W, SEG_TEX_H, SEG_TEX_W, SEG_TEX_H,
                        0x99000000 | (ghostColor() & 0xFFFFFF));
            }
            int fillW = Mth.floor(bodyW * progress);
            if (fillW > 0) {
                tintedBlit(g, SEGMENT, bodyX, bodyY, fillW, bodyHeight,
                        0, 0, SEG_TEX_W, SEG_TEX_H, SEG_TEX_W, SEG_TEX_H,
                        tierColor);
                // 液面前缘：贴着填充右端的一枚提亮月牙，血量越低也至少占 1px
                int ew = Math.min(EDGE_TEX_W, fillW);
                int eu = EDGE_TEX_W - ew;
                tintedBlit(g, SEGMENT_EDGE,
                        bodyX + fillW - ew, bodyY, ew, bodyHeight,
                        eu, 0, ew, EDGE_TEX_H, EDGE_TEX_W, EDGE_TEX_H,
                        lighten(tierColor));
            }
            // 边框最后画：纸框、藤蔓与装饰压住条身与填充
            nineSlice(g, FRAMES[tier - 1], x, frameY, width);
            underY = frameY + FRAME_LOGICAL_H;
        }

        int seal = 0;
        if (!textured) {
            // 降级画法仍用老版式：燕尾撕边 + 右下朱印方块（没有贴图就没有纸边）
            drawTornEdge(g, x, underY, width, bodyH());
            seal = Mth.ceil(BalanceNumbers.TALISMAN_BAR_SEAL_SIZE);
            if (seal > 0) {
                fill(g, x + width - seal, underY, seal, seal, frameColor());
            }
        }

        if (cardName != null) {
            drawCardLine(g, font, cardName, x, width, seal, underY, tierColor);
        }
    }

    /**
     * 当前符卡名；无符卡 / 无下标 / 缺 lang 键时返回空。
     *
     * <p><b>缺 lang 键的判据</b>是「解析结果仍是 {@code spellcard.gensokyou.} 开头的串」
     * ——lang 未加载或键缺失时 {@code getString()} 原样吐出键名，画出来是
     * {@code spellcard.gensokyou.rumia.3} 这串东西，比不显示更难读。
     * 真正的防线是 {@code tools/lang_audit.py}（它展开 {@code BossCards#card()} 逐键核对），
     * 这里只是渲染期兜底。
     */
    private static Optional<Component> spellCardLine(Entity boss) {
        OptionalInt index = ClientSpellCardNames.get(boss.getId());
        if (index.isEmpty()) {
            return Optional.empty();
        }
        return ((TouhouBoss) boss).spellCardName(index.getAsInt())
                .filter(name -> !name.getString().startsWith(UNRESOLVED_KEY_PREFIX));
    }

    /** 未解析的 lang 键会原样以此开头（见 {@link #spellCardLine}）。 */
    private static final String UNRESOLVED_KEY_PREFIX = "spellcard.gensokyou.";

    /**
     * 符卡行：右对齐到朱印左侧，与朱印构成「落款 + 钤印」版式。
     *
     * <p>宽度余量：182px ÷ 9px（CJK 字宽）≈ 19 字，扣掉朱印仍有 18 字；现有符卡名最长 4 字。
     * 故<b>不设省略号</b>——真超长再说，别为一个从未发生的情况牺牲可读性。
     */
    private static void drawCardLine(GuiGraphics g, Font font, Component name,
                                    int x, int width, int seal, int underY, int color) {
        String text = name.getString();
        int right = x + width - (seal > 0 ? seal + 2 : 0);
        g.drawString(font, text, right - font.width(text), underY + 4, color, false);
    }

    /**
     * 降级画法：贴图缺失时的老式纯 {@code fill()} 血条。
     *
     * <p>形体取自本变更之前的那版（符首 2 + 条身 7 + 符尾 2），故 config 的
     * {@code TALISMAN_BAR_BODY_HEIGHT} 在这条路径上仍然有效——它没有变成死配置。
     * 条身颜色退回 config 的朱砂（不含阶色）：没有贴图就没有 tint 通道，
     * 而「假装有阶色」只会让占位色与真阶色两套值并存，比老实退回更糟。
     *
     * <p>条身横跨<b>满宽</b>（没有端头符柱），与贴图路径的差别是整块端头装饰而不是那几像素——
     * 降级路径不追求像素一致，追求的是「有图没图都不崩、不空、不重叠」。
     */
    private static void drawLegacy(GuiGraphics g, int x, int bodyY, int width, int legacyH,
                                   float health, float progress) {
        fill(g, x, bodyY, width, legacyH, trackColor());
        int ghostW = Mth.floor(width * Mth.clamp(health, 0.0F, 1.0F));
        fill(g, x, bodyY, ghostW, legacyH, ghostColor());
        int fillW = Mth.floor(width * progress);
        fill(g, x, bodyY, fillW, legacyH, BalanceNumbers.TALISMAN_BAR_COLOR_FILL);
        fill(g, x, bodyY - 2, width, 2, frameColor());
        fill(g, x, bodyY + legacyH, width, 2, frameColor());
    }

    /**
     * 无符卡行时一行需要的高度。
     *
     * <p>贴图从 {@code y - TOP_BAND} 画到 {@code y + 24}，名字在它上方 9 行，
     * 故整行是 {@code TOP_BAND + 24 + 9 = 45}，留 1px 呼吸带。
     * 降级路径（2 + 7 + 2 + 撕边 = 17）比它矮，取大者即可覆盖两者。
     */
    private static int rowHeightNoCard() {
        return FRAME_LOGICAL_H + NAME_LINE_H + 1;
    }

    /** 名字行高（= MC 默认 Font.lineHeight；仅用于行高预算）。 */
    private static final int NAME_LINE_H = 9;

    /**
     * 条身高度 = 边框贴图的透明血槽高（{@link #BODY_H}），<b>不是</b> config 的
     * {@code TALISMAN_BAR_BODY_HEIGHT}——后者控制的是<b>降级画法</b>的条身高度。
     * 让 config 也能改贴图路径的条身高，就得让血槽高度随 config 变，而血槽位置一阶一档
     * 就会把堆叠预算和截断判定全拖下水（见 {@link #WIN_ROW_TOP_TEX} 的说明）。
     */
    private static int bodyH() {
        return BODY_H;
    }

    private static int rowHeightWithCard() {
        return rowHeightNoCard() + CARD_LINE_H;
    }

    // ---------------------------------------------------------------- 九宫格与 tint

    /**
     * 九宫格拉伸：端头原样、中段横向拉伸。
     *
     * <p>默认宽度 182 时 {@code midTarget == 62 逻辑像素 == 248 texel}，与中段源宽
     * {@link #FRAME_MID_TEX} 相等：在 guiScale=4 下<b>原样 1:1</b>，不产生任何拉伸；
     * 只有玩家改 config 宽度时才拉伸中段。中段之所以敢拉伸，是因为
     * {@code tools/boss_bar_art.py} 收口时把中段 248 列做成了<b>横向均质</b>
     * （每行同色），横向复制不失真。透明原画保留中段装饰，改宽时其装饰也会拉伸；
     * 默认 182 宽仍是原样映射。
     *
     * <p>⚠️ 目标宽度/高度用**逻辑像素**，源区域用**texel**——两者差 {@link #TEXEL_SCALE} 倍，
     * 混用会画成裁切错位（不报错，只是画错）。
     */
    private static void nineSlice(GuiGraphics g, ResourceLocation tex,
                                  int x, int y, int targetW) {
        if (targetW <= 0) {
            return;
        }
        // Plain GuiGraphics.blit does not enable blending. tintedBlit above
        // explicitly disables it, so without this even almost-transparent
        // texels expose their hidden RGB as opaque coloured noise.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            int midTarget = targetW - 2 * FRAME_CAP;
            if (midTarget > 0) {
                blitRegion(g, tex, x + FRAME_CAP, y, midTarget, FRAME_LOGICAL_H,
                        FRAME_CAP_TEX, 0, FRAME_MID_TEX, FRAME_TEX_H);
            }
            blitRegion(g, tex, x, y, FRAME_CAP, FRAME_LOGICAL_H,
                    0, 0, FRAME_CAP_TEX, FRAME_TEX_H);
            blitRegion(g, tex, x + targetW - FRAME_CAP, y, FRAME_CAP, FRAME_LOGICAL_H,
                    FRAME_TEX_W - FRAME_CAP_TEX, 0, FRAME_CAP_TEX, FRAME_TEX_H);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    /** 原版 {@code GuiGraphics.blit} 的独立纹理子区域版本（不染色）。 */
    private static void blitRegion(GuiGraphics g, ResourceLocation tex,
                                   int x, int y, int w, int h,
                                   int u, int v, int uW, int vH) {
        g.blit(tex, x, y, w, h, (float) u, (float) v, uW, vH, FRAME_TEX_W, FRAME_TEX_H);
    }

    /**
     * 带染色的子区域 blit。
     *
     * <p><b>为什么不能直接用 {@code GuiGraphics.blit} + {@code setColor}</b>：
     * 原版的独立纹理 blit 走 {@code innerBlit}，顶点格式是 {@code POSITION_TEX}
     * ——<b>根本没有颜色属性</b>；而 {@code setColor} 设的是全局 {@code ColorModulator}
     * uniform，{@code core::position_tex} 这个 shader 不读它。两者相乘的结果是「不染色」。
     * 原版唯一带染色的 blit 重载只接受 {@code TextureAtlasSprite}（要自己拼图集），
     * 且那个 {@code innerBlit} 是包级私有、调不到。
     *
     * <p>故这里手搓一个 {@code POSITION_TEX_COLOR} 的四边形，调用链与原版带染色分支
     * 逐行一致（{@code setShaderTexture} → {@code setShader} → {@code enableBlend} →
     * {@code drawWithShader} → {@code disableBlend}）。乘法染色只能压暗不能提亮，
     * 所以条身贴图的斜面只做「上亮下暗」。
     */
    private static void tintedBlit(GuiGraphics g, ResourceLocation tex,
                                   int x, int y, int w, int h,
                                   int u, int v, int uW, int vH, int texW, int texH,
                                   int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float a = (argb >>> 24 & 0xFF) / 255.0F;
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float gr = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float minU = u / (float) texW;
        float maxU = (u + uW) / (float) texW;
        float minV = v / (float) texH;
        float maxV = (v + vH) / (float) texH;

        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = g.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(pose, (float) x, (float) y, 0.0F).setUv(minU, minV).setColor(r, gr, b, a);
        buffer.addVertex(pose, (float) x, (float) (y + h), 0.0F).setUv(minU, maxV).setColor(r, gr, b, a);
        buffer.addVertex(pose, (float) (x + w), (float) (y + h), 0.0F).setUv(maxU, maxV).setColor(r, gr, b, a);
        buffer.addVertex(pose, (float) (x + w), (float) y, 0.0F).setUv(maxU, minV).setColor(r, gr, b, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /**
     * 推进掉血残影：本体掉血当刻残影<b>原地不动</b>，延迟期满后再线性追上。
     *
     * <p>与 {@code LerpingBossEvent.getProgress()} 的分工：后者给的是本体自身的 100ms
     * 平滑，本方法额外加一段"残影滞留"，两者叠加才读得出"刚刚被打了一大口"。
     *
     * <p>条目在追上后立即移除，故该 Map 的规模是"当前在场且刚掉过血的 bar 数"，不随时间增长。
     */
    private static float advanceGhost(LerpingBossEvent bar) {
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
        if (now - ghost.changedAtMs >= BalanceNumbers.TALISMAN_BAR_GHOST_DELAY_MS) {
            ghost.health = Math.max(health, ghost.health - GHOST_CATCHUP_PER_FRAME);
        }
        if (Math.abs(ghost.health - health) < 0.002F) {
            GHOSTS.remove(bar.getId());
        }
        return ghost.health;
    }

    /** 燕尾撕边：沿宽度均分若干三角齿，齿高取条身高的一半。 */
    private static void drawTornEdge(GuiGraphics g, int x, int y, int width, int bodyH) {
        int teeth = BalanceNumbers.TALISMAN_BAR_TORN_EDGE;
        if (teeth <= 0 || width <= 0 || bodyH <= 0) {
            return;
        }
        int step = Math.max(1, width / teeth);
        int depth = Math.max(1, bodyH / 2);
        for (int i = 0; i * step < width; i++) {
            int w = Math.min(step, width - i * step);
            int rows = Math.max(1, depth * w / step);
            fill(g, x + i * step, y, w, rows, frameColor());
        }
    }

    /** 实心矩形。颜色按 0xRRGGBB 存于 config，故补 alpha。 */
    private static void fill(GuiGraphics g, int x, int y, int w, int h, int rgb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, opaque(rgb));
    }

    /** 0xRRGGBB -> 0xFFRRGGBB。{@code tintedBlit} 读 alpha，缺 alpha 会整块透明。 */
    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /**
     * 阶色的提亮版（约 1.25x），给液面前缘用。
     *
     * <p>乘法染色只能压暗：条身贴图最大 0.86，前缘贴图近白再乘提亮色，
     * 才会比条身亮一档。金色这类接近上限的色会被压到 255，属预期。
     */
    private static int lighten(int argb) {
        int a = argb >>> 24 & 0xFF;
        int r = Math.min(255, (argb >> 16 & 0xFF) * 5 / 4);
        int g = Math.min(255, (argb >> 8 & 0xFF) * 5 / 4);
        int b = Math.min(255, (argb & 0xFF) * 5 / 4);
        return a << 24 | r << 16 | g << 8 | b;
    }

    // config 色：文本路径下它们仍是**结构色**（暗轨底 / 残影 / 朱红框 / 撕边），
    // 只有「当前血量」那一层走阶色贴图。旧值保留不删——降级画法仍可能用到。

    private static int frameColor() {
        return BalanceNumbers.TALISMAN_BAR_COLOR_FRAME;
    }

    private static int trackColor() {
        return BalanceNumbers.TALISMAN_BAR_COLOR_TRACK;
    }

    /**
     * 凹轨色：深墨底 + 25% 品阶色。
     *
     * <p>纯 {@code #2A1A1C} 在暗色剖面下就是一块纯黑（实机反馈「血条底不要纯黑」）。
     * 混入品阶色后，绿阶是暗橄榄、蓝阶是暗蓝灰、金阶是暗金褐、红阶是暗红、紫阶是暗紫，
     * 既保留「凹槽」的暗度，又和边框主题同源。
     */
    private static int trackColor(int tier) {
        return blendRgb(trackColor(), TierPalette.rgb(tier), 0.25F);
    }

    /** 0xRRGGBB 两色线性混合；输入可带 alpha，输出 alpha = 0xFF。 */
    private static int blendRgb(int a, int b, float t) {
        int r = Math.round((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int g = Math.round((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int ghostColor() {
        return BalanceNumbers.TALISMAN_BAR_COLOR_GHOST;
    }
}
