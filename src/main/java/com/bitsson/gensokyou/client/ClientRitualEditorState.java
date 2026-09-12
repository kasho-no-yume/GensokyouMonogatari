package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.editor.EditorPreviewState;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;

/**
 * 编辑杖力建预览的客户端暂存：预览态 + 渲染用 pattern（合成 JSON 或 loader 原图，服务端择一下发）。
 * 三色分类每帧基于本地世界重算（同构建杖投影范式，零逐格同步）。
 */
public final class ClientRitualEditorState {

    public record Active(EditorPreviewState state, RitualPattern pattern) {
    }

    @Nullable
    private static Active active;

    private ClientRitualEditorState() {
    }

    public static void update(com.bitsson.gensokyou.network.EditorPreviewPayload payload) {
        if (payload.patternId() == null) {
            active = null;
            return;
        }
        var id = payload.patternId();
        RitualPattern pattern;
        if (payload.composedJson() != null) {
            try {
                pattern = RitualPatternLoader.parseForEdit(id,
                        JsonParser.parseString(payload.composedJson()).getAsJsonObject());
            } catch (RuntimeException exception) {
                active = null;
                return;
            }
        } else {
            pattern = RitualPatternLoader.byId(id).orElse(null);
            if (pattern == null) {
                active = null;
                return;
            }
        }
        active = new Active(payload.toState(), pattern);
    }

    public static void clear() {
        active = null;
    }

    @Nullable
    public static Active active() {
        return active;
    }
}
