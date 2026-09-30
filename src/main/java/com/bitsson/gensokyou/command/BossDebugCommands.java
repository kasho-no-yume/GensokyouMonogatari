package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.AbstractTouhouBoss;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * BOSS 实机调试命令（开发设施）。
 *
 * <p>存在的理由：走完整链条才能召出一只 BOSS 的话，调一次弹幕要付 40 分钟生存成本，
 * 弹幕侧根本迭代不动。故提供直生成口，与既有 {@code /gs_test}（数值台）、
 * {@code /gs_debug summon}（仪式演出台）并列，构成三角调试面。
 *
 * <p>命令：
 * <pre>
 *   /gs_boss list
 *   /gs_boss spawn &lt;entity_id&gt; [秒数] [挨弹数]
 *   /gs_boss lint
 * </pre>
 *
 * <p>权限 2，与既有调试命令一致。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class BossDebugCommands {

    private BossDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gs_boss")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));
        root.then(Commands.literal("lint").executes(ctx -> lint(ctx.getSource())));
        root.then(Commands.literal("danmaku").executes(ctx -> danmakuStats(ctx.getSource())));
        root.then(Commands.literal("danmaku").then(Commands.literal("reset")
                .executes(ctx -> danmakuReset(ctx.getSource()))));
        root.then(Commands.literal("spawn")
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(ctx -> spawn(ctx.getSource(),
                                ResourceLocationArgument.getId(ctx, "id"), null, null))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(5D, 3600D))
                                .then(Commands.argument("hits", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> spawn(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "id"),
                                                DoubleArgumentType.getDouble(ctx, "seconds"),
                                                IntegerArgumentType.getInteger(ctx, "hits")))))));

        dispatcher.register(root);
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[GS-BOSS] 召唤型 BOSS："), false);
        for (EntityType<?> type : List.of(ModEntityTypes.BIG_FAIRY.get(),
                ModEntityTypes.KUZUMONO.get())) {
            source.sendSuccess(() -> Component.literal("[GS-BOSS] "
                    + typeId(type) + "  ->  /gs_boss spawn "
                    + typeId(type)), false);
        }
        return 1;
    }

    private static int lint(CommandSourceStack source) {
        int violations = 0;
        for (EntityType<?> type : List.of(ModEntityTypes.BIG_FAIRY.get(),
                ModEntityTypes.KUZUMONO.get())) {
            // 实体需要世界才能构造，故 lint 走静态符卡表；此处只做存在性自检。
            source.sendSuccess(() -> Component.literal("[GS-BOSS] "
                    + typeId(type) + " 已注册"), false);
        }
        if (violations == 0) {
            source.sendSuccess(() -> Component.literal("[GS-BOSS] 符卡表静态校验见单测 "
                    + "BossCardLintTest（gradlew test）"), false);
        }
        return violations;
    }

    /**
     * 弹幕管线统计。「看着撞到了却不掉血」有两种成因：判定没命中，或伤害被吞。
     * 这条命令把 发射 / 实体命中 / 方块命中 / 实际伤害量 摆出来，一次定位。
     */
    private static int danmakuStats(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[GS-DANMAKU] "
                + com.bitsson.gensokyou.danmaku.DanmakuBudget.stats(source.getLevel())), false);
        // 「弹幕莫名消失」单列一行。所有死法在原版那边都是同一个 remove(DISCARDED)，
        // 分开打印才有意义；age 那段是死亡年龄分布，用来判断「是不是按寿命到期」。
        source.sendSuccess(() -> Component.literal("[GS-DANMAKU] "
                + com.bitsson.gensokyou.danmaku.DanmakuBudget.removalStats()), false);
        source.sendSuccess(() -> Component.literal("[GS-DANMAKU] "
                + com.bitsson.gensokyou.danmaku.DanmakuBudget.timingStats()), false);
        // 状态同步读数单列一行：上面那行的 lag/age 是「空间误差在速度方向上的投影」，
        // 这里 cmp/oot 是「与同一采样时刻的本地状态比出来的真误差」。两者不同源，
        // 混在一行里读会把投影当年龄差。
        source.sendSuccess(() -> Component.literal("[GS-DANMAKU] "
                + com.bitsson.gensokyou.danmaku.render.DanmakuSyncStats.summary()), false);
        // 时间轴读数**仅客户端有意义**（速率是客户端相对服务器的快慢）。专用服务端上
        // 它恒为 WARMING/rate=1.0，那本身就是「这里没有客户端时间轴」的正确答案，
        // 所以不按服务端/客户端分开打印 —— 打印一个恒定值比不打印更容易误导。
        source.sendSuccess(() -> Component.literal("[GS-DANMAKU] clock "
                + com.bitsson.gensokyou.danmaku.render.DanmakuClientClock.summary()), false);
        return 1;
    }

    private static int danmakuReset(CommandSourceStack source) {
        com.bitsson.gensokyou.danmaku.DanmakuBudget.resetStats();
        com.bitsson.gensokyou.danmaku.render.DanmakuSyncStats.reset();
        source.sendSuccess(() -> Component.literal("[GS-DANMAKU] 统计已清零"), false);
        return 1;
    }

    private static int spawn(CommandSourceStack source, ResourceLocation id,
                             Double seconds, Integer hits) {
        ServerLevel level = source.getLevel();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null) {
            source.sendFailure(Component.literal("[GS-BOSS] 未知实体 " + id));
            return 0;
        }
        Entity created = type.create(level);
        if (created == null) {
            source.sendFailure(Component.literal("[GS-BOSS] " + id + " 创建失败（非生物？）"));
            return 0;
        }
        Vec3 at = new Vec3(source.getPosition().x + 3.0D,
                source.getPosition().y + 1.0D, source.getPosition().z);
        created.moveTo(at.x, at.y, at.z, 0F, 0F);
        if (created instanceof AbstractTouhouBoss boss) {
            boss.setAnchor(source.getPosition());
            boss.rollStats(level.getRandom()::nextDouble);
            if (seconds != null) {
                boss.debugSetSeconds(seconds);
            }
            if (hits != null) {
                boss.debugSetHits(hits);
            }
            boss.debugApplyStats();
        }
        level.addFreshEntity(created);
        if (source.getPlayer() != null && created instanceof net.minecraft.world.entity.Mob mob) {
            mob.setTarget(source.getPlayer());
        }
        String summary = String.format(
                "[GS-BOSS] 已生成 %s  生命=%.0f  弹伤=%.1f  有效HP=%.0f  目标数=%d  覆盖玩家=%s",
                id, maxHealthOf(created), danmakuDamageOf(created), effectiveHpOf(created),
                targetCountOf(created),
                source.getPlayer() != null ? source.getPlayer().getGameProfile().getName() : "-");
        source.sendSuccess(() -> Component.literal(summary), false);
        if (source.getPlayer() != null && targetCountOf(created) == 0) {
            source.sendSuccess(() -> Component.literal(
                    "[GS-BOSS] 警告：未锁定到任何目标。创造/旁观模式玩家会被目标选取跳过，"
                            + "且创造模式玩家本身免疫非 bypasses_invulnerability 伤害——请在生存模式测试。"),
                    false);
        }
        return 1;
    }

    private static int targetCountOf(Entity entity) {
        return entity instanceof AbstractTouhouBoss boss ? boss.lockedTargets().size() : -1;
    }

    private static double effectiveHpOf(Entity entity) {
        return entity instanceof AbstractTouhouBoss boss ? boss.effectiveHp() : 0.0D;
    }

    private static String typeId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private static double maxHealthOf(Entity entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity living
                ? living.getMaxHealth() : 0.0D;
    }

    private static double danmakuDamageOf(Entity entity) {
        return entity instanceof AbstractTouhouBoss boss ? boss.danmakuDamage() : 0.0D;
    }
}
