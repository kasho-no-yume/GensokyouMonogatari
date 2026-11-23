package com.bitsson.gensokyou.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;

public final class GensokyoBiomeSource extends BiomeSource {
    public static final MapCodec<GensokyoBiomeSource> CODEC = RecordCodecBuilder.<GensokyoBiomeSource>mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("sanzu_river").forGetter(source -> source.sanzuRiver),
            Biome.CODEC.fieldOf("hakurei_shrine").forGetter(source -> source.hakureiShrine),
            Biome.CODEC.fieldOf("moriya_mountain").forGetter(source -> source.moriyaMountain),
            Biome.CODEC.fieldOf("magic_forest").forGetter(source -> source.magicForest),
            Biome.CODEC.fieldOf("misty_bamboo_grove").forGetter(source -> source.mistyBambooGrove),
            Biome.CODEC.fieldOf("hakugyokurou").forGetter(source -> source.hakugyokurou),
            Biome.CODEC.fieldOf("chireiden").forGetter(source -> source.chireiden),
            Biome.CODEC.fieldOf("scarlet_devil_mansion").forGetter(source -> source.scarletMansion),
            Biome.CODEC.fieldOf("misty_lake").forGetter(source -> source.mistyLake),
            Biome.CODEC.fieldOf("human_village").forGetter(source -> source.humanVillage)
    ).apply(instance, GensokyoBiomeSource::new)).stable();

    private final Holder<Biome> sanzuRiver;
    private final Holder<Biome> hakureiShrine;
    private final Holder<Biome> moriyaMountain;
    private final Holder<Biome> magicForest;
    private final Holder<Biome> mistyBambooGrove;
    private final Holder<Biome> hakugyokurou;
    private final Holder<Biome> chireiden;
    private final Holder<Biome> scarletMansion;
    private final Holder<Biome> mistyLake;
    private final Holder<Biome> humanVillage;
    private final List<Holder<Biome>> possibleBiomes;

    private GensokyoBiomeSource(Holder<Biome> sanzuRiver, Holder<Biome> hakureiShrine,
                                Holder<Biome> moriyaMountain, Holder<Biome> magicForest,
                                Holder<Biome> mistyBambooGrove, Holder<Biome> hakugyokurou,
                                Holder<Biome> chireiden, Holder<Biome> scarletMansion, Holder<Biome> mistyLake,
                                Holder<Biome> humanVillage) {
        this.sanzuRiver = sanzuRiver;
        this.hakureiShrine = hakureiShrine;
        this.moriyaMountain = moriyaMountain;
        this.magicForest = magicForest;
        this.mistyBambooGrove = mistyBambooGrove;
        this.hakugyokurou = hakugyokurou;
        this.chireiden = chireiden;
        this.scarletMansion = scarletMansion;
        this.mistyLake = mistyLake;
        this.humanVillage = humanVillage;
        this.possibleBiomes = List.of(sanzuRiver, hakureiShrine, moriyaMountain, magicForest,
                mistyBambooGrove, hakugyokurou, chireiden, scarletMansion, mistyLake, humanVillage);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return possibleBiomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        GensokyoLayout layout = GensokyoLayoutDensity.layoutOf(sampler.continentalness());
        int x = QuartPos.toBlock(quartX);
        int z = QuartPos.toBlock(quartZ);
        GensokyoLayout.Region region = layout.regionAt(x, z);
        if (region == null) {
            return sanzuRiver;
        }
        return switch (region) {
            case HAKUREI -> hakureiShrine;
            case MORIYA -> moriyaMountain;
            case MAGIC_FOREST -> magicForest;
            case BAMBOO -> mistyBambooGrove;
            case HAKUGYOKUROU -> hakugyokurou;
            case CHIREIDEN -> chireiden;
            case SCARLET_MANSION -> layout.isMistyLake(x, z)
                    ? mistyLake : scarletMansion;
            case HUMAN_VILLAGE -> humanVillage;
        };
    }
}
