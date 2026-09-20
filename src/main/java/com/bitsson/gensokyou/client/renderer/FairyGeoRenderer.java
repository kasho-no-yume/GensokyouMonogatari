package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.model.FairyGeoModel;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.FairyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 小妖精的 GeckoLib 渲染器。
 *
 * <p>模型自然高度 25.28 单位（1.58 格），缩放到约 1 格以对齐 0.45×1.0 的碰撞箱。
 * 飞行前进时给整个模型加一个向前的俯仰（前倾），速度越快倾角越大。
 */
public class FairyGeoRenderer extends GeoEntityRenderer<FairyEntity> {
    private static final float SCALE = 0.63F;

    /** 达到满前倾所需的水平速度（格/tick）。 */
    private static final double FULL_PITCH_SPEED = 0.15D;

    public FairyGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new FairyGeoModel());
        this.withScale(SCALE);
    }

    @Override
    protected void applyRotations(FairyEntity animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);

        double dx = animatable.getX() - animatable.xo;
        double dz = animatable.getZ() - animatable.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        float t = (float) Mth.clamp(speed / FULL_PITCH_SPEED, 0.0D, 1.0D);
        if (t > 0.0F) {
            float pitch = (float) GensokyouConfig.FAIRY_FLY_PITCH_DEGREES.get().doubleValue() * t;
            // 模型面朝 -Z：绕 X 轴负向旋转 = 机头下压（前倾）。
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }
    }
}
