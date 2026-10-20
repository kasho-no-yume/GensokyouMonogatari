package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Set;

/**
 * 锥形弹幕（3D 建模的箭矢弹体）。
 *
 * <p>特性：尖端恒指速度方向、命中实体或方块即消失、<b>不产生尾迹</b>
 * ——10 发/秒的射速下尾迹会让同时在飞的弹幕实体数翻倍。
 *
 * <p><b>朝向</b>：由父类的 {@code updateRotationFromVelocity()} 每 tick 由速度写入
 * {@code yRot/xRot}，渲染器据此把尖端对准局部 +Z。速度为零时该方法直接返回，
 * 故静止时保持最后朝向（"静止时方向任意"天然满足）。
 *
 * <p><b>判定箱与视觉尺寸解耦</b>：视觉总宽 0.2 格（见 {@code ConeDanmakuRenderer} 的几何常量），
 * 判定箱为实体注册尺寸 0.32 格。二者是<b>各自独立的参数</b>，不是同一个值——
 * 共用同一个值会让细长弹体在 BOSS 面前"看着擦过却没伤害"。
 */
public class ConeDanmaku extends AbstractDanmakuProjectile {

    /** 弹体本色（东方矢型弹的蓝）。 */
    private static final int DEFAULT_COLOR = 0x3B6FD9;

    public ConeDanmaku(EntityType<? extends ConeDanmaku> type, Level level) {
        super(type, level);
    }

    public ConeDanmaku(Level level, LivingEntity owner, float damage, Set<EntityType<?>> whitelist) {
        super(ModEntityTypes.CONE_DANMAKU.get(), owner, level);
        this.damage = damage;
        this.setWhitelist(whitelist);
        this.setColor(DEFAULT_COLOR);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        Entity hit = result.getEntity();
        if (this.isWhitelisted(hit)) {
            return;
        }
        if (hit.isAlive()) {
            hit.hurt(ModDamageTypes.danmaku(this, this.getOwner()), this.damage);
        }
        this.discard(DanmakuBudget.RemovalCause.ENTITY);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level() instanceof ServerLevel) {
            this.discard(DanmakuBudget.RemovalCause.BLOCK);
        }
    }

    @Override
    public boolean hasGlowEffect() {
        // 东方弹幕普遍自带很强的亮度：锥弹是锐利的 3D 多面体，光靠顶点色不够亮，
        // 故渲染器另加一层放大的加法外发光（见 ConeDanmakuRenderer#GLOW_SCALE）。
        return true;
    }
}
