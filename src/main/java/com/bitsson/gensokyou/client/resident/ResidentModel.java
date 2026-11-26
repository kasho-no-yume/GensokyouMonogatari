package com.bitsson.gensokyou.client.resident;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.List;

/** Shared villager-derived geometry; deliberately neither an EntityModel nor an EntityRenderer. */
public final class ResidentModel {
    private final ModelPart root;
    private final List<ModelPart> parts;
    private final ModelPart head, body, arms, hands, rightLeg, leftLeg;
    private int alpha = 255;

    public ResidentModel() {
        root = createLayer().bakeRoot();
        parts = root.getAllParts().toList();
        head = root.getChild("head"); body = root.getChild("body");
        arms = root.getChild("arms"); hands = arms.getChild("hands");
        rightLeg = root.getChild("right_leg"); leftLeg = root.getChild("left_leg");
    }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = VillagerModel.createBodyModel();
        PartDefinition root = mesh.getRoot();
        // Keep the folded-arm silhouette, but separate hands from sleeves for independent skin tint.
        PartDefinition arms = root.addOrReplaceChild("arms", CubeListBuilder.create().texOffs(44, 22)
                        .addBox(-8, -2, -2, 4, 8, 4).texOffs(44, 22).mirror()
                        .addBox(4, -2, -2, 4, 8, 4),
                PartPose.offsetAndRotation(0, 3, -1, -0.75F, 0, 0));
        arms.addOrReplaceChild("hands", CubeListBuilder.create().texOffs(96, 96)
                .addBox(-4, 2, -2, 8, 4, 4), PartPose.ZERO);

        root.addOrReplaceChild("coat", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-4.5F, 0, -3.5F, 9, 12, 7, new CubeDeformation(0.1F)), PartPose.ZERO);
        root.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-4.5F, 0, -3.5F, 9, 18, 7, new CubeDeformation(0.1F)), PartPose.ZERO);
        root.addOrReplaceChild("apron", CubeListBuilder.create().texOffs(96, 0)
                .addBox(-3.5F, 4, -3.8F, 7, 13, 1), PartPose.ZERO);
        root.addOrReplaceChild("sash", CubeListBuilder.create().texOffs(96, 64)
                .addBox(-4.5F, 8, -3.5F, 9, 2, 7, new CubeDeformation(0.25F)), PartPose.ZERO);

        root.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(64, 32)
                .addBox(-4, -10, -4, 8, 3, 8, new CubeDeformation(0.22F))
                .texOffs(64, 32).addBox(-4, -7, 2, 8, 5, 2, new CubeDeformation(0.15F)), PartPose.ZERO);
        root.addOrReplaceChild("topknot", CubeListBuilder.create().texOffs(64, 32)
                .addBox(-1.5F, -12.5F, 0.5F, 3, 3, 3), PartPose.ZERO);
        root.addOrReplaceChild("headscarf", CubeListBuilder.create().texOffs(64, 32)
                .addBox(-4, -10, -4, 8, 4, 8, new CubeDeformation(0.35F))
                .texOffs(64, 32).addBox(3, -7, 3, 2, 5, 2), PartPose.ZERO);
        root.addOrReplaceChild("straw_hat", CubeListBuilder.create().texOffs(64, 64)
                .addBox(-7, -9.5F, -7, 14, 1, 14)
                .texOffs(64, 64).addBox(-4, -12.5F, -4, 8, 3, 8), PartPose.ZERO);
        root.addOrReplaceChild("satchel", CubeListBuilder.create().texOffs(96, 32)
                .addBox(4, 7, -1, 3, 5, 4), PartPose.ZERO);
        root.addOrReplaceChild("bundle", CubeListBuilder.create().texOffs(96, 32)
                .addBox(-4, 3, 3.5F, 8, 8, 5), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    public void render(PoseStack pose, VertexConsumer out, int light, ResidentAppearance look,
                        ResidentWalker.Frame frame, double age, boolean details) {
        render(pose, out, light, look, frame, age, details, 1F);
    }

    public void render(PoseStack pose, VertexConsumer out, int light, ResidentAppearance look,
                       ResidentWalker.Frame frame, double age, boolean details, float opacity) {
        alpha = Math.clamp(Math.round(opacity * 255F), 0, 255);
        // Geometry is reused for every person. resetPose alone does not reset visibility.
        for (ModelPart part : parts) {
            part.resetPose();
            part.visible = true;
            part.skipDraw = false;
        }
        head.getChild("hat").visible = false;
        body.getChild("jacket").visible = false;
        hands.visible = false;
        double phase = (age + look.phaseTicks()) * 0.045;
        head.yRot = (float) Math.sin(phase * 0.37) * 0.12F;
        head.xRot = (float) Math.sin(phase * 0.23) * 0.035F;
        arms.zRot = (float) Math.sin(phase) * 0.012F;
        float swing = frame.walking() ? (float) Math.cos(frame.walked() * 9) * 0.6F : 0;
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;

        head.render(pose, out, light, OverlayTexture.NO_OVERLAY, tint(look.skin()));
        body.render(pose, out, light, OverlayTexture.NO_OVERLAY, tint(look.cloth()));
        arms.render(pose, out, light, OverlayTexture.NO_OVERLAY, tint(look.cloth()));
        pose.pushPose();
        arms.translateAndRotate(pose);
        hands.visible = true;
        hands.render(pose, out, light, OverlayTexture.NO_OVERLAY, tint(look.skin()));
        pose.popPose();
        rightLeg.render(pose, out, light, OverlayTexture.NO_OVERLAY, tint(look.sash()));
        leftLeg.render(pose, out, light, OverlayTexture.NO_OVERLAY, tint(look.sash()));

        draw(look.outfit() == ResidentAppearance.Outfit.ROBE ? "robe" : "coat", pose, out, light, look.cloth());
        draw("sash", pose, out, light, look.sash());
        if (look.outfit() == ResidentAppearance.Outfit.APRON) draw("apron", pose, out, light, 0xFFD5C9AD);

        // Keep the main head silhouette at distance; only small accessories use the detail cutoff.
        pose.pushPose();
        head.translateAndRotate(pose);
        switch (look.headStyle()) {
            case SHORT_HAIR -> draw("hair", pose, out, light, look.hair());
            case TOPKNOT -> {
                draw("hair", pose, out, light, look.hair());
                if (details) draw("topknot", pose, out, light, look.hair());
            }
            case HEADSCARF -> draw("headscarf", pose, out, light, look.sash());
            case STRAW_HAT -> draw("straw_hat", pose, out, light, 0xFFD2B575);
        }
        pose.popPose();
        if (!details) return;
        switch (look.accessory()) {
            case NONE -> { }
            case SATCHEL -> draw("satchel", pose, out, light, 0xFF96704B);
            case BUNDLE -> draw("bundle", pose, out, light, look.sash());
        }
    }

    private void draw(String part, PoseStack pose, VertexConsumer out, int light, int color) {
        root.getChild(part).render(pose, out, light, OverlayTexture.NO_OVERLAY, tint(color));
    }
    private int tint(int color) { return (color & 0xFFFFFF) | alpha << 24; }
}
