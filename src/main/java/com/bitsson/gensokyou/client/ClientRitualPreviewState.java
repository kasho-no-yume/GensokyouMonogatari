package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.ritual.RitualPreviewState;

import java.util.Optional;

/**
 * 构建器投影的客户端暂存：静态持有服务端下发的预览态。
 * 置入/替换与清除完全由 payload 驱动；重连自然清空（payload 不再下发），
 * 同连接换维度/死亡由预览态内 dimension 字段在渲染门控处兜底。
 */
public final class ClientRitualPreviewState {

    private static RitualPreviewState preview;

    private ClientRitualPreviewState() {
    }

    public static void update(Optional<RitualPreviewState> payload) {
        preview = payload.orElse(null);
    }

    public static RitualPreviewState active() {
        return preview;
    }
}
