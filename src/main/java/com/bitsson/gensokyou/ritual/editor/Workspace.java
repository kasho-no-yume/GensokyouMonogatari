package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.ritual.RitualPattern;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * 编辑工作区：以锚定核心为中心的捕获/清扫唯一视野边界（design D2）。
 * x/z 因四重对称恒以核心为心（sizeX/sizeZ 为奇数全长），仅 y 可偏移。
 * 区外方块既不被 sweep 也不被捕获——"区外万物皆不存在"。
 */
public record Workspace(int sizeX, int sizeZ, int height, int yOffset) {

    public static final Codec<Workspace> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.intRange(1, 255).fieldOf("size_x").forGetter(Workspace::sizeX),
                    Codec.intRange(1, 255).fieldOf("size_z").forGetter(Workspace::sizeZ),
                    Codec.intRange(1, 255).fieldOf("height").forGetter(Workspace::height),
                    Codec.intRange(-255, 255).fieldOf("y_offset").forGetter(Workspace::yOffset)
            ).apply(instance, Workspace::of));

    public static final StreamCodec<RegistryFriendlyByteBuf, Workspace> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Workspace::sizeX,
                    ByteBufCodecs.VAR_INT, Workspace::sizeZ,
                    ByteBufCodecs.VAR_INT, Workspace::height,
                    ByteBufCodecs.VAR_INT, Workspace::yOffset,
                    Workspace::new);

    /** 归一：x/z 全长钳到奇数（保证核心居中），其余取绝对值下限 1。 */
    public static Workspace of(int sizeX, int sizeZ, int height, int yOffset) {
        return new Workspace(odd(sizeX), odd(sizeZ), Math.max(1, height), yOffset);
    }

    private static int odd(int v) {
        v = Math.max(1, v);
        return v % 2 == 0 ? v - 1 : v;
    }

    public int halfX() {
        return (sizeX - 1) / 2;
    }

    public int halfZ() {
        return (sizeZ - 1) / 2;
    }

    public int minY() {
        return yOffset;
    }

    public int maxY() {
        return yOffset + height - 1;
    }

    public boolean contains(int x, int y, int z) {
        return Math.abs(x) <= halfX() && Math.abs(z) <= halfZ() && y >= minY() && y <= maxY();
    }

    /** 按配置上限逐轴钳制（轴长与 |y偏移| 共用上限，y 偏移留正负号）。 */
    public Workspace clamped(int maxAxis) {
        int cap = maxAxis | 1;
        int x = Math.min(sizeX, cap);
        int z = Math.min(sizeZ, cap);
        int h = Math.min(height, cap);
        int off = Math.max(-cap, Math.min(cap, yOffset));
        return of(x, z, h, off);
    }

    /** 默认工作区 = 该阶级累积切片 AABB（y 偏移 = 切片 minY）；空切片退化为单格。 */
    public static Workspace defaultFor(List<RitualPattern.BlockEntry> slice) {
        if (slice.isEmpty()) {
            return of(1, 1, 1, 0);
        }
        int maxAbsX = 0;
        int maxAbsZ = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (RitualPattern.BlockEntry entry : slice) {
            maxAbsX = Math.max(maxAbsX, Math.abs(entry.x()));
            maxAbsZ = Math.max(maxAbsZ, Math.abs(entry.z()));
            minY = Math.min(minY, entry.y());
            maxY = Math.max(maxY, entry.y());
        }
        return of(2 * maxAbsX + 1, 2 * maxAbsZ + 1, maxY - minY + 1, minY);
    }
}
