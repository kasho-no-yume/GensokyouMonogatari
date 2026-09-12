package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.registry.ModBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/** 运行期 {@link BlockTagIndex}：标签成员与品阶查内置注册表（服务端保存链路使用）。 */
public final class RegistryBlockTagIndex implements BlockTagIndex {

    public static final RegistryBlockTagIndex INSTANCE = new RegistryBlockTagIndex();

    private RegistryBlockTagIndex() {
    }

    @Override
    public List<String> members(String tagId) {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(tagId));
        List<String> out = new ArrayList<>();
        BuiltInRegistries.BLOCK.getTag(tag).ifPresent(holders ->
                holders.forEach(holder -> out.add(BuiltInRegistries.BLOCK.getKey(holder.value()).toString())));
        return out;
    }

    @Override
    public int tierOf(String blockId) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId))
                .map(ModBlocks::tierOf)
                .orElse(-1);
    }
}
