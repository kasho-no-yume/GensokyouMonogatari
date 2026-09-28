package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.track.SpellCard;
import com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile;
import com.bitsson.gensokyou.entity.KnifeDanmaku;
import com.bitsson.gensokyou.entity.LaserDanmaku;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.bitsson.gensokyou.entity.TalismanDanmaku;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
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
import java.util.List;

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
                
                // /danmaku sphere [颜色] [档案id] - 在玩家面前生成球型弹幕
                .then(Commands.literal("sphere")
                        .executes(context -> spawnSphere(context.getSource().getPlayerOrException(), 0))
                        .then(Commands.argument("color", IntegerArgumentType.integer(0, 0xFFFFFF))
                                .executes(context -> spawnSphere(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "color")))))

                // /danmaku wall <数量> [周期tick] [占空比] - 相位隐藏弹幕墙
                .then(Commands.literal("wall")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2000))
                                .executes(context -> spawnWall(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "count"), 40, 0.5D))
                                .then(Commands.argument("period", IntegerArgumentType.integer(1, 600))
                                        .executes(context -> spawnWall(
                                                context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                IntegerArgumentType.getInteger(context, "period"), 0.5D))
                                        .then(Commands.argument("duty",
                                                        IntegerArgumentType.integer(0, 100))
                                                .executes(context -> spawnWall(
                                                        context.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        IntegerArgumentType.getInteger(context, "period"),
                                                        IntegerArgumentType.getInteger(context, "duty")
                                                                / 100.0D))))))

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

                // /danmaku stress <N> [存活秒] - 密度阶梯测试台
                .then(Commands.literal("stress")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 8000))
                                .executes(context -> spawnStress(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "count"), 600))
                                .then(Commands.argument("lifetimeSeconds",
                                                IntegerArgumentType.integer(1, 1200))
                                        .executes(context -> spawnStress(
                                                context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                IntegerArgumentType.getInteger(context, "lifetimeSeconds"))))))

                // /danmaku prism [数量] - 生成五角星柱（视觉档案的可换几何验证件）
                .then(Commands.literal("prism")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(context -> spawnPrism(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "count")))))

                // /danmaku list - 列出全部可预览的符卡
                .then(Commands.literal("list")
                        .executes(context -> listCards(context.getSource())))

                // /danmaku stop - 停止预览
                .then(Commands.literal("stop")
                        .executes(context -> stopPreview(context.getSource())))

                // /danmaku card <BOSS> [卡序] [秒数] [伤害] - 只跑弹幕，不跑 BOSS
                .then(Commands.literal("card")
                        .then(Commands.argument("boss", StringArgumentType.word())
                                .executes(context -> previewCards(context.getSource(),
                                        StringArgumentType.getString(context, "boss"), -1, 30, 0.0F))
                                .then(Commands.argument("card", IntegerArgumentType.integer(-1, 9))
                                        .executes(context -> previewCards(context.getSource(),
                                                StringArgumentType.getString(context, "boss"),
                                                IntegerArgumentType.getInteger(context, "card"),
                                                30, 0.0F))
                                        .then(Commands.argument("seconds",
                                                        IntegerArgumentType.integer(5, 300))
                                                .executes(context -> previewCards(context.getSource(),
                                                        StringArgumentType.getString(context, "boss"),
                                                        IntegerArgumentType.getInteger(context, "card"),
                                                        IntegerArgumentType.getInteger(context, "seconds"),
                                                        0.0F))
                                                .then(Commands.argument("damage",
                                                                DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                                        .executes(context -> previewCards(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "boss"),
                                                                IntegerArgumentType.getInteger(context, "card"),
                                                                IntegerArgumentType.getInteger(context, "seconds"),
                                                                (float) DoubleArgumentType
                                                                        .getDouble(context, "damage"))))))))
        );
    }

    /**
     * 预览符卡图案：<b>只跑弹幕</b>，没有 BOSS 的 AI / 转向 / 避障。
     *
     * <p>用途是审图案本身。实机对着 BOSS 看时，「弹走得不顺」与「BOSS 站错了位置」
     * 会混在一起；本命令把后者摘掉。
     *
     * @param cardIndex -1 = 依次预览该 BOSS 的全部符卡
     * @param damage    默认 0。伤害为 0 仍会走完命中判定（弹照样撞上你并消失），
     *                  故命中行为可验，但不会在审图案时把人打死
     */
    private static int previewCards(CommandSourceStack source, String bossId, int cardIndex,
                                    int seconds, float damage) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("该命令需要由玩家执行"));
            return 0;
        }
        DanmakuPreview.CardSet set = DanmakuPreview.setOf(bossId);
        if (set == null) {
            source.sendFailure(Component.literal("未知的 BOSS id: " + bossId));
            source.sendFailure(Component.literal("可用: " + String.join(", ",
                    "big_fairy, kuzumono, kitsune_bi, nomen_mask")));
            return 0;
        }
        List<SpellCard> cards = set.cards();
        if (cardIndex >= cards.size()) {
            source.sendFailure(Component.literal("卡序越界: " + bossId + " 只有 "
                    + cards.size() + " 张"));
            return 0;
        }
        List<SpellCard> selected = cardIndex < 0
                ? cards
                : List.of(cards.get(cardIndex));
        DanmakuPreview.start(player, set, selected, seconds, damage);
        source.sendSuccess(() -> Component.literal("§c图案预览 §7"
                + bossId + (cardIndex < 0 ? "（全部 " + cards.size() + " 张）" : " / " + cards.get(cardIndex).name())
                + "，共 " + seconds + " 秒，伤害 " + damage
                + "。§f/danmaku stop 停止"), false);
        return selected.size();
    }

    private static int listCards(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§c可预览的符卡 §7（/danmaku card <id> [卡序] [秒数] [伤害]）"), false);
        for (String line : DanmakuPreview.describeAll().split("\n")) {
            if (!line.isBlank()) {
                source.sendSuccess(() -> Component.literal("§7" + line.trim()), false);
            }
        }
        return 1;
    }

    private static int stopPreview(CommandSourceStack source) {
        try {
            DanmakuPreview.stop(source.getPlayerOrException());
        } catch (CommandSyntaxException e) {
            // 无玩家在场时无从停止，忽略
        }
        source.sendSuccess(() -> Component.literal("预览已停止"), false);
        return 1;
    }

    /** 压测弹的公转半径（格）。见 {@link #spawnStress} 的设计说明。 */
    private static final double STRESS_ORBIT_RADIUS = 2.0D;
    /** 压测弹的弹速（格/tick）。半径与角速度由此反推，保持两者相乘不变。 */
    private static final double STRESS_SPEED = 0.2D;
    /** 压测网格的格距（格）。大于最大弹径，避免同格重叠。 */
    private static final double STRESS_SPACING = 3.0D;
    /** 压测弹直径（格）。取小值以免其碰撞箱与玩家或其它弹互相干扰。 */
    private static final float STRESS_SIZE = 0.12F;

    /**
     * 密度阶梯测试台：在玩家周围生成 N 颗<b>原地公转</b>的弹，令密度稳定可控。
     *
     * <p><b>为什么必须是公转而不是直线</b>——本测试要测的是命中判定开销与<b>局部密度</b>
     * 的关系，两项硬要求：
     * <ol>
     *   <li>每 tick 速度 MUST 非零，否则 {@code DanmakuHitScan} 在扫掠前就早退，
     *       测不到查询开销（零位移时整个判定被跳过）</li>
     *   <li>弹 MUST NOT 飞离所在体积，否则密度随时间衰减，{@code live} 不再等于 N</li>
     * </ol>
     * 公转同时满足两者：绕自身出生点转圈，半径固定，永远不离开原地。
     *
     * <p>公转角速度由「半径 × 角速度 = 线速度」反推：
     * {@code rate = 2π·speed·(180/π)/(20·radius) = 18·speed/radius}（度/秒）。
     *
     * <p>用法：
     * <pre>
     * /gs_boss danmaku reset
     * /danmaku stress 250
     * （等 10 秒）
     * /gs_boss danmaku          ← 记 live（≈N）与 tickTime 的 avg
     * </pre>
     * 依次取 N = 100 / 250 / 500 / 1000 / 2000 作阶梯：
     * <b>avg 随 N 呈直线</b> → 判定开销与密度无关（按类型子表剪枝的假设成立）
     * <b>avg 随 N 呈抛物线</b> → 假设不成立，弹幕间存在相互遍历，须重新评估
     */
    private static int spawnStress(ServerPlayer player, int count, int lifetimeTicks) {
        Vec3 origin = player.getEyePosition();
        int side = (int) Math.ceil(Math.cbrt(count));
        double rateDegPerSec = 18.0D * STRESS_SPEED / STRESS_ORBIT_RADIUS;
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int gx = i % side;
            int gy = (i / side) % side;
            int gz = i / (side * side);
            Vec3 at = origin.add(
                    (gx - side / 2.0) * STRESS_SPACING,
                    (gy - side / 2.0) * STRESS_SPACING,
                    (gz - side / 2.0) * STRESS_SPACING);

            SphereDanmaku bullet = new SphereDanmaku(
                    player.level(), player, 0.0F, 0, STRESS_SIZE, new HashSet<>());
            bullet.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            // 任意水平方向皆可——公转后轨迹与初始方向无关，只要非零。
            bullet.setDirection(new Vec3(1.0D, 0.0D, 0.0D), STRESS_SPEED);
            bullet.configureCurve(new Vec3(0.0D, 1.0D, 0.0D), rateDegPerSec);
            bullet.setLifetimeTicks(lifetimeTicks);
            player.level().addFreshEntity(bullet);
            spawned++;
        }
        DanmakuBudget.recordEmit();
        player.sendSystemMessage(Component.literal(String.format(
                "§c压测弹 §7%d 发（%d×%d×%d 网格，格距 %.1f，公转半径 %.1f，寿命 %d tick）",
                spawned, side, side, side, STRESS_SPACING, STRESS_ORBIT_RADIUS, lifetimeTicks)));
        player.sendSystemMessage(Component.literal(
                "§7等约 10 秒后执行 /gs_boss danmaku，读 live 与 tickTime 的 avg"));
        return spawned;
    }

    /**
     * 相位隐藏弹幕墙：验证「隐藏期不判伤、不销毁、方块碰撞仍生效」。
     *
     * <p>弹在玩家前方一片网格上生成、<b>朝玩家缓慢推进</b>并配相位隐藏。观察要点：
     * <ol>
     *   <li>明期打得到你，暗期<b>打不到也不消失</b>——可站原地不动验证</li>
     *   <li>暗期弹变半透明，且<b>身后可见弹不被挖出方洞</b>（本体与发光两层都已切到不写深度）</li>
     *   <li>暗期撞上方块<b>照样消失</b>（方块判定独立于隐藏掩码）</li>
     * </ol>
     *
     * <p>全批相位偏移取 0，让它们同时明灭——这正是弹幕墙要的整片效果。
     */
    private static int spawnWall(ServerPlayer player, int count, int period, double duty) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int side = (int) Math.ceil(Math.sqrt(count));
        double spacing = 1.2D;
        for (int i = 0; i < count; i++) {
            int gx = i % side;
            int gz = i / side;
            Vec3 at = origin.add(look.scale(10.0D))
                    .add(new Vec3((gx - side / 2.0) * spacing, 0.0D, (gz - side / 2.0) * spacing));
            SphereDanmaku bullet = new SphereDanmaku(
                    player.level(), player, 2.0F, 0, 0.35F, new HashSet<>());
            bullet.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            // 朝玩家缓慢推进——慢速是为了留出观察时间
            Vec3 toPlayer = player.getEyePosition().subtract(at);
            if (toPlayer.lengthSqr() > 1.0E-6D) {
                bullet.setDirection(toPlayer.normalize(), 0.10D);
            }
            bullet.configurePhaseHide(period, duty, 0);
            bullet.setLifetimeTicks(period * 20);
            player.level().addFreshEntity(bullet);
        }
        DanmakuBudget.recordEmit();
        player.sendSystemMessage(Component.literal(String.format(
                "§c相位隐藏弹幕墙 §7%d 发（%d×%d 网格，周期 %d tick，占空比 %.0f%%）",
                count, side, side, period, duty * 100.0D)));
        player.sendSystemMessage(Component.literal(
                "§7明期应打得到你；暗期应打不到、不消失、且能直接穿过"));
        return count;
    }

    /**
     * 五角星柱：视觉档案「几何可换」的验证件。
     *
     * <p>弹为静止（不推进），因为要观察的是<b>造型</b>——尤其是俯仰摆动让厚度在
     * 极值处显形。碰撞缩放 0.60 已由档案给出，故从凹角擦过时的判定是否符合观感
     * 也一并可验。
     */
    private static int spawnPrism(ServerPlayer player, int count) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int side = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            int gx = i % side;
            int gz = i / side;
            Vec3 at = origin.add(look.scale(6.0D))
                    .add(new Vec3((gx - side / 2.0) * 2.5D, 0.0D, (gz - side / 2.0) * 2.5D));
            SphereDanmaku bullet = new SphereDanmaku(
                    player.level(), player, 0.0F, 0, 0.8F, new HashSet<>());
            bullet.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            bullet.setVisualProfile(DanmakuVisualProfile.STAR_PRISM);
            bullet.setDeltaMovement(Vec3.ZERO);
            bullet.setLifetimeTicks(600);
            player.level().addFreshEntity(bullet);
        }
        DanmakuBudget.recordEmit();
        player.sendSystemMessage(Component.literal(
                "§c五角星柱 §7" + count + " 发（厚度 = 半径/2，碰撞缩放 0.60，俯仰摆动 50°/40tick）"));
        player.sendSystemMessage(Component.literal(
                "§7观察点：厚度是否可见、五角星轮廓是否恒可读、凹角处判定是否符合观感"));
        return count;
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
