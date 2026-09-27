package com.bitsson.gensokyou.support;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

/**
 * 纯 JUnit 环境下启动 Minecraft 注册表所需的引导。
 *
 * <p>{@link Bootstrap#bootStrap()} 会在方块注册期间加载 NeoForge 的特性标志，
 * 而该过程要求 {@link LoadingModList} 已存在；同时实体注册会触发数据修复器，
 * 需要先设定游戏版本。测试里补齐这两项前置条件即可，不必启动完整的模组加载器。
 */
public final class MinecraftTestBootstrap {

    private static boolean started;

    private MinecraftTestBootstrap() {
    }

    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        if (LoadingModList.get() == null) {
            LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        started = true;
    }

    /**
     * 原版语言文件是否在测试类路径上。
     *
     * <p>把 {@link net.minecraft.network.chat.Component} 解析成文本需要
     * {@code assets/minecraft/lang/en_us.json}。若测试运行时缺少该资源，
     * 只有依赖文本解析的用例应当跳过。
     */
    public static boolean hasDefaultLanguage() {
        return net.minecraft.locale.Language.class
                .getResource("/assets/minecraft/lang/en_us.json") != null;
    }
}
