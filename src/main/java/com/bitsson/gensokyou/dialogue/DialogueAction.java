package com.bitsson.gensokyou.dialogue;

import com.mojang.serialization.Codec;
import java.util.Locale;

/** 对话选项动作（null = 仅跳转/关闭由 next 决定）。 */
public enum DialogueAction {
    OPEN_TRADE,
    CLOSE;

    public static final Codec<DialogueAction> CODEC = Codec.stringResolver(
            action -> action.name().toLowerCase(Locale.ROOT),
            name -> DialogueAction.valueOf(name.toUpperCase(Locale.ROOT)));
}
