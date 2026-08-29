package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.weapon.BulletCoreItem;
import com.bitsson.gensokyou.item.weapon.DanmakuTargetPicker;
import com.bitsson.gensokyou.item.weapon.DanmakuWeaponItem;
import com.bitsson.gensokyou.item.weapon.WeaponSlots;
import com.bitsson.gensokyou.item.weapon.WeaponSlotsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Vector4f;

/**
 * 灵符核目标标记（括号样式）：用本帧真实的视图/投影矩阵把目标中心
 * 投影到屏幕坐标，在其位置绘制 2D 角括号，指示当前瞄准的实体。
 * 矩阵计算挂在关卡渲染阶段，绘制挂在准星 GUI 层之后（同帧零延迟）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class TargetMarkerRenderer {

    private static final int COLOR = 0xFFB7E8FF;
    private static final int HALF = 11;
    private static final int ARM = 4;

    /** 投影结果（GUI 缩放坐标）；每帧由关卡渲染阶段写入。 */
    private static boolean projected;
    private static int screenX;
    private static int screenY;

    private TargetMarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        projected = false;
        Entity target = currentTarget();
        if (target == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 cameraPos = event.getCamera().getPosition();
        Vector4f view = new Vector4f(
                (float) (center.x - cameraPos.x),
                (float) (center.y - cameraPos.y),
                (float) (center.z - cameraPos.z), 1.0F);
        event.getModelViewMatrix().transform(view);
        event.getProjectionMatrix().transform(view);
        if (view.w() <= 0.0F) {
            return;
        }
        float ndcX = view.x() / view.w();
        float ndcY = view.y() / view.w();
        // 帧缓冲 NDC → GUI 缩放坐标
        screenX = Math.round((ndcX * 0.5F + 0.5F) * minecraft.getWindow().getGuiScaledWidth());
        screenY = Math.round((1.0F - (ndcY * 0.5F + 0.5F)) * minecraft.getWindow().getGuiScaledHeight());
        projected = true;
    }

    @SubscribeEvent
    public static void onRenderCrosshair(RenderGuiLayerEvent.Post event) {
        if (event.getName() != VanillaGuiLayers.CROSSHAIR || !projected) {
            return;
        }
        drawCornerBrackets(event.getGuiGraphics(), screenX, screenY);
    }

    private static Entity currentTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof DanmakuWeaponItem)) {
            return null;
        }
        WeaponSlots slots = WeaponSlotsHelper.read(held);
        if (!(slots.slot1().getItem() instanceof BulletCoreItem core) || !core.pattern().isTalisman()) {
            return null;
        }
        return DanmakuTargetPicker.pick(player);
    }

    private static void drawCornerBrackets(GuiGraphics graphics, int cx, int cy) {
        int left = cx - HALF;
        int right = cx + HALF;
        int top = cy - HALF;
        int bottom = cy + HALF;
        // 左上
        graphics.fill(left, top, left + ARM, top + 1, COLOR);
        graphics.fill(left, top, left + 1, top + ARM, COLOR);
        // 右上
        graphics.fill(right - ARM, top, right, top + 1, COLOR);
        graphics.fill(right - 1, top, right, top + ARM, COLOR);
        // 左下
        graphics.fill(left, bottom - 1, left + ARM, bottom, COLOR);
        graphics.fill(left, bottom - ARM, left + 1, bottom, COLOR);
        // 右下
        graphics.fill(right - ARM, bottom - 1, right, bottom, COLOR);
        graphics.fill(right - 1, bottom - ARM, right, bottom, COLOR);
    }
}
