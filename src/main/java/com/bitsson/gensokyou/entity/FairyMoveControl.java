package com.bitsson.gensokyou.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * 妖精专用飞行移动控制。
 *
 * <p>原版 {@code FlyingMoveControl} 只对 {@code yya} 生效，而 {@code FlyingMob.travel}
 * 只读取 {@code (xxa, yya, zza)} 输入向量、不读 {@code speed}，二者组合下无法产生水平位移。
 * 参考 Ghast 的做法：直接朝目标点累加 {@code deltaMovement}，由 {@code FlyingMob.travel}
 * 负责积分与阻力（含碰撞解算），从而得到稳定的三维飞行。
 */
public class FairyMoveControl extends MoveControl {

    /** 加速度相对 speedModifier 的系数；越小越飘。 */
    private static final double ACCEL_FACTOR = 0.0533D;

    /** 到目标点的减速半径（格）。 */
    private static final double SLOW_RADIUS = 2.0D;

    public FairyMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (this.operation != MoveControl.Operation.MOVE_TO) {
            return;
        }

        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - this.mob.getY();
        double dz = this.wantedZ - this.mob.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist < 0.05D) {
            this.operation = MoveControl.Operation.WAIT;
            return;
        }

        double accel = this.speedModifier * ACCEL_FACTOR * Math.min(1.0D, dist / SLOW_RADIUS);
        Vec3 dir = new Vec3(dx, dy, dz).scale(1.0D / dist);
        this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(dir.scale(accel)));

        // 朝运动方向转身（GeckoLib 用 yBodyRot 渲染，必须显式写入）。
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        this.mob.setYRot(yaw);
        this.mob.setYHeadRot(yaw);
        this.mob.yBodyRot = yaw;
    }
}
