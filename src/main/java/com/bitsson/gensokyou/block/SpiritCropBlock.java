package com.bitsson.gensokyou.block;

import com.mojang.serialization.MapCodec;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 幻想乡作物基类：与 {@link CropBlock} 完全同构，外加一段「基质加速」的随机刻。
 *
 * <p><b>为什么不需要覆写 mayPlaceOn 认耕地</b>：NeoForge 21.1.248 的 ModDevGradle 会把原版
 * {@code state.is(Blocks.FARMLAND)} 改写为 {@code block instanceof FarmBlock}，
 * 因此任何 {@code FarmBlock} 子类（含 {@link SpiritSoilFarmlandBlock}）都被原版作物逻辑直接接受，
 * 无需在此重复声明。详见 design.md 的 D2。
 *
 * <p><b>基质加速判定放在作物侧而不是耕地侧</b>：{@code CropBlock#getGrowthSpeed} 的 3x3 扫描只认
 * {@code instance of FarmBlock}，若把加速写在 {@link SpiritSoilFarmlandBlock#randomTick} 里，
 * 「种在彼岸土上的彼岸花」就拿不到加速——彼岸土是普通土色方块、不是耕地，且它的作物压根不在
 * 任何耕地方块上方。故加速必须由作物自身读下方基底。
 */
public class SpiritCropBlock extends CropBlock {

    /** 6 生长阶段（0..5）。 */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 5);

    public static final MapCodec<SpiritCropBlock> CODEC =
            BlockBehaviour.simpleCodec(properties -> new SpiritCropBlock(properties, false));

    /** 为真时允许种植在 {@link ModBlocks#HIGAN_SOIL} 之上，并享有彼岸土加速。 */
    private final boolean higanSoilCrop;

    public SpiritCropBlock(BlockBehaviour.Properties properties, boolean higanSoilCrop) {
        super(properties);
        this.higanSoilCrop = higanSoilCrop;
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    public IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return 5;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        if (higanSoilCrop && state.is(ModBlocks.HIGAN_SOIL.get())) {
            return true;
        }
        return super.mayPlaceOn(state, level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) {
            return;
        }
        super.randomTick(state, level, pos, random);
        accelerateOnSpiritSoil(state, level, pos, random);
    }

    /**
     * 灵土加速：在原版生长之外，按概率追加一次生长进度。
     *
     * <ul>
     *   <li>下方是 {@link SpiritSoilFarmlandBlock}（含任意湿度）→ 按 {@code SPIRIT_SOIL_GROWTH_CHANCE} 判定；</li>
     *   <li>下方是 {@link ModBlocks#HIGAN_SOIL} 且本条目是彼岸系作物 → 按
     *       {@code HIGAN_SOIL_GROWTH_MULTIPLIER} 放大（分母越小越快）。</li>
     * </ul>
     *
     * <p>概率判定为「{@code random.nextInt(divisor) == 0}」，与骨粉相比温和得多，
     * 玩家看到的是"明显更快"而非"瞬间成熟"。
     */
    private void accelerateOnSpiritSoil(BlockState state, ServerLevel level, BlockPos pos,
                                        RandomSource random) {
        if (isMaxAge(state) || level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        int divisor = growthDivisor(level.getBlockState(pos.below()));
        if (divisor <= 0 || random.nextInt(divisor) != 0) {
            return;
        }
        BlockState grown = getStateForAge(getAge(state) + 1);
        if (grown != null && !grown.equals(state)) {
            level.setBlock(pos, grown, 2);
        }
    }

    /** 随机刻命中分母：越小越容易触发加速。返回 {@code 0} 表示不触发。 */
    private int growthDivisor(BlockState below) {
        if (below.getBlock() instanceof SpiritSoilFarmlandBlock) {
            return Math.max(1, GensokyouConfig.SPIRIT_SOIL_GROWTH_CHANCE.get());
        }
        if (higanSoilCrop && below.is(ModBlocks.HIGAN_SOIL.get())) {
            int base = Math.max(1, GensokyouConfig.SPIRIT_SOIL_GROWTH_CHANCE.get());
            int mult = Math.max(1, GensokyouConfig.HIGAN_SOIL_GROWTH_MULTIPLIER.get());
            return Math.max(1, base / mult);
        }
        return 0;
    }

    /** 供调试读取：本类是否吃彼岸土。 */
    public boolean isHiganSoilCrop() {
        return higanSoilCrop;
    }
}
