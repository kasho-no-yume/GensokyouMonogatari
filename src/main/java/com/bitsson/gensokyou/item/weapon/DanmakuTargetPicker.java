package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 灵符核目标拾取：准星锥内取「视角偏角最小」的实体（视觉上最贴准星者优先），
 * 而非射线最先命中者——避免贴脸实体擦边挡住真正瞄着的远处目标。
 * 客户端目标准星与服务端发射共用本方法，保证两端口径一致。
 */
public final class DanmakuTargetPicker {

    /** 允许偏角判定时给命中箱的额外宽容（格）。 */
    private static final double ANGULAR_SLACK = 0.5D;

    /** 命中锥宽容倍率：乘在整个允许偏角上（1.0 = 贴着命中箱瞄）。 */
    private static final double FORGIVENESS = 2.0D;

    private DanmakuTargetPicker() {
    }

    @Nullable
    public static Entity pick(Player player) {
        double range = GensokyouConfig.WEAPON_TALISMAN_PICK_RANGE.get();
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(range + 4.0D)).inflate(1.0D);

        Entity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, searchBox,
                candidate -> candidate instanceof LivingEntity living && living.isAlive()
                        && !(candidate instanceof Player) && !candidate.isSpectator())) {
            Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
            Vec3 toEntity = center.subtract(from);
            double distance = toEntity.length();
            if (distance > range || distance < 1.0E-4D) {
                continue;
            }
            double angle = Math.acos(Mth.clamp(look.dot(toEntity) / distance, -1.0D, 1.0D));
            // 实体允许偏角 = 视角半径（宽/2 + 宽容格数）× 宽容倍率；越大越容易选中
            double allowance = FORGIVENESS
                    * Math.atan2(entity.getBbWidth() * 0.5D + ANGULAR_SLACK, distance);
            if (angle > allowance || angle >= bestAngle) {
                continue;
            }
            bestAngle = angle;
            best = entity;
        }
        return best;
    }
}
