package com.bitsson.gensokyou.mixin.client;

import com.bitsson.gensokyou.client.FoxPossessionClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 被狐火附身的生物走原版"着火"渲染路径：{@code EntityRenderDispatcher.render} 只在
 * {@code entity.displayFireAnimation()} 为真时调用原版火焰绘制。对正被狐火附身的生物
 * （由 {@link FoxPossessionClient} 从狐火实体的同步状态判定）返回 true，但<b>不</b>调用
 * {@code setOnFire}——所以没有真实火焰伤害；颜色/贴图由 {@link SoulBurnFlameMixin} 改成蓝焰。
 */
@Mixin(Entity.class)
public abstract class SoulBurnFireAnimationMixin {

    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    private void gensokyou$soulBurnDisplay(CallbackInfoReturnable<Boolean> cir) {
        if (FoxPossessionClient.isSoulBurning((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
