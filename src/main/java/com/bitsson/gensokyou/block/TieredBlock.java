package com.bitsson.gensokyou.block;

import net.minecraft.world.level.block.Block;

/** 携带品阶的普通方块（仪式石族）。品阶由注册身份静态确定。 */
public class TieredBlock extends Block {

    private final int tier;

    public TieredBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return this.tier;
    }
}
