package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 额外槽位 NBT 往返的编码契约 —— 回归守卫。
 *
 * <p><b>为什么要有这个测试</b>：核心的额外槽数组恒为 {@code MAX_EXTRA_SLOTS = 4} 格，而
 * 实际声明的仪式少名只占 1 格、星移也只占 1 格。原来的写盘实现对<b>每一格</b>无条件调
 * {@link ItemStack#save}，而该方法对空栈**抛异常**（"Cannot encode empty ItemStack"），
 * 于是每次存盘必炸。后果远不止"存不下"：
 * <ul>
 *   <li>{@code getUpdateTag()} 与 {@code saveAdditional()} 同一条路径，它一抛，
 *       {@code sendBlockUpdated} 就发不出方块实体数据 → 客户端永远收不到渲染态
 *       → <b>所有特效静默消失</b>；</li>
 *   <li>区块存盘被跳过（LevelChunk 报 "It will not persist"），核心状态丢失。</li>
 * </ul>
 *
 * <p>修法是「空槽写空 {@link CompoundTag} 而非跳过」，好让下标与格位保持一一对应。
 * 本测试把这条编码契约钉死：空 CompoundTag 是合法的"空槽"编码，且
 * {@link ItemStack#parseOptional} 能把它还原成 {@link ItemStack#EMPTY}。
 * 后半段同时断言 save 对空栈确实会抛——哪天原版改了行为，这条提醒就得重写。
 */
class ExtraSlotNbtRoundTripTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static HolderLookup.Provider registries() {
        return net.minecraft.core.RegistryAccess.EMPTY;
    }

    /** 空槽 MUST NOT 走 {@code save}：它会抛，整条存盘链路（含渲染态下发）随之断掉。 */
    @Test
    void savingAnEmptyStackThrows() {
        assertThrows(IllegalStateException.class, () -> ItemStack.EMPTY.save(registries()),
                "若原版不再对空栈抛异常，本测试的修法前提需重新评估");
    }

    /** 空槽的合法编码是<b>空 CompoundTag</b>——它保住 ListTag 下标与格位的对应关系。 */
    @Test
    void emptyCompoundTagEncodesAnEmptySlot() {
        CompoundTag emptySlot = new CompoundTag();
        assertTrue(ItemStack.parseOptional(registries(), emptySlot).isEmpty(),
                "空 CompoundTag 必须被还原成 EMPTY，否则读回会把空槽填成物品");
    }

    /** 非空槽照常走 save，且往返后物品与数量一致。 */
    @Test
    void nonEmptyStackRoundTrips() {
        ItemStack blaze = new ItemStack(Items.BLAZE_POWDER, 3);
        CompoundTag tag = (CompoundTag) blaze.save(registries());
        ItemStack back = ItemStack.parseOptional(registries(), tag);
        assertEquals(Items.BLAZE_POWDER, back.getItem());
        assertEquals(3, back.getCount());
    }

    /**
     * 模拟写盘侧对 4 格数组的处理：1 格有货 + 3 格空。
     *
     * <p>断言整条循环<b>不抛</b>——这正是原先崩掉的那一步。
     */
    @Test
    void mixedOccupancyArraySavesWithoutThrowing() {
        ItemStack[] slots = {
                new ItemStack(Items.BLAZE_POWDER),
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
        };
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        assertDoesNotThrow(() -> {
            for (ItemStack stack : slots) {
                list.add(stack.isEmpty() ? new CompoundTag() : (CompoundTag) stack.save(registries()));
            }
        }, "混合占用的槽数组必须能整体写盘");
        assertEquals(4, list.size(), "ListTag 必须与 MAX_EXTRA_SLOTS 等长，下标即格号");
        assertTrue(ItemStack.parseOptional(registries(), (CompoundTag) list.get(1)).isEmpty());
        assertEquals(Items.BLAZE_POWDER,
                ItemStack.parseOptional(registries(), (CompoundTag) list.get(0)).getItem());
    }
}