package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 星银镐的「mod 原矿追加掉落」。
 *
 * <p><b>为什么用 {@link BlockEvent.BreakEvent} 而不是覆写 {@code mineBlock}</b>：
 * {@code BreakEvent} 是"玩家破坏"这一语义的权威信号，且在精准采集 / 时运判定之后触发，
 * 能拿到最终掉落表；追加掉落只需在这里补一个物品，不替换原版掉落表，
 * 因此不会被时运或精准的既有流程干扰。
 *
 * <p><b>自带精准采集走另一条路</b>：由 {@link GensokyouPickaxeItem#mineBlock} 在
 * 掉落生成前拦截——那里能拿到完整上下文（包括要掉的方块本体与玩家实体），
 * 比在事件侧清空掉落表更可控。
 *
 * <p>只限镐：星银斧 / 锹 / 锄不参与，追加掉落是"采矿特技"而不是通用加成。
 */
@EventBusSubscriber(modid = "gensokyou")
public final class GensokyouToolEvents {

    private GensokyouToolEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }
        ItemStack tool = event.getPlayer().getMainHandItem();
        if (!isStarSilverPickaxe(tool)) {
            return;
        }
        BlockState state = event.getState();
        if (state == null || !isGensokyouOre(state)) {
            return;
        }
        double chance = GensokyouConfig.STAR_SILVER_EXTRA_ORE_CHANCE.get();
        if (chance <= 0.0D || event.getPlayer().getRandom().nextDouble() >= chance) {
            return;
        }
        // 追加掉 1 个原矿方块本身（不是材料）：落点取方块位，让玩家看清来源
        Block.popResource(event.getPlayer().level(), event.getPos(),
                new ItemStack(state.getBlock()));
    }

    private static boolean isStarSilverPickaxe(ItemStack tool) {
        return tool != null && !tool.isEmpty()
                && tool.is(ModItems.STAR_SILVER_PICKAXE.get());
    }

    private static boolean isGensokyouOre(BlockState state) {
        return state.is(ModBlocks.CINNABAR.get())
                || state.is(ModBlocks.SPIRIT_IRON_ORE.get())
                || state.is(ModBlocks.STAR_SILVER_ORE.get())
                || state.is(ModBlocks.ONI_STONE.get());
    }

    /** 供调试读取：追加掉落当前是否启用及其概率。 */
    public static double extraOreChance() {
        return GensokyouConfig.STAR_SILVER_EXTRA_ORE_CHANCE.get();
    }
}
