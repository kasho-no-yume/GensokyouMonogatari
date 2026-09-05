package com.bitsson.gensokyou.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.Optional;

/** 对话选项：标签 + 下一节点（空=结束）+ 动作（可空）。 */
public record DialogueOption(Component label, Optional<String> next, Optional<DialogueAction> action) {

    public static final Codec<DialogueOption> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ComponentSerialization.CODEC.fieldOf("label").forGetter(DialogueOption::label),
                    Codec.STRING.optionalFieldOf("next").forGetter(DialogueOption::next),
                    DialogueAction.CODEC.optionalFieldOf("action").forGetter(DialogueOption::action)
            ).apply(instance, DialogueOption::new));
}
