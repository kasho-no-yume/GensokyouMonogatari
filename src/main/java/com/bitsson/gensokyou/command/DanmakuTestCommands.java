package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.KnifeDanmaku;
import com.bitsson.gensokyou.entity.LaserDanmaku;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.bitsson.gensokyou.entity.TalismanDanmaku;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashSet;

/**
 * 弹幕系统测试指令
 * 用于测试四种弹幕类型的功能
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class DanmakuTestCommands {

    private DanmakuTestCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("danmaku")
                .requires(source -> source.hasPermission(2))
                
                // /danmaku sphere [颜色] - 在玩家面前生成球型弹幕
                .then(Commands.literal("sphere")
                        .executes(context -> spawnSphere(context.getSource().getPlayerOrException(), 0))
                        .then(Commands.argument("color", IntegerArgumentType.integer(0, 0xFFFFFF))
                                .executes(context -> spawnSphere(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "color")))))
                
                // /danmaku knife - 生成穿透型飞刀弹幕
                .then(Commands.literal("knife")
                        .executes(context -> spawnKnife(context.getSource().getPlayerOrException())))
                
                // /danmaku talisman <目标> [灵敏度] - 生成追踪型灵符弹幕
                .then(Commands.literal("talisman")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> spawnTalisman(
                                        context.getSource().getPlayerOrException(),
                                        EntityArgument.getEntity(context, "target"),
                                        60.0))
                                .then(Commands.argument("sensitivity", IntegerArgumentType.integer(10, 360))
                                        .executes(context -> spawnTalisman(
                                                context.getSource().getPlayerOrException(),
                                                EntityArgument.getEntity(context, "target"),
                                                IntegerArgumentType.getInteger(context, "sensitivity"))))))
                
                // /danmaku laser [长度] - 生成激光弹幕
                .then(Commands.literal("laser")
                        .executes(context -> spawnLaser(context.getSource().getPlayerOrException(), 20.0))
                        .then(Commands.argument("length", IntegerArgumentType.integer(1, 100))
                                .executes(context -> spawnLaser(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "length")))))
                
                // /danmaku ring [数量] - 环形弹幕测试
                .then(Commands.literal("ring")
                        .executes(context -> spawnRing(context.getSource().getPlayerOrException(), 8))
                        .then(Commands.argument("count", IntegerArgumentType.integer(3, 32))
                                .executes(context -> spawnRing(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                
                // /danmaku barrage - 弹幕狂潮测试（性能测试）
                .then(Commands.literal("barrage")
                        .executes(context -> spawnBarrage(context.getSource().getPlayerOrException())))
        );
    }

    /**
     * 生成球型弹幕
     */
    private static int spawnSphere(ServerPlayer player, int color) {
        Vec3 pos = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        
        SphereDanmaku sphere = new SphereDanmaku(
                player.level(),
                player,
                4.0F,           // 伤害
                color,          // 颜色（0=随机）
                0.4F,           // 大小
                new HashSet<>() // 空白名单（会打所有人）
        );
        
        sphere.setPos(pos.x, pos.y, pos.z);
        sphere.shoot(look.x, look.y, look.z, 1.0F, 0F);
        player.level().addFreshEntity(sphere);
        
        player.sendSystemMessage(Component.literal("§a生成球型弹幕 §7(颜色: " + 
                (color == 0 ? "随机" : String.format("#%06X", color)) + ")"));
        return 1;
    }

    /**
     * 生成飞刀弹幕（穿透型）
     */
    private static int spawnKnife(ServerPlayer player) {
        Vec3 pos = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        
        KnifeDanmaku knife = new KnifeDanmaku(
                player.level(),
                player,
                3.0F,           // 伤害
                new HashSet<>()
        );
        
        knife.setPos(pos.x, pos.y, pos.z);
        knife.shoot(look.x, look.y, look.z, 1.5F, 0F);
        player.level().addFreshEntity(knife);
        
        player.sendSystemMessage(Component.literal("§a生成飞刀弹幕 §7(穿透型)"));
        return 1;
    }

    /**
     * 生成追踪型灵符弹幕
     *
     * @param sensitivity 每秒可偏转角度，越小转弯越缓越平滑
     */
    private static int spawnTalisman(ServerPlayer player, Entity target, double sensitivity) {
        Vec3 pos = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        TalismanDanmaku talisman = new TalismanDanmaku(
                player.level(),
                player,
                4.0F,           // 伤害
                0xFF3030,       // 红色
                target,         // 追踪目标
                sensitivity,    // 灵敏度（度/秒）
                new HashSet<>()
        );

        talisman.setPos(pos.x, pos.y, pos.z);
        talisman.shoot(look.x, look.y, look.z, 0.8F, 0F);
        player.level().addFreshEntity(talisman);

        player.sendSystemMessage(Component.literal(
                "§a生成灵符弹幕 §7(追踪: " + target.getName().getString()
                        + ", 灵敏度: " + (int) sensitivity + "°/s)"));
        return 1;
    }

    /**
     * 生成激光弹幕
     */
    private static int spawnLaser(ServerPlayer player, double length) {
        Vec3 pos = player.getEyePosition();
        Vec3 direction = player.getLookAngle();

        LaserDanmaku laser = new LaserDanmaku(
                player.level(),
                pos,
                direction,
                2.0F,           // 每次判伤的伤害
                0x00FFFF,       // 青色
                length,         // 最大长度
                0.25,           // 粗细半径
                1.0,            // 延迟 1 秒
                3.0,            // 持续 3 秒
                player,         // 发射者
                new HashSet<>()
        );
        player.level().addFreshEntity(laser);

        player.sendSystemMessage(Component.literal("§a生成激光弹幕 §7(长度: " + (int) length + "格, 延迟1秒, 持续3秒)"));
        return 1;
    }

    /**
     * 环形弹幕测试
     */
    private static int spawnRing(ServerPlayer player, int count) {
        Vec3 origin = player.getEyePosition();
        
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle)).normalize();
            
            SphereDanmaku sphere = new SphereDanmaku(
                    player.level(),
                    player,
                    4.0F,
                    0,  // 随机颜色
                    0.4F,
                    new HashSet<>()
            );
            
            sphere.setPos(origin.x, origin.y, origin.z);
            sphere.shoot(dir.x, dir.y, dir.z, 0.8F, 0F);
            player.level().addFreshEntity(sphere);
        }
        
        player.sendSystemMessage(Component.literal("§a生成环形弹幕 §7(" + count + "发)"));
        return 1;
    }

    /**
     * 弹幕狂潮测试（性能测试）
     */
    private static int spawnBarrage(ServerPlayer player) {
        Vec3 origin = player.getEyePosition();
        int totalCount = 0;
        
        // 10层环形弹幕
        for (int layer = 0; layer < 10; layer++) {
            int count = 12 + layer * 2;
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count + layer * 0.3;
                double pitch = (layer - 5) * 0.1;
                Vec3 dir = new Vec3(
                        Math.cos(angle) * Math.cos(pitch),
                        Math.sin(pitch),
                        Math.sin(angle) * Math.cos(pitch)
                ).normalize();
                
                SphereDanmaku sphere = new SphereDanmaku(
                        player.level(),
                        player,
                        2.0F,
                        0,  // 随机颜色
                        0.3F,
                        new HashSet<>()
                );
                
                sphere.setPos(origin.x, origin.y, origin.z);
                sphere.shoot(dir.x, dir.y, dir.z, 0.6F, 0F);
                player.level().addFreshEntity(sphere);
                totalCount++;
            }
        }
        
        player.sendSystemMessage(Component.literal("§c弹幕狂潮！ §7(共" + totalCount + "发)"));
        return 1;
    }
}
