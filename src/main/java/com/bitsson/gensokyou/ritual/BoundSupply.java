package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.item.BuilderBind;
import com.bitsson.gensokyou.ritual.behavior.WujinzangStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/**
 * 绑定无尽藏核心的读取侧契约（构建器补料与计数共用）。
 *
 * <p>闸门 = 同维度 · 已成型无尽藏 · 已启动，与无尽藏终端"停止态仓储锁定"语义一致
 * （{@code WujinzangTerminalMenu.storageAvailable}）。本类刻意不复用终端菜单。
 */
public final class BoundSupply {

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_UNFORMED = 1;
    public static final int STATUS_STOPPED = 2;
    public static final int STATUS_OTHER_DIMENSION = 3;

    private BoundSupply() {
    }

    /** 绑定核心是否为当前维度、当前成型且已启动的无尽藏。 */
    public static boolean available(ServerLevel level, @Nullable BuilderBind bind) {
        return status(level, bind) == STATUS_AVAILABLE;
    }

    /** 闸门状态：0=可用，1=未成形/非同仪式，2=已停止，3=异维度。 */
    public static int status(ServerLevel level, @Nullable BuilderBind bind) {
        if (bind == null) {
            return STATUS_UNFORMED;
        }
        if (!level.dimension().location().equals(bind.dimension())) {
            return STATUS_OTHER_DIMENSION;
        }
        return statusOf(core(level, bind));
    }

    private static int statusOf(@Nullable RitualCoreBlockEntity core) {
        if (core == null) {
            return STATUS_UNFORMED;
        }
        RitualMatch match = core.activeMatch();
        if (match == null || !RitualBehaviors.WUJINZANG.equals(match.patternId())) {
            return STATUS_UNFORMED;
        }
        return core.isEnabled() ? STATUS_AVAILABLE : STATUS_STOPPED;
    }

    /** 同维度下的绑定核心方块实体（异维度/未加载返回 null）。 */
    @Nullable
    public static RitualCoreBlockEntity core(ServerLevel level, @Nullable BuilderBind bind) {
        if (bind == null || !level.dimension().location().equals(bind.dimension())) {
            return null;
        }
        return level.getBlockEntity(bind.pos()) instanceof RitualCoreBlockEntity c ? c : null;
    }

    /** 可用状态下的无尽藏匹配；否则 null。 */
    @Nullable
    public static RitualMatch wujinzangMatch(ServerLevel level, @Nullable BuilderBind bind) {
        if (!available(level, bind)) {
            return null;
        }
        RitualCoreBlockEntity core = core(level, bind);
        return core == null ? null : core.activeMatch();
    }

    /**
     * 从绑定仓储精确取出一个 {@code block} 的物品（真实消耗）。
     * 闸门不满足、方块无物品形态或仓储无货时返回 false。
     */
    public static boolean tryConsumeOne(ServerLevel level, @Nullable BuilderBind bind, Block block) {
        RitualMatch match = wujinzangMatch(level, bind);
        if (match == null || block.asItem() == net.minecraft.world.item.Items.AIR) {
            return false;
        }
        ItemStack removed = WujinzangStorage.extract(level, match, new ItemStack(block.asItem()), 1);
        return !removed.isEmpty();
    }
}
