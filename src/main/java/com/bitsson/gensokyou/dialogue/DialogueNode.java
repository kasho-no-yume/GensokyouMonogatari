package com.bitsson.gensokyou.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.Optional;

/** 单个对话节点：多行文本 + 选项列表。 */
public record DialogueNode(Component text, List<DialogueOption> options) {

    public static final Codec<DialogueNode> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ComponentSerialization.CODEC.fieldOf("text").forGetter(DialogueNode::text),
                    DialogueOption.CODEC.listOf().optionalFieldOf("options", List.of())
                            .forGetter(DialogueNode::options)
            ).apply(instance, DialogueNode::new));
}
