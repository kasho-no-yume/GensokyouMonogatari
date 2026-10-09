package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.config.GensokyouConfig;
import java.util.function.Supplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * 灵铁 / 星银的工具材质，数值与修复素材分离定义。
 *
 * <p><b>为什么不用枚举</b>：需要给每个档位挂一个「常见方块零耐久」白名单开关
 * （见 {@link #isCommonBlockDurabilityFree}），enum 常量做不到按档位差异化特技。
 *
 * <p>挖掘速度 / 攻击加成 / 采集等级照 {@code Tier} 契约；修复素材走对应成品金属。
 */
public class GensokyouTier implements Tier {

    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final TagKey<Block> incorrectBlocks;
    private final int enchantmentValue;
    private final Supplier<Ingredient> repairIngredient;
    private final boolean silkTouch;
    private final boolean extraOreDrop;

    public GensokyouTier(int uses, float speed, float attackDamageBonus,
                         TagKey<Block> incorrectBlocks, int enchantmentValue,
                         Supplier<Ingredient> repairIngredient,
                         boolean silkTouch, boolean extraOreDrop) {
        this.uses = uses;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.incorrectBlocks = incorrectBlocks;
        this.enchantmentValue = enchantmentValue;
        this.repairIngredient = repairIngredient;
        this.silkTouch = silkTouch;
        this.extraOreDrop = extraOreDrop;
    }

    // ---- Tier 契约 ----

    @Override
    public int getUses() {
        return uses;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return attackDamageBonus;
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return incorrectBlocks;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }

    // ---- 特色特技 ----

    /** 星银工具自带精准采集：掉落方块物品本身，绕开附魔通道。 */
    public boolean hasSilkTouch() {
        return silkTouch;
    }

    /** 星银工具挖 mod 原矿时概率追加掉落 1 个原矿方块。 */
    public boolean hasExtraOreDrop() {
        return extraOreDrop;
    }

    /**
     * 灵铁工具的「常见方块零耐久」。判定用的白名单在
     * {@link GensokyouTools#isDurabilityFree(BlockState)}，这里只暴露开关本身。
     */
    public boolean isCommonBlockDurabilityFree() {
        return false;
    }
}
