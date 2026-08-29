package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 旧版弹幕实体，现作为存档兼容的薄壳重定向到 {@link SphereDanmaku}。
 *
 * <p>保留 {@code gensokyou:danmaku} 实体 id 与 "Damage" NBT 键，
 * 使旧存档中在飞的弹幕能正常加载并继承球型弹幕的全部行为
 * （匀速、白名单、发光、60 秒寿命）。新代码请勿再使用本类。
 *
 * @deprecated 使用 {@link SphereDanmaku} 或其他三种弹幕类型。
 */
@Deprecated
public class DanmakuProjectile extends SphereDanmaku {

    public DanmakuProjectile(EntityType<? extends DanmakuProjectile> type, Level level) {
        super(type, level);
    }

    public DanmakuProjectile(Level level, LivingEntity owner, float damage) {
        super(level, owner, damage, 0xFFFFFF, 0.4F, java.util.Set.of());
    }

    /** 旧工厂方法，保留以兼容历史调用点。 */
    public static DanmakuProjectile basic(Level level, LivingEntity owner) {
        return new DanmakuProjectile(level, owner,
                GensokyouConfig.DANMAKU_BASE_DAMAGE.get().floatValue());
    }
}
