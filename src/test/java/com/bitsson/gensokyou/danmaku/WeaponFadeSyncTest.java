package com.bitsson.gensokyou.danmaku;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 玩家武器弹「近相机淡出」的同步契约（源码级断言）。
 *
 * <p><b>为什么是源码级</b>：本仓库没有构造实体与 {@code Level} 的测试脚手架，而这条纪律的
 * 本质是「{@code fromWeapon} 必须随实体同步到客户端」——客户端渲染器拿它区分
 * 「生成在眼睛处的玩家武器弹」与「远处飞来的敌方弹幕」。一旦它退回成服务端普通字段，
 * 客户端恒读到默认值 false，淡出整段被跳过：麟弹 10 Hz 白闪回归，而敌方弹幕的渐隐修复
 * 依然生效——两个症状同时出现，正是本测试要钉死的那次回归。
 */
class WeaponFadeSyncTest {

    private static final Path PROJECTILE = Path.of("src", "main", "java", "com", "bitsson",
            "gensokyou", "entity", "AbstractDanmakuProjectile.java");
    private static final Path RENDERER = Path.of("src", "main", "java", "com", "bitsson",
            "gensokyou", "client", "renderer", "AbstractDanmakuRenderer.java");

    private static String projectile;
    private static String renderer;

    @BeforeAll
    static void loadSource() throws IOException {
        for (Path p : new Path[]{PROJECTILE, RENDERER}) {
            assertTrue(Files.exists(p), "找不到源码：" + p.toAbsolutePath()
                    + "（测试的工作目录应为项目根目录）");
        }
        projectile = Files.readString(PROJECTILE, StandardCharsets.UTF_8);
        renderer = Files.readString(RENDERER, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("fromWeapon 走 SynchedEntityData，不是服务端普通字段")
    void fromWeaponIsSynched() {
        assertTrue(projectile.contains("EntityDataSerializers.BOOLEAN")
                        && projectile.contains("DATA_FROM_WEAPON"),
                "必须定义布尔型同步字段 DATA_FROM_WEAPON");
        assertTrue(projectile.contains("builder.define(DATA_FROM_WEAPON, false)"),
                "DATA_FROM_WEAPON 必须在 defineSynchedData 里注册");
        assertTrue(projectile.contains("return this.entityData.get(DATA_FROM_WEAPON)"),
                "isFromWeapon() 必须读同步字段，不能读普通字段");
        assertTrue(projectile.contains("this.entityData.set(DATA_FROM_WEAPON, fromWeapon)"),
                "setFromWeapon() 必须写同步字段");
        assertTrue(!projectile.contains("private boolean fromWeapon"),
                "不得再保留服务端普通字段 fromWeapon（客户端会恒读到默认值 false）");
    }

    @Test
    @DisplayName("近相机淡出仍以 isFromWeapon 为门控")
    void fadeStillGatedOnFromWeapon() {
        int method = renderer.indexOf("protected float nearCameraFade(");
        assertTrue(method >= 0, "渲染器里找不到 nearCameraFade");
        String body = renderer.substring(method, renderer.indexOf("\n    }", method));
        assertTrue(body.contains("if (!entity.isFromWeapon())"),
                "nearCameraFade 必须只放行玩家武器弹，敌方弹幕全程可见");
    }

    @Test
    @DisplayName("存档键 FromWeapon 成对读写")
    void saveKeyIsPaired() {
        assertTrue(projectile.contains("putBoolean(\"FromWeapon\", true)"),
                "写入侧缺 FromWeapon");
        assertTrue(projectile.contains("tag.getBoolean(\"FromWeapon\")"),
                "读取侧缺 FromWeapon");
    }
}
