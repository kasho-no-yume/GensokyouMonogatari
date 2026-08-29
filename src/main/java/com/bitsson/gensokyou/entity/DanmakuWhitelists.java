package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * 预置弹幕白名单：其中的实体类型不会互相伤害，也不阻挡弹幕。
 * 弹幕的发射者始终免疫，无需写入白名单。
 */
public final class DanmakuWhitelists {

    /** 妖精系弹幕白名单：妖精之间不误伤。 */
    public static final Set<EntityType<?>> FAIRY = Set.of(
            ModEntityTypes.FAIRY.get(),
            ModEntityTypes.BIG_FAIRY.get()
    );

    private DanmakuWhitelists() {
    }
}
