package com.bitsson.gensokyou.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * 球形核心的物品渲染扩展。
 *
 * <p><b>为什么单独一个类</b>：{@code IClientItemExtensions#getCustomRenderer()} 的返回类型
 * 是客户端专属类 {@code BlockEntityWithoutLevelRenderer}。把实现放进独立类，公共侧
 * （{@code DanmakuWeaponItem}）就只在<b>方法体</b>里引用它——而方法体里的引用是惰性解析的，
 * 专用服务器永远不会加载客户端类。
 *
 * <p>（直接把实现写成 {@code DanmakuWeaponItem} 里的匿名内部类是常见写法，但那个匿名类
 * 的方法签名里带着客户端类，一旦被服务端加载就会 NoClassDefFoundError。）
 */
public final class CoreOrbItemExtensions {

    /** 渲染器实例（惰性创建并缓存）。 */
    private static BlockEntityWithoutLevelRenderer renderer;

    /** 自定义渲染器实例。仅在客户端被加载。 */
    public static final IClientItemExtensions INSTANCE = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
                Minecraft mc = Minecraft.getInstance();
                renderer = new CoreOrbRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
            }
            return renderer;
        }
    };

    private CoreOrbItemExtensions() {
    }
}
