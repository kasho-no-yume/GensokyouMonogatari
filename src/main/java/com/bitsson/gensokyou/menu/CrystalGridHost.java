package com.bitsson.gensokyou.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 仓储网格菜单的公共契约：服务端权威的导航（搜索/滚动/排序）与网格手势入口。
 * 无尽藏晶单块面板与无尽藏终端共用同一 C2S 协议与处理路径。
 */
public interface CrystalGridHost {

    int containerId();

    void applyNav(String query, int scrollDelta, int sortMode);

    void handleClick(ServerPlayer player, int action, ItemStack key);
}
