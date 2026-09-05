package com.bitsson.gensokyou.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** 对话节点图：id + 节点表 + 入口节点。形状对齐未来 datapack JSON。 */
public record DialogueGraph(ResourceLocation id, Map<String, DialogueNode> nodes, String entry) {

    public static final Codec<DialogueGraph> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(DialogueGraph::id),
                    Codec.unboundedMap(Codec.STRING, DialogueNode.CODEC).fieldOf("nodes")
                            .forGetter(DialogueGraph::nodes),
                    Codec.STRING.fieldOf("entry").forGetter(DialogueGraph::entry)
            ).apply(instance, DialogueGraph::new));

    public DialogueNode node(String key) {
        return nodes.get(key);
    }
}
