package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualExtraSlots;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 思兼神封（`gensokyou:omoikane_circle`，1/2/3 阶）：附魔打造仪式。
 *
 * <p><b>玩法</b>：核心额外槽放可附魔装备（祭品台放附魔书，升序折叠合并入装备，
 * 全阶无视冲突）或普通书（祭品台放青金石块，每块换一个随机词条）。点一次
 * 「开始打造」跑一个批次，<b>足额预检</b>——灵力不足整批失败、零消耗。
 * 数值口径全在 {@link OmoikaneScaling}。
 *
 * <p><b>一次性而非启停</b>：{@code handlesStartViaUiAction} 为真，通用 start/stop 让位；
 * pattern {@code toggleable:false}。红石上升沿由框架代管触发（缺省真），故玩家不在场
 * 时也能起批。
 *
 * <p><b>缓存常驻</b>：空闲态容量不为 0，本仪式因此可被万象共鸣选为下游目标。
 */
public class OmoikaneBehavior implements RitualBehavior, RitualExtraSlots {

    /** 注入按钮：唯一操作「开始打造」。 */
    public static final int ACTION_FORGE = 0;

    private static final String KEY_BUTTON = "gui.gensokyou.ritual.omoikane.start";

    @Override
    public boolean handlesStartViaUiAction() {
        return true; // 批次型：通用启停通道让位，走一次性按钮
    }

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    RitualCoreBlockEntity core) {
        return List.of(new UiAction(ACTION_FORGE, KEY_BUTTON));
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core, ServerPlayer player,
                                        int actionId) {
        if (actionId != ACTION_FORGE) {
            return InteractionResult.PASS;
        }
        // player 可能为 null（红石代管触发），本操作不依赖玩家身份
        return OmoikaneForging.forge(level, corePos, match, core).producedAnything()
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    // ---- 灵力 ----

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return OmoikaneScaling.inRateOf(match.level());
    }

    /**
     * 非发电仪式：走默认的「电池 → 缓存」，每 tick 把槽内灵力核心按其
     * {@code fillRatePerSecond} 补入缓存。不可省——非托管核心的 {@code getStored()}
     * 只反映缓存，不灌注就成了只能靠路由充能的死仪式（少名同款论证）。
     */
    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        core.tickBatteryToCacheFill();
    }

    // ---- 额外槽（装备 / 普通书） ----

    @Override
    public int slotCount() {
        return 1;
    }

    @Override
    public boolean isSlotValid(int slot, ItemStack stack) {
        if (slot != OmoikaneForging.GEAR_SLOT || stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.BOOK)) {
            return true; // 普通书 → 书模式
        }
        // 附魔书拒收（书只作祭品上台，不进核心槽）；其余按可附魔装备口径
        return !stack.is(Items.ENCHANTED_BOOK) && stack.isEnchantable();
    }

    @Override
    public String labelKey() {
        return "gui.gensokyou.ritual.omoikane.slot";
    }

    // ---- GUI ----

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        return OmoikaneForging.uiInfo(level, corePos, match, core);
    }
}
