package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.DanmakuEmitter;
import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import com.bitsson.gensokyou.danmaku.motion.FormationFrame;
import com.bitsson.gensokyou.danmaku.track.Behaviour;
import com.bitsson.gensokyou.danmaku.track.Geometry;
import com.bitsson.gensokyou.danmaku.track.Projectile;
import com.bitsson.gensokyou.danmaku.track.Shape;
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

                .then(spiralNode())
                .then(flowerNode())

                // /danmaku laser-ring [N] [区域半径] [瞄准夹角] [长度] [粗细] [延迟秒] [持续秒]
                // 目标周围一圈激光朝内指：发射点在目标周围，不在 BOSS 身上。
                .then(laserRingNode())

                // /danmaku web [N] [球半径] [散射夹角] [直瞄比例] [长度] [粗细] [延迟秒] [持续秒]
                // 凌乱激光网：发射点真随机，瞄准逐发独立，玩家置身网中找夹缝
                .then(webNode())

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
     * 两重公转螺旋：弹绕自己的编队中心自转，整队同时绕外部中心公转，并沿初始方向螺旋前进。
     *
     * <p><b>两层轴刻意取不同方向</b>——外层公转的法线是竖直（编队在水平面里绕大圈），
     * 内层自转的法线是水平（弹在竖直面里绕编队中心转）。同轴会直接叠加成「转得更快」，
     * 那样就只剩一层，看不到「两重」。
     *
     * <p>叠上呼吸缩放后，螺旋的圈会一松一紧——这是「螺旋」最像 spirals 的读法。
     */
    /**
     * {@code /danmaku spiral} 的参数树。
     *
     * <p>七个可选参数逐级追加。每加一个就把前面已解析的值原样传下去，
     * 于是「默认值」只出现在最内层那一处，参数表与调用点不会各写一份。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spiralNode() {
        return Commands.literal("spiral")
                .executes(context -> spawnSpiral(context.getSource().getPlayerOrException(),
                        24, 4.0D, 6.0D, 1.5D, 0.35D, 60, 0.25D))
                .then(Commands.argument("count", IntegerArgumentType.integer(3, 256))
                        .executes(context -> spawnSpiral(context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "count"),
                                4.0D, 6.0D, 1.5D, 0.35D, 60, 0.25D))
                        .then(Commands.argument("innerRate", DoubleArgumentType.doubleArg(-20.0D, 20.0D))
                                .executes(context -> spawnSpiral(context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "count"),
                                        DoubleArgumentType.getDouble(context, "innerRate"),
                                        6.0D, 1.5D, 0.35D, 60, 0.25D))
                                .then(Commands.argument("outerRadius", DoubleArgumentType.doubleArg(0.0D, 40.0D))
                                        .executes(context -> spawnSpiral(context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                DoubleArgumentType.getDouble(context, "innerRate"),
                                                DoubleArgumentType.getDouble(context, "outerRadius"),
                                                1.5D, 0.35D, 60, 0.25D))
                                        .then(Commands.argument("outerRate", DoubleArgumentType.doubleArg(-20.0D, 20.0D))
                                                .executes(context -> spawnSpiral(context.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        DoubleArgumentType.getDouble(context, "innerRate"),
                                                        DoubleArgumentType.getDouble(context, "outerRadius"),
                                                        DoubleArgumentType.getDouble(context, "outerRate"),
                                                        0.35D, 60, 0.25D))
                                                .then(Commands.argument("breathAmp", DoubleArgumentType.doubleArg(0.0D, 3.0D))
                                                        .executes(context -> spawnSpiral(context.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(context, "count"),
                                                                DoubleArgumentType.getDouble(context, "innerRate"),
                                                                DoubleArgumentType.getDouble(context, "outerRadius"),
                                                                DoubleArgumentType.getDouble(context, "outerRate"),
                                                                DoubleArgumentType.getDouble(context, "breathAmp"),
                                                                60, 0.25D))
                                                        .then(Commands.argument("breathPeriod", IntegerArgumentType.integer(10, 600))
                                                                .executes(context -> spawnSpiral(context.getSource().getPlayerOrException(),
                                                                        IntegerArgumentType.getInteger(context, "count"),
                                                                        DoubleArgumentType.getDouble(context, "innerRate"),
                                                                        DoubleArgumentType.getDouble(context, "outerRadius"),
                                                                        DoubleArgumentType.getDouble(context, "outerRate"),
                                                                        DoubleArgumentType.getDouble(context, "breathAmp"),
                                                                        IntegerArgumentType.getInteger(context, "breathPeriod"),
                                                                        0.25D))
                                                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.0D, 2.0D))
                                                                        .executes(context -> spawnSpiral(context.getSource().getPlayerOrException(),
                                                                                IntegerArgumentType.getInteger(context, "count"),
                                                                                DoubleArgumentType.getDouble(context, "innerRate"),
                                                                                DoubleArgumentType.getDouble(context, "outerRadius"),
                                                                                DoubleArgumentType.getDouble(context, "outerRate"),
                                                                                DoubleArgumentType.getDouble(context, "breathAmp"),
                                                                                IntegerArgumentType.getInteger(context, "breathPeriod"),
                    DoubleArgumentType.getDouble(context, "speed"))))))))));
    }

    /**
     * {@code /danmaku flower} 的参数树。最后可选的 {@code retreat} 字面量给花瓣挂上
     * 「减速-悬停-后退」的推进项。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> flowerNode() {
        return Commands.literal("flower")
                .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                        5, 12, 3.0D, 1.8D, 2.0D, 0.4D, 60, 1.2D, false))
                .then(Commands.argument("petals", IntegerArgumentType.integer(3, 12))
                        .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "petals"),
                                12, 3.0D, 1.8D, 2.0D, 0.4D, 60, 1.2D, false))
                        .then(Commands.argument("perPetal", IntegerArgumentType.integer(1, 40))
                                .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "petals"),
                                        IntegerArgumentType.getInteger(context, "perPetal"),
                                        3.0D, 1.8D, 2.0D, 0.4D, 60, 1.2D, false))
                                .then(Commands.argument("baseRadius", DoubleArgumentType.doubleArg(0.5D, 20.0D))
                                        .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "petals"),
                                                IntegerArgumentType.getInteger(context, "perPetal"),
                                                DoubleArgumentType.getDouble(context, "baseRadius"),
                                                1.8D, 2.0D, 0.4D, 60, 1.2D, false))
                                        .then(Commands.argument("amp", DoubleArgumentType.doubleArg(0.0D, 20.0D))
                                                .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(context, "petals"),
                                                        IntegerArgumentType.getInteger(context, "perPetal"),
                                                        DoubleArgumentType.getDouble(context, "baseRadius"),
                                                        DoubleArgumentType.getDouble(context, "amp"),
                                                        2.0D, 0.4D, 60, 1.2D, false))
                                                .then(Commands.argument("spinRate", DoubleArgumentType.doubleArg(-20.0D, 20.0D))
                                                        .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(context, "petals"),
                                                                IntegerArgumentType.getInteger(context, "perPetal"),
                                                                DoubleArgumentType.getDouble(context, "baseRadius"),
                                                                DoubleArgumentType.getDouble(context, "amp"),
                                                                DoubleArgumentType.getDouble(context, "spinRate"),
                                                                0.4D, 60, 1.2D, false))
                                                        .then(Commands.argument("breathAmp", DoubleArgumentType.doubleArg(0.0D, 3.0D))
                                                                .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                                                        IntegerArgumentType.getInteger(context, "petals"),
                                                                        IntegerArgumentType.getInteger(context, "perPetal"),
                                                                        DoubleArgumentType.getDouble(context, "baseRadius"),
                                                                        DoubleArgumentType.getDouble(context, "amp"),
                                                                        DoubleArgumentType.getDouble(context, "spinRate"),
                                                                        DoubleArgumentType.getDouble(context, "breathAmp"),
                                                                        60, 1.2D, false))
                                                                .then(Commands.argument("breathPeriod", IntegerArgumentType.integer(10, 600))
                                                                        .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                                                                IntegerArgumentType.getInteger(context, "petals"),
                                                                                IntegerArgumentType.getInteger(context, "perPetal"),
                                                                                DoubleArgumentType.getDouble(context, "baseRadius"),
                                                                                DoubleArgumentType.getDouble(context, "amp"),
                                                                                DoubleArgumentType.getDouble(context, "spinRate"),
                                                                                DoubleArgumentType.getDouble(context, "breathAmp"),
                                                                                IntegerArgumentType.getInteger(context, "breathPeriod"),
                                                                                1.2D, false))
                                                                        .then(Commands.argument("stamenSize", DoubleArgumentType.doubleArg(0.0D, 4.0D))
                                                                                .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                                                                        IntegerArgumentType.getInteger(context, "petals"),
                                                                                        IntegerArgumentType.getInteger(context, "perPetal"),
                                                                                        DoubleArgumentType.getDouble(context, "baseRadius"),
                                                                                        DoubleArgumentType.getDouble(context, "amp"),
                                                                                        DoubleArgumentType.getDouble(context, "spinRate"),
                                                                                        DoubleArgumentType.getDouble(context, "breathAmp"),
                                                                                        IntegerArgumentType.getInteger(context, "breathPeriod"),
                                                                                        DoubleArgumentType.getDouble(context, "stamenSize"),
                                                                                        false))
                                                                                .then(Commands.literal("retreat")
                                                                                        .executes(context -> spawnFlower(context.getSource().getPlayerOrException(),
                                                                                                IntegerArgumentType.getInteger(context, "petals"),
                                                                                                IntegerArgumentType.getInteger(context, "perPetal"),
                                                                                                DoubleArgumentType.getDouble(context, "baseRadius"),
                                                                                                DoubleArgumentType.getDouble(context, "amp"),
                                                                                                DoubleArgumentType.getDouble(context, "spinRate"),
                                                                                                DoubleArgumentType.getDouble(context, "breathAmp"),
                                                                                                IntegerArgumentType.getInteger(context, "breathPeriod"),
                                                                                                DoubleArgumentType.getDouble(context, "stamenSize"),
                                                                                true)))))))))));
    }

    /**
     * 两重公转螺旋（需求图一）。
     *
     * <p>三个分量，各司其职：
     * <ul>
     *   <li><b>平面内</b>：环绕自己的中心自转（内层），环心绕另一个中心转（外层）。
     *       两层同轴，于是都发生在「面向玩家」的那个平面里。</li>
     *   <li><b>沿法线</b>：整组朝玩家推进。平面内转 + 法线平移 = 螺纹线，
     *       这才是「螺旋」的来源。</li>
     *   <li><b>呼吸</b>：环的半径周期性胀缩，螺距因此一松一紧。</li>
     * </ul>
     *
     * <p><b>初速方向 MUST 是法线（视线方向），不是环内每颗弹的半径方向。</b>
     * 早先的实现把初速设成了半径方向，于是整组在平面里向外扩散、沿法线毫无分量——
     * 看着是「一个转着的环」，而不是「一个朝你压过来的螺旋」。
     */
    private static int spawnSpiral(ServerPlayer player, int count, double innerRate,
                                   double outerRadius, double outerRate, double breathAmp,
                                   double breathPeriod, double speed) {
        Vec3 normal = player.getLookAngle().normalize();
        Vec3 center = player.getEyePosition().add(normal.scale(SPAWN_AHEAD));
        Vec3 up = Math.abs(normal.y) > 0.98D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = up.cross(normal).normalize();
        up = normal.cross(right).normalize();

        Behaviour.Formation formation = Behaviour.Formation.spin(0, 0, innerRate)
                .withSpin(normal, innerRate)
                .withOrbit(normal, outerRadius, outerRate)
                .withBreathing(1.0D, breathAmp, breathPeriod);

        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2.0D * i / count;
            Vec3 offset = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)))
                    .scale(SPIRAL_INNER_RADIUS);
            Vec3 origin = center.add(offset);

            SphereDanmaku bullet = new SphereDanmaku(
                    player.level(), player, 4.0F, 0, 0.4F, new HashSet<>());
            bullet.setPos(origin.x, origin.y, origin.z);
            bullet.setDirection(normal, speed);
            bullet.bindToFrame(formation.frameFor(center, origin));
            bullet.setLifetimeTicks(SPIRAL_LIFETIME);
            player.level().addFreshEntity(bullet);
        }
        DanmakuBudget.recordEmit();
        player.sendSystemMessage(Component.literal(String.format(
                "§a两重公转螺旋 §7(%d 发 · 平面内自转 %.2f°/tick + 环心公转 R%.1f @ %.2f°/tick"
                        + " · 沿法线 %.2f 格/tick · 呼吸 ±%.2f / %d tick)",
                count, innerRate, outerRadius, outerRate, speed, breathAmp,
                (int) breathPeriod)));
        return 1;
    }

    private static final double SPIRAL_INNER_RADIUS = 2.5D;
    private static final int SPIRAL_LIFETIME = 200;
    /**
     * 生成点距眼睛的距离。
     *
     * <p>沿法线（视线方向）推进时，若直接在眼睛处生成，整组会从玩家体内穿过——
     * 一秒内就到身后，演示变成「一帧闪过」。先推出去一段距离，才看得到「迎面压过来」。
     */
    private static final double SPAWN_AHEAD = 8.0D;

    /**
     * 玫瑰线花形阵列（需求图二）。
     *
     * <p>两个正交的分量：
     * <ul>
     *   <li><b>平面内</b>：每颗弹沿<b>自己到中心的连线</b>远离→靠近往复，
     *       也就是编队帧的整体等比缩放。形状（花瓣长短）编码在出生点上，
     *       所以「花瓣开合」与「行进」完全无关，可以各自独立调。</li>
     *   <li><b>沿法线</b>：整组朝玩家推进。{@code retreat} 让这一项变成
     *       「减速→停 3 秒→反向加速」，于是整朵花先压过来、顿住、再退回去。</li>
     * </ul>
     *
     * <p>花蕊是<b>单独一颗大弹</b>，出生点就在编队中心 ⇒ 偏移为 0 ⇒ 钉在中心不跟着花瓣飘。
     * 这正是「固定花蕊、花瓣开合」的形态；让花蕊一起缩放的话，整朵花就只是「推拉镜头」。
     */
    private static int spawnFlower(ServerPlayer player, int petals, int perPetal, double baseRadius,
                                  double amp, double spinRate, double breathAmp,
                                  double breathPeriod, double stamenSize, boolean retreat) {
        int n = Math.max(3, petals) * Math.max(1, perPetal);
        Vec3 normal = player.getLookAngle().normalize();
        Vec3 center = player.getEyePosition().add(normal.scale(SPAWN_AHEAD));
        Vec3 up = Math.abs(normal.y) > 0.98D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = up.cross(normal).normalize();
        up = normal.cross(right).normalize();

        Behaviour.Formation formation = Behaviour.Formation.spin(0, 0, spinRate)
                .withSpin(normal, spinRate)
                .withBreathing(1.0D, breathAmp, breathPeriod);

        double speed = retreat ? FLOWER_RETREAT_SPEED : FLOWER_ADVANCE_SPEED;
        for (int i = 0; i < n; i++) {
            // 先按花瓣分组，再在花瓣内均分——「每瓣十几颗」而不是「整圈均分」。
            int petal = i / Math.max(1, perPetal);
            int within = i % Math.max(1, perPetal);
            double theta = Math.PI * 2.0D * petal / Math.max(3, petals)
                    + Math.PI * 2.0D * within / Math.max(1, perPetal) * 0.08D;
            double r = baseRadius + amp * Math.cos(Math.max(1, petals) * theta);
            Vec3 origin = center.add(right.scale(Math.cos(theta)).add(up.scale(Math.sin(theta)))
                    .scale(r));

            SphereDanmaku bullet = new SphereDanmaku(
                    player.level(), player, 4.0F, 0, 0.4F, new HashSet<>());
            bullet.setPos(origin.x, origin.y, origin.z);
            bullet.setDirection(normal, speed);
            if (retreat) {
                bullet.configureSpeedProfile(DanmakuSpeedProfile.decelerateAndReturn(
                        speed, 20.0D, 60.0D, 40.0D, 0.15D), false);
            }
            bullet.bindToFrame(formation.frameFor(center, origin));
            bullet.setLifetimeTicks(FLOWER_LIFETIME);
            player.level().addFreshEntity(bullet);
        }

        if (stamenSize > 0.0D) {
            SphereDanmaku stamen = new SphereDanmaku(
                    player.level(), player, 4.0F, 0, (float) stamenSize, new HashSet<>());
            stamen.setPos(center.x, center.y, center.z);
            stamen.setDirection(normal, speed);
            if (retreat) {
                stamen.configureSpeedProfile(DanmakuSpeedProfile.decelerateAndReturn(
                        speed, 20.0D, 60.0D, 40.0D, 0.15D), false);
            }
            // 偏移为 0 ⇒ 无论编队帧怎么旋转缩放，花蕊都钉在花心
            stamen.bindToFrame(formation.frameFor(center, center));
            stamen.setLifetimeTicks(FLOWER_LIFETIME);
            player.level().addFreshEntity(stamen);
        }
        DanmakuBudget.recordEmit();

        player.sendSystemMessage(Component.literal(String.format(
                "§a玫瑰线花形 §7(%d 瓣 × %d = %d 发 + 花蕊 · R%.1f±%.1f · 自转 %.2f°/tick"
                        + " · 呼吸 ±%.2f / %d tick · 沿法线 %.2f 格/tick%s)",
                petals, perPetal, n, baseRadius, amp, spinRate, breathAmp,
                (int) breathPeriod, speed, retreat ? " · 减速3秒后退" : "")));
        return 1;
    }

    /**
     * {@code /danmaku laser-ring} 的参数树。
     *
     * <p>八个可选参数逐级追加，默认值只出现在最内层一处。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> laserRingNode() {
        return Commands.literal("laser-ring")
                .executes(context -> spawnLaserRing(context.getSource().getPlayerOrException(),
                        12, 5.0D, 0.0D, 14.0D, 0.35D, 0.6D, 1.5D))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(context -> spawnLaserRing(context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "count"),
                                5.0D, 0.0D, 14.0D, 0.35D, 0.6D, 1.5D))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 24.0D))
                                .executes(context -> spawnLaserRing(context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "count"),
                                        DoubleArgumentType.getDouble(context, "radius"),
                                        0.0D, 14.0D, 0.35D, 0.6D, 1.5D))
                                .then(Commands.argument("aimDeg", DoubleArgumentType.doubleArg(0.0D, 180.0D))
                                        .executes(context -> spawnLaserRing(context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                DoubleArgumentType.getDouble(context, "radius"),
                                                DoubleArgumentType.getDouble(context, "aimDeg"),
                                                14.0D, 0.35D, 0.6D, 1.5D))
                                        .then(Commands.argument("length", DoubleArgumentType.doubleArg(2.0D, 40.0D))
                                                .executes(context -> spawnLaserRing(context.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        DoubleArgumentType.getDouble(context, "radius"),
                                                        DoubleArgumentType.getDouble(context, "aimDeg"),
                                                        DoubleArgumentType.getDouble(context, "length"),
                                                        0.35D, 0.6D, 1.5D))
                                                .then(Commands.argument("thickness", DoubleArgumentType.doubleArg(0.05D, 2.0D))
                                                        .executes(context -> spawnLaserRing(context.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(context, "count"),
                                                                DoubleArgumentType.getDouble(context, "radius"),
                                                                DoubleArgumentType.getDouble(context, "aimDeg"),
                                                                DoubleArgumentType.getDouble(context, "length"),
                                                                DoubleArgumentType.getDouble(context, "thickness"),
                                                                0.6D, 1.5D))
                                                        .then(Commands.argument("delaySec", DoubleArgumentType.doubleArg(0.0D, 10.0D))
                                                                .executes(context -> spawnLaserRing(context.getSource().getPlayerOrException(),
                                                                        IntegerArgumentType.getInteger(context, "count"),
                                                                        DoubleArgumentType.getDouble(context, "radius"),
                                                                        DoubleArgumentType.getDouble(context, "aimDeg"),
                                                                        DoubleArgumentType.getDouble(context, "length"),
                                                                        DoubleArgumentType.getDouble(context, "thickness"),
                                                                        DoubleArgumentType.getDouble(context, "delaySec"),
                                                                        1.5D))
                                                                .then(Commands.argument("durationSec", DoubleArgumentType.doubleArg(0.2D, 20.0D))
                                                                        .executes(context -> spawnLaserRing(context.getSource().getPlayerOrException(),
                                                                                IntegerArgumentType.getInteger(context, "count"),
                                                                                DoubleArgumentType.getDouble(context, "radius"),
                                                                                DoubleArgumentType.getDouble(context, "aimDeg"),
                                                                                DoubleArgumentType.getDouble(context, "length"),
                                                                                DoubleArgumentType.getDouble(context, "thickness"),
                                                                                DoubleArgumentType.getDouble(context, "delaySec"),
                                                                                DoubleArgumentType.getDouble(context, "durationSec"))))))))));
    }

    /**
     * 目标周围一圈激光朝内指。
     *
     * <p>走的是与符卡完全相同的路径（{@code Shape.AROUND_TARGET} + {@code Projectile.LASER}），
     * 所以这个命令同时也是那条通路的一次实机验证——它跑得通，符卡里就写得出来。
     *
     * <p>瞄准夹角可到 <b>180°（完全自由，含从背后射）</b>——激光有延迟预警，
     * 公平性来自预警而非方向。默认 0：全部精确指向玩家。
     */
    private static int spawnLaserRing(ServerPlayer player, int count, double radius, double aimDeg,
                                      double length, double thickness, double delaySec,
                                      double durationSec) {
        Vec3 target = player.getEyePosition();
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 worldUp = new Vec3(0, 1, 0);
        if (Math.abs(forward.dot(worldUp)) > 0.98D) {
            worldUp = new Vec3(1, 0, 0);
        }

        java.util.List<Geometry.Shot> shots = Geometry.build(Shape.AROUND_TARGET,
                player.getEyePosition(), forward, target, worldUp,
                Shape.Params.defaults().count(count).radius(radius).spread(aimDeg),
                0.0D, 0.0D);
        Projectile laser = Projectile.laser(length, thickness, delaySec, durationSec);
        for (Geometry.Shot shot : shots) {
            DanmakuEmitter.emit(player, shot, Behaviour.NONE, 0xFF3355, 4.0F, null, null, laser);
        }
        player.sendSystemMessage(Component.literal(String.format(
                "§c目标周围激光环 §7(%d 道 · 半径 %.1f 格 · 瞄准夹角 ≤%.0f° · 长 %.1f · 粗 %.2f"
                        + " · 延迟 %.1fs · 持续 %.1fs)",
                shots.size(), radius, aimDeg, length, thickness, delaySec, durationSec)));
        return 1;
    }

    /**
     * {@code /danmaku web} 的参数树。
     *
     * <p>九个可选参数逐级追加，默认值只出现在最内层一处。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> webNode() {
        return Commands.literal("web")
                .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                        20, 6.0D, 90.0D, 0.3D, 16.0D, 0.35D, 0.6D, 1.5D))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "count"),
                                6.0D, 90.0D, 0.3D, 16.0D, 0.35D, 0.6D, 1.5D))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 24.0D))
                                .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "count"),
                                        DoubleArgumentType.getDouble(context, "radius"),
                                        90.0D, 0.3D, 16.0D, 0.35D, 0.6D, 1.5D))
                                .then(Commands.argument("aimDeg", DoubleArgumentType.doubleArg(0.0D, 90.0D))
                                        .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                DoubleArgumentType.getDouble(context, "radius"),
                                                DoubleArgumentType.getDouble(context, "aimDeg"),
                                                0.3D, 16.0D, 0.35D, 0.6D, 1.5D))
                                        .then(Commands.argument("aimBias", DoubleArgumentType.doubleArg(0.01D, 1.0D))
                                                .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        DoubleArgumentType.getDouble(context, "radius"),
                                                        DoubleArgumentType.getDouble(context, "aimDeg"),
                                                        DoubleArgumentType.getDouble(context, "aimBias"),
                                                        16.0D, 0.35D, 0.6D, 1.5D))
                                                .then(Commands.argument("length", DoubleArgumentType.doubleArg(2.0D, 40.0D))
                                                        .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(context, "count"),
                                                                DoubleArgumentType.getDouble(context, "radius"),
                                                                DoubleArgumentType.getDouble(context, "aimDeg"),
                                                                DoubleArgumentType.getDouble(context, "aimBias"),
                                                                DoubleArgumentType.getDouble(context, "length"),
                                                                0.35D, 0.6D, 1.5D))
                                                        .then(Commands.argument("thickness", DoubleArgumentType.doubleArg(0.05D, 2.0D))
                                                                .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                                                                        IntegerArgumentType.getInteger(context, "count"),
                                                                        DoubleArgumentType.getDouble(context, "radius"),
                                                                        DoubleArgumentType.getDouble(context, "aimDeg"),
                                                                        DoubleArgumentType.getDouble(context, "aimBias"),
                                                                        DoubleArgumentType.getDouble(context, "length"),
                                                                        DoubleArgumentType.getDouble(context, "thickness"),
                                                                        0.6D, 1.5D))
                                                                .then(Commands.argument("delaySec", DoubleArgumentType.doubleArg(0.0D, 10.0D))
                                                                        .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                                                                                IntegerArgumentType.getInteger(context, "count"),
                                                                                DoubleArgumentType.getDouble(context, "radius"),
                                                                                DoubleArgumentType.getDouble(context, "aimDeg"),
                                                                                DoubleArgumentType.getDouble(context, "aimBias"),
                                                                                DoubleArgumentType.getDouble(context, "length"),
                                                                                DoubleArgumentType.getDouble(context, "thickness"),
                                                                                DoubleArgumentType.getDouble(context, "delaySec"),
                                                                                1.5D))
                                                                        .then(Commands.argument("durationSec", DoubleArgumentType.doubleArg(0.2D, 20.0D))
                                                                                .executes(context -> spawnLaserWeb(context.getSource().getPlayerOrException(),
                                                                                        IntegerArgumentType.getInteger(context, "count"),
                                                                                        DoubleArgumentType.getDouble(context, "radius"),
                                                                                        DoubleArgumentType.getDouble(context, "aimDeg"),
                                                                                        DoubleArgumentType.getDouble(context, "aimBias"),
                                                                                        DoubleArgumentType.getDouble(context, "length"),
                                                                                        DoubleArgumentType.getDouble(context, "thickness"),
                                                                                        DoubleArgumentType.getDouble(context, "delaySec"),
                                                                                        DoubleArgumentType.getDouble(context, "durationSec")))))))))));
    }

    /**
     * 凌乱激光网——「让玩家置身网中、找夹缝」。
     *
     * <p>走的是与符卡完全相同的路径（{@code Shape.LATTICE} + {@code Projectile.LASER}），
     * 所以这个命令同时也是那条通路的一次实机验证。
     *
     * <p>与 {@code laser-ring} 的分野是<b>随机</b>：这里的发射点在目标周围的球体内真随机
     * （位置与距离都随机），瞄准逐发独立，所以整片网没有可读的秩序——那才是「网」，
     * 而角度等分的那一版读起来像「道具生成的阵」。
     *
     * <p>{@code aimBias} 比例精确瞄准，其余在 {@code aimDeg} 内散开：全散射则随便走就能躲，
     * 全直瞄则没有夹缝。默认 0.3 / 90°。
     */
    private static int spawnLaserWeb(ServerPlayer player, int count, double radius, double aimDeg,
                                     double aimBias, double length, double thickness,
                                     double delaySec, double durationSec) {
        Vec3 target = player.getEyePosition();
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 worldUp = new Vec3(0, 1, 0);
        if (Math.abs(forward.dot(worldUp)) > 0.98D) {
            worldUp = new Vec3(1, 0, 0);
        }

        java.util.List<Geometry.Shot> shots = Geometry.build(Shape.LATTICE,
                player.getEyePosition(), forward, target, worldUp,
                Shape.Params.defaults().count(count).radius(radius).spread(aimDeg).aimBias(aimBias),
                0.0D, 0.0D, player.level().getRandom());
        Projectile laser = Projectile.laser(length, thickness, delaySec, durationSec);
        for (Geometry.Shot shot : shots) {
            DanmakuEmitter.emit(player, shot, Behaviour.NONE, 0xFF3355, 4.0F, null, null, laser);
        }
        player.sendSystemMessage(Component.literal(String.format(
                "§c凌乱激光网 §7(%d 道 · 球半径 ≤%.1f 格 · 直瞄 %.0f%% · 其余夹角 ≤%.0f°"
                        + " · 长 %.1f · 粗 %.2f · 延迟 %.1fs · 持续 %.1fs)",
                shots.size(), radius, aimBias * 100.0D, aimDeg,
                length, thickness, delaySec, durationSec)));
        return 1;
    }

    private static final double FLOWER_ADVANCE_SPEED = 0.18D;
    private static final double FLOWER_RETREAT_SPEED = 0.25D;
    private static final int FLOWER_LIFETIME = 200;

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
