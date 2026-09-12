package com.bitsson.gensokyou.ritual.editor;

/** 捕获/diff 域的轻量相对坐标（纯 int，不依赖 MC BlockPos 以便无注册表测试）。 */
public record BlockPos3(int x, int y, int z) {
    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
