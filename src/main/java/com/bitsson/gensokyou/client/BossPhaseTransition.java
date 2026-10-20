package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.TouhouBoss;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 切阶段演出：符卡切换时的「符卡宣言」。
 *
 * <p>原作的冲击来自「音效 + 浮空立绘 + 符卡名」。本项目<b>没有立绘</b>，于是用
 * <b>符名 + 法阵 + 朱印</b>替代 —— 这也更贴本项目的世界观：符卡是写出来的二维物，
 * 活在立体世界里（见 boss-dev-design §16）。一张在屏幕上展开的符卡，
 * 比一张别家画风的立绘更像幻想乡自己的符号。
 *
 * <h2>触发</h2>
 * 演出由「该 BOSS 的<b>符卡下标发生变化</b>」触发。
 * 「玩家开始观测时补发当前下标」<b>不</b>触发 —— 玩家从未见过那张牌的宣言，
 * 不该在晚进场时补播一遍。
 *
 * <h2>只影响表现</h2>
 * 演出不改动判定、伤害、符卡内容时序与切牌时机。
 * 法阵与白闪一律走<b>加法混合</b>：加法只提亮不遮盖，弹幕在演出期间仍然清晰可辨
 * （弹幕在压暗期间仍然判伤，压暗会制造「看不清却挨打」的不可读局面）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class BossPhaseTransition {

    /** 宣言总时长（秒）。1 秒：够读出符卡名，又不至于挡住战斗。 */
    private static final double DECLARE_SECONDS = 1.0D;
    /** 展开时长（秒）。 */
    private static final double OPEN_SECONDS = 0.14D;
    /** 收尾淡出时长（秒）。 */
    private static final double FADE_SECONDS = 0.30D;
    /** 白闪时长（秒）。 */
    private static final double FLASH_SECONDS = 0.26D;
    /** 法阵每秒自旋角度。 */
    private static final double SPIN_DEGREES_PER_SECOND = 42.0D;

    /** 未解析的 lang 键会原样以此开头（与咒符条血条同一判据）。 */
    private static final String UNRESOLVED_KEY_PREFIX = "spellcard.gensokyou.";

    private static final ResourceLocation MAGIC_CIRCLE =
            Gensokyou.id("textures/fx/magic_circle.png");
    private static final int MAGIC_CIRCLE_TEX = 16;

    /**
     * 声明音效。<b>原版占位</b>：项目尚无自制音效资源，先用末影龙的吼叫顶
     * （1.21.1 的 {@code SoundEvents} 里没有专门的「复活」音效，这条吼叫就是
     * 「龙出现/咆哮」那一声）。后续换自制素材时只改这一处。
     */
    private static final ResourceLocation DECLARE_SOUND =
            ResourceLocation.parse("minecraft:entity.ender_dragon.growl");

    private record Declaration(int cardIndex, long startedNanos) {}

    private static final Map<Integer, Declaration> DECLARATIONS = new LinkedHashMap<>();

    private BossPhaseTransition() {}

    /** 符卡下标变化时播一次宣言。返回 true 表示这是一次真正的切换（而非状态补发）。 */
    public static boolean declare(int entityId, int cardIndex) {
        DECLARATIONS.put(entityId, new Declaration(cardIndex, System.nanoTime()));
        playDeclareSound();
        return true;
    }

    /** 实体离开世界：清掉它的声明记录（entityId 会被回收）。 */
    public static void forget(int entityId) { DECLARATIONS.remove(entityId); }

    /** 断线重连 / 切世界：全部清空。 */
    public static void clear() { DECLARATIONS.clear(); }

    private static void playDeclareSound() {
        try {
            BuiltInRegistries.SOUND_EVENT.getOptional(DECLARE_SOUND).ifPresent(event ->
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 0.85F)));
        } catch (RuntimeException ignored) {
            // 表现层的音效缺失 MUST NOT 打断演出。
        }
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Gensokyou.id("boss_phase_transition"), BossPhaseTransition::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui || minecraft.isPaused()) {
            return;
        }
        if (DECLARATIONS.isEmpty()) return;
        long now = System.nanoTime();
        // 按宣言时刻排序：同一时间窗内多只 BOSS 切牌时纵向错开，不叠在一起。
        var active = new java.util.ArrayList<Map.Entry<Integer, Declaration>>(DECLARATIONS.entrySet());
        active.sort(java.util.Comparator.comparingLong(entry -> entry.getValue().startedNanos()));
        int slot = 0;
        for (var entry : active) {
            Declaration declaration = entry.getValue();
            double age = (now - declaration.startedNanos()) * 1.0E-9D;
            if (age >= DECLARE_SECONDS) { DECLARATIONS.remove(entry.getKey()); continue; }
            Entity entity = minecraft.level.getEntity(entry.getKey());
            if (!(entity instanceof TouhouBoss boss)) continue;
            Optional<String> name = resolvedName(boss, declaration.cardIndex());
            if (name.isEmpty()) continue;   // 缺 lang 键：跳过整次宣言，不画原始键名
            draw(graphics, name.get(), age, slot++);
        }
    }

    /** 符卡名走 lang 键反查；缺键时返回空（不显示原始键名）。 */
    private static Optional<String> resolvedName(TouhouBoss boss, int cardIndex) {
        return boss.spellCardName(cardIndex)
                .map(Component::getString)
                .filter(text -> !text.startsWith(UNRESOLVED_KEY_PREFIX));
    }

    private static void draw(GuiGraphics graphics, String name, double age, int slot) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // 白闪：起始的一瞬间顶满，随后线性退去。刻意压低强度：弹幕在闪白期间仍然判伤，
        // 盖得太狠就变成「看不清却挨打」。
        float flash = Math.max(0.0F, 1.0F - (float) (age / FLASH_SECONDS));
        if (flash > 0.0F) {
            graphics.fill(0, 0, width, height, argb(flash * 0.35F, 0xFF, 0xFF, 0xFF));
        }
        float open = Math.min(1.0F, (float) (age / OPEN_SECONDS));
        float fade = Math.min(1.0F, (float) ((DECLARE_SECONDS - age) / FADE_SECONDS));
        float envelope = Math.max(0.0F, Math.min(open, fade));
        if (envelope <= 0.0F) return;

        int centerX = width / 2;
        int centerY = (int) (height * 0.28F) + slot * 26;

        // 法阵：加法混合的旋转贴图。16×16 的素材放大后是一圈柔和的符环。
        float spin = (float) (age * SPIN_DEGREES_PER_SECOND);
        int ring = (int) (96 * (0.6F + 0.4F * envelope));
        additiveMagicCircle(graphics, centerX, centerY, ring, spin, envelope);

        Font font = Minecraft.getInstance().font;
        int scale = 2;
        int textWidth = font.width(name);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        // 横向展开：符卡名从中间向两侧拉开，读作「宣读」。
        int stretched = (int) (textWidth * (0.35F + 0.65F * open));
        graphics.drawString(font, name, -stretched / 2, -4,
                argb(envelope, 0xFF, 0xEC, 0xB4), true);
        graphics.pose().popPose();

        // 朱印：符名右侧的一枚朱砂方印（落款 + 钤印的版式）。
        int sealSize = 10;
        int sealX = centerX + (textWidth * scale) / 2 + 6;
        int sealY = centerY - sealSize;
        graphics.fill(sealX, sealY, sealX + sealSize, sealY + sealSize, argb(envelope * 0.85F, 0xC8, 0x1E, 0x1E));
        graphics.drawString(font, "符", sealX, sealY - 2, argb(envelope, 0xFF, 0xFF, 0xFF), false);
    }

    /**
     * 加法混合的旋转法阵贴图。
     *
     * <p>走 {@code POSITION_TEX_COLOR} 四边形：原版 {@code blit} 只有位置纹理顶点，
     * 没有颜色属性，染不了色也加不了混合（见 {@code TouhouBossBarRenderer#tintedBlit} 的同一坑）。
     */
    private static void additiveMagicCircle(GuiGraphics graphics, int centerX, int centerY,
                                            int size, float angleDegrees, float envelope) {
        RenderSystem.setShaderTexture(0, MAGIC_CIRCLE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angleDegrees));
        Matrix4f pose = graphics.pose().last().pose();
        float half = size / 2.0F;
        int alpha = (int) Mth.clamp(envelope * 190.0F, 0.0F, 255.0F);
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(pose, -half, -half, 0.0F).setUv(0.0F, 0.0F).setColor(255, 226, 160, alpha);
        buffer.addVertex(pose, half, -half, 0.0F).setUv(1.0F, 0.0F).setColor(255, 226, 160, alpha);
        buffer.addVertex(pose, half, half, 0.0F).setUv(1.0F, 1.0F).setColor(255, 226, 160, alpha);
        buffer.addVertex(pose, -half, half, 0.0F).setUv(0.0F, 1.0F).setColor(255, 226, 160, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        graphics.pose().popPose();
        RenderSystem.disableBlend();
    }

    private static int argb(float alpha, int r, int g, int b) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
