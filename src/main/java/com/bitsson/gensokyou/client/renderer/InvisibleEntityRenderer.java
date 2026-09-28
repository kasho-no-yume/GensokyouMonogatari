package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 什么都不画的实体渲染器。
 *
 * <p>给「存在但不可见」的实体用：编队装置（rig）必须被客户端追踪并持有同步数据，
 * 却<b>不该</b>有任何外观——它是「一批弹共享的环绕中心」，画出来只会让人误以为
 * 那儿有东西可打。
 *
 * <p><b>为什么必须注册而不是干脆不注册</b>：不注册的话
 * {@code EntityRenderDispatcher} 找不到渲染器，会在日志里打一条
 * "Missing entity renderer" 警告。日志里凭空多一条与本 mod 无关的警告，
 * 会让真正的注册错误淹没在噪声里。
 *
 * <p><b>影子也要显式关掉</b>：空实现只挡掉了本体，{@code EntityRenderDispatcher}
 * 仍会按 {@link #getShadowRadius} 在地上画一团黑影。一个「什么都没有」的实体
 * 却在地上留个影子，看起来就像渲染出了 bug。
 */
public class InvisibleEntityRenderer<T extends Entity> extends EntityRenderer<T> {

    public InvisibleEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // 刻意为空。
    }

    /**
     * 本方法在 1.21.1 是抽象的，编译期强制实现。
     *
     * <p>由于 {@link #render} 根本不走绘制流程，这里的返回值永远不会被采样；
     * 仍然返回「丢失贴图」是为了让任何间接取用它的路径都拿到一个诚实的值，
     * 而不是某个真实贴图——真被画出来时那才叫一眼可辨的错。
     */
    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return MissingTextureAtlasSprite.getLocation();
    }
}
