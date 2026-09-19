package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.network.CrystalStoragePagePayload;

/** 客户端暂存：当前打开的无尽藏晶可见页快照（按容器 id 归属）。 */
public final class ClientCrystalStorageState {

    private static int containerId = -1;
    private static CrystalStoragePagePayload page;

    private ClientCrystalStorageState() {
    }

    public static void update(CrystalStoragePagePayload payload) {
        containerId = payload.containerId();
        page = payload;
    }

    public static CrystalStoragePagePayload pageFor(int id) {
        return id == containerId ? page : null;
    }
}
