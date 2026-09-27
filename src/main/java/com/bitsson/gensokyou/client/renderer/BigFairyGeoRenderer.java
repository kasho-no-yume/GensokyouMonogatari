package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.model.BigFairyGeoModel;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.BigFairyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 大妖精的 GeckoLib 渲染器。
 *
 * <p>缩放按小妖精的先例推：{@code lesser_fairy.geo.json} 自然高 25.3 单位
 * （1 格 = 16 单位 ⇒ 1.58 格），{@code SCALE=0.63} 把它收到约 1 格。
 * {@code greater_fairy.geo.json} 自然高 <b>29.2 单位 = 1.82 格</b>，
 * 故要落到与 0.7×1.6 碰撞箱相称的身高，取 {@code 0.88}。
 *
 * <p>（先前误按 body pivot 反推成 0.105，等于把模型缩到 0.19 格——直接看不见。）
 */
public class BigFairyGeoRenderer extends GeoEntityRenderer<BigFairyEntity> {
    /** 29.2 单位 × 0.88 ≈ 1.6 格，对齐 0.7×1.6 碰撞箱。 */
    private static final float SCALE = 0.88F;
    private static final double FULL_PITCH_SPEED = 0.15D;

    public BigFairyGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new BigFairyGeoModel());
        this.withScale(SCALE);
    }

    @Override
    protected void applyRotations(BigFairyEntity animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
        double dx = animatable.getX() - animatable.xo;
        double dz = animatable.getZ() - animatable.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        float t = (float) Mth.clamp(speed / FULL_PITCH_SPEED, 0.0D, 1.0D);
        if (t > 0.0F) {
            float pitch = (float) GensokyouConfig.FAIRY_FLY_PITCH_DEGREES.get().doubleValue() * t;
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }
    }
}
