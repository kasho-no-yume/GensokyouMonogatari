package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.Audio;
import com.bitsson.gensokyou.audio.WeaponShotCue;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 玩家武器开火的客户端声音。
 *
 * <p>服务端在 {@code WeaponFiring} 里把 cue 写进弹实体的同步字节；客户端在实体加入世界时
 * 读一次并交给 {@link Audio}。散弹/扇射只由第一颗弹携带 cue，因此这里天然是「一次扳机一声」。
 *
 * <p>激光不在本类播放：它需要等延迟结束、进入 ACTIVE 后才响，并由激光实体自己维持循环。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class WeaponShotSounds {

    private WeaponShotSounds() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof AbstractDanmakuProjectile projectile)) {
            return;
        }
        WeaponShotCue cue = projectile.shotCue();
        if (cue == null || cue.laser()) {
            return;
        }
        Audio.playAt(cue.cue(), projectile.position());
    }
}
