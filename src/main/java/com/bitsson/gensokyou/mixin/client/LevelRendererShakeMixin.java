package com.bitsson.gensokyou.mixin.client;

import com.bitsson.gensokyou.client.ShatterScreenFx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 结界崩解的<b>真·镜头晃动</b>：在 {@code LevelRenderer.renderLevel} 原地旋转
 * {@code frustumMatrix}。
 *
 * <h2>为什么必须用 mixin</h2>
 * 三条 mixin-free 路径都试过、都不通（细节与源码行号见
 * {@code ShatterScreenFx#shakeDegrees} 的 javadoc）：
 * <ul>
 *   <li>{@code RenderLevelStageEvent.AFTER_SKY} 的 poseStack 是 <b>null</b>；</li>
 *   <li>{@code AFTER_ENTITIES} / {@code AFTER_BLOCK_ENTITIES} 都在<b>地形之后</b>，
 *       只能让世界<b>一半</b>晃动；</li>
 *   <li>改 {@code Camera} 对象来不及——{@code GameRenderer.renderLevel} 在调用
 *       {@code LevelRenderer.renderLevel} <b>之前</b>就已把 {@code camera.rotation()}
 *       的<b>逆旋转</b>烘进 {@code frustumMatrix}。</li>
 * </ul>
 *
 * <h2>为什么右乘小角度就等于"转相机"</h2>
 * 调用方构造 {@code frustumMatrix} 的方式（{@code GameRenderer.renderLevel}）是：
 * <pre>{@code
 * Quaternionf q = camera.rotation().conjugate(new Quaternionf());
 * Matrix4f frustumMatrix = new Matrix4f().rotation(q);
 * levelRenderer.renderLevel(..., frustumMatrix, projectionMatrix);
 * }</pre>
 * 它是<b>纯旋转、没有平移</b>，即"世界 → 视图"的完整相机位姿逆变换。
 * 于是：视图空间里<b>相机就在原点</b>，在它右边乘一个绕相机自身轴的小旋转
 * （{@code this = this * R}，即 R 先作用）＝ 相机自身 roll/pitch，
 * <b>不是</b>绕世界原点的公转。
 *
 * <h2>覆盖范围</h2>
 * {@code frustumMatrix} 是天空（{@code renderSky}）与地形（{@code renderSectionLayer}）的
 * 变换；实体/方块实体/粒子走 {@code RenderSystem.getModelViewStack()}，而它在
 * {@code renderLevel} 内部被 {@code mul(frustumMatrix)} 同步乘上同一份矩阵。
 * 所以天空/地形/实体/粒子/天气<b>一起晃</b>，而 HUD 与手持物（在 GUI 阶段单独渲染）
 * <b>不晃</b>——这正是真实镜头晃动的行为。
 *
 * <p>强度由 {@link ShatterScreenFx#shakeDegrees()} 提供（按玩家到最近一扇正在崩解的门的
 * 距离衰减，100 格外为 0），本类只负责把它写进矩阵。
 */
@Mixin(value = LevelRenderer.class)
public abstract class LevelRendererShakeMixin {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void gensokyou$applyCameraShake(DeltaTracker deltaTracker, boolean renderBlockOutline,
                                           Camera camera, GameRenderer gameRenderer,
                                           LightTexture lightTexture,
                                           Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                           CallbackInfo ci) {
        float[] shake = ShatterScreenFx.shakeDegrees();
        float roll = shake[0];
        float pitch = shake[1];
        if (roll == 0.0F && pitch == 0.0F) {
            return;
        }
        // 右乘（this = this * R）：R 作用在相机自身的轴上 ⇒ 真正的 roll/pitch
        if (pitch != 0.0F) {
            frustumMatrix.rotateX(pitch * DEG_TO_RAD);
        }
        if (roll != 0.0F) {
            frustumMatrix.rotateZ(roll * DEG_TO_RAD);
        }
    }
}
