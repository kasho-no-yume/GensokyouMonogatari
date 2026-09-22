package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.model.TestBossGeoModel;
import com.bitsson.gensokyou.entity.BalanceTestBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 数值测试 BOSS 渲染：复用妖精模型与动画，放大以便与普通妖精区分（占位，不做美术打磨）。
 */
public class TestBossGeoRenderer extends GeoEntityRenderer<BalanceTestBossEntity> {
    private static final float SCALE = 1.0F;
    private static final double FULL_PITCH_SPEED = 0.15D;

    public TestBossGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new TestBossGeoModel());
        this.withScale(SCALE);
    }

    @Override
    protected void applyRotations(BalanceTestBossEntity animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
        double dx = animatable.getX() - animatable.xo;
        double dz = animatable.getZ() - animatable.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        float t = (float) Mth.clamp(speed / FULL_PITCH_SPEED, 0.0D, 1.0D);
        if (t > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-40.0F * t));
        }
    }
}
