package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.balance.BalanceNumbers;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.balance.MonsterStatBudget;
import com.bitsson.gensokyou.danmaku.program.content.BossProgramCards;
import com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramBackend;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.netprof.NetProfile;
import com.bitsson.gensokyou.registry.ModAttributes;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 召唤型东方 BOSS 的基类（{@code add-remnant-touhou-bosses}）。
 *
 * <p>提供四件此前散落在各处的共性：
 * <ol>
 *   <li><b>符卡即阶段</b>——正式符卡由程序战斗按血量阈值切分。
 *       符卡<b>不</b>设独立生命池，血条读整体占比。
 *   <li><b>超过 1024 的生命</b>——原版 {@code MAX_HEALTH} 是 RangedAttribute，上限 1024。
 *       实际血量钳到 1024，超出部分以伤害除数实现（{@link #damageScale()}），
 *       比例天然正确，故血条照常工作。
 *   <li><b>距离带 + 自由游走</b>的移动策略，全部参数可覆写。
 *   <li><b>≤5 多目标</b>与血条全体可见。
 * </ol>
 *
 * <p>移动刻意<b>不是</b>固定角速度的环绕轨道：一个可解的站位就废掉整场战斗。
 * 自由游走让弹幕出发点每发都在变，玩家无法背角度记死。
 */
public abstract class AbstractTouhouBoss extends FlyingMob implements Enemy, TouhouMonster, TouhouBoss {

    /** 原版 MAX_HEALTH 上限。 */
    private static final double VANILLA_MAX_HEALTH = 1024.0D;
    /** 目标列表刷新周期（tick）。 */
    private static final int TARGET_REFRESH_TICKS = 20;

    /**
     * 弹幕锁定半径（格）——<b>同时</b>作为原版 {@code NearestAttackableTargetGoal} 的
     * followRange。
     *
     * <p>两者 MUST 是同一个数。此前它们是 64（弹幕锁定，硬编码在 {@code refreshTargets}）
     * 与 48（followRange，构造属性时传入）两个独立的值，于是存在一段
     * <b>双标准区间</b>：48~64 格内 BOSS 会朝你放按人复制的弹幕，却不再追你。
     * 那段区间没有任何设计依据，只是一次没对齐的巧合。
     *
     * <p>它同时管着三件事，故 MUST 只有一个来源：按人复制的瞄准型轨道、默认攻击的瞄准基准、
     * 以及移动/注视的取人范围。改动请只改这一处。
     */
    public static final double LOCK_RADIUS = 64.0D;

    /** 连续这么多 tick 位移不达标即判为卡住。 */
    private static final int STUCK_TICKS = 20;
    /** 「没动」的判定阈值（20 tick 内平方位移）。 */
    private static final double STUCK_EPSILON_SQR = 0.04D;
    /** 自救时最多向上找几格空气。 */
    private static final int UNSTICK_LOOKUP = 6;
    /** 召唤锚点（祭坛核心上方）。游走点会避开它附近，避免 BOSS 贴在祭坛上不动。 */
    private Vec3 anchor = null;

    private final TouhouBossBar bossBar;
    /** 正式程序战斗；未迁移的占位内容通过独立子类运行。 */
    private BossProgramBackend programBackend;
    private boolean programOpenFailed;
    /** 存档恢复的后端标记；旧存档缺键时为 null。 */
    private com.bitsson.gensokyou.danmaku.program.gameplay.ProgramBackendState persistedBackend;
    private int lastAnnouncedProgramCard = -1;
    private List<Player> lockedTargets = new ArrayList<>();
    private Vec3 wanderTarget;
    /** 当前<b>注视</b>的那名被锁定目标（与主目标未必是同一个）。 */
    private Player facingTarget;
    private int wanderRepickAt;
    private double effectiveHp = -1.0D;
    private boolean statsApplied;
    private int lastTargetScanTick;
    /** 卡墙检测：上次确认「确实在动」时的 tick 与位置。 */
    private int lastProgressTick;
    private Vec3 lastProgressPos;
    /** 玩家符卡控制（add-player-spellcards）：被致盲（禁锁定）的截止 gameTime。 */
    private long spellControlUntil;
    /** 被减速的截止 gameTime 与倍率（自定义修饰，不用原版 Slowness）。 */
    private long spellSlowUntil;
    private double spellSlowFactor = 1.0D;

    protected AbstractTouhouBoss(EntityType<? extends AbstractTouhouBoss> type, Level level) {        super(type, level);
        this.xpReward = 200;
        this.moveControl = new FairyMoveControl(this);
        this.setNoGravity(true);
        this.lastProgressPos = this.position();
        // ⚠️ 传 this 而非 this.getUUID()：实体的 UUID 在构造之后还会被 Entity#load 用存档值
        // 覆盖（读档即变）。TouhouBossBar 内部懒解析 UUID，抓构造期快照会静默失配。
        this.bossBar = new TouhouBossBar(this, this.getDisplayName(),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    }

    // ------------------------------------------------------------------
    // 玩家符卡控制接入（add-player-spellcards D5）：独立的"被控制"开关，
    // 不改 refreshTargets / BossSteering 的调度结构。
    // ------------------------------------------------------------------

    /** 致盲：清当前目标并在给的 tick 内抑制重新锁定（黑暗结界每 tick 调用刷新）。 */
    public void applySpellControl(int ticks) {
        this.spellControlUntil = Math.max(this.spellControlUntil, this.level().getGameTime() + ticks);
        if (this.getTarget() != null) {
            super.setTarget(null);
        }
    }

    /** 减速：给自定义移速倍率，持续给的 tick（蛛网每 tick 调用刷新）。 */
    public void applySpellSlow(int ticks, double factor) {
        this.spellSlowUntil = Math.max(this.spellSlowUntil, this.level().getGameTime() + ticks);
        this.spellSlowFactor = Math.max(0.01D, Math.min(1.0D, factor));
    }

    /** 当前是否处于"被控制（禁锁定）"窗口内。 */
    public boolean isSpellControlled() {
        return this.level().getGameTime() < this.spellControlUntil;
    }

    private double spellSpeedFactor() {
        return this.level().getGameTime() < this.spellSlowUntil ? this.spellSlowFactor : 1.0D;
    }

    /**
     * 被控制期间拒绝一切目标注入（{@code NearestAttackableTargetGoal} 与外部代码都走这里）。
     * 显式 {@code setTarget(null)} 放行（清目标）。
     */
    @Override
    public void setTarget(net.minecraft.world.entity.LivingEntity target) {
        if (target != null && isSpellControlled()) {
            return;
        }
        super.setTarget(target);
    }

    // ------------------------------------------------------------------
    // 子类契约
    // ------------------------------------------------------------------


    /** 战斗秒数目标：生命 = 参照玩家DPS × 秒。见文件末尾的 {@code configSeconds} 覆写层。 */
    protected abstract double configSeconds();

    /** 挨弹数目标：弹伤 = 参照玩家EHP ÷ 挨弹数。见文件末尾的 {@code configHits} 覆写层。 */
    protected abstract int configHits();

    /** 参照玩家阶级，用于取 DPS/EHP 曲线。 */
    protected int referenceTier() {
        return 1;
    }

    /** HUD 使用零起下标；程序战斗安装前返回空。 */
    @Override
    public OptionalInt activeCardIndex() {
        BossProgramBackend program = programBackend;
        if (program != null && program.active()) {
            int index = program.activeCardIndex();
            return index < 1 ? OptionalInt.empty() : OptionalInt.of(index - 1);
        }
        return OptionalInt.empty();
    }

    /** 名称直接来自正式程序内容表，客户端无需构建旧轨道。 */
    @Override
    public Optional<Component> spellCardName(int index) {
        return programContentId().flatMap(BossProgramCards::forOwner)
                .filter(deck -> index >= 0 && index < deck.cards().size())
                .map(deck -> Component.translatable(deck.cards().get(index).nameKey()));
    }

    /** 距离带覆写点。 */
    protected double moveMin() {
        return BalanceNumbers.BOSS_MOVE_MIN;
    }

    protected double moveMax() {
        return BalanceNumbers.BOSS_MOVE_MAX;
    }

    /**
     * 相对<b>召唤锚点</b>的巡航空域带（格）。BOSS 有自己的飞行高度，
     * <b>不跟随玩家升降</b>——跟随会让它像被绳子拴在玩家头顶，也让垂直方向的
     * 躲避解失效（玩家往上一跳就跟着上去，等于没有这一维）。
     */
    protected double hoverFloor() {
        return 3.0D;
    }

    protected double hoverCeiling() {
        return 8.0D;
    }

    /**
     * 移速覆写点。
     *
     * <p>本值是 {@link FairyMoveControl} 唯一的速度来源（它只读
     * {@code MoveControl.speedModifier}，不读 {@code Attributes.MOVEMENT_SPEED} /
     * {@code FLYING_SPEED}——那两个属性在 {@link #applyStats()} 里照常设置，但对位移无影响）。
     *
     * <p><b>语义：乘区，非格/tick。</b>{@code FairyMoveControl} 是加速度模型
     * （每 tick 累加 accel，终速由 {@code FlyingMob.travel} 的阻力系数决定），
     * 故本值是相对于 accel 基线的倍率，不能直接读作格/秒。
     *
     * <p>子类可覆写以获得单只 BOSS 的独立移速。
     */
    protected double moveSpeed() {
        return BalanceNumbers.BOSS_MOVE_SPEED;
    }

    /**
     * 静态属性 supplier 的通用部分。
     *
     * <p>全部东方 BOSS 经由本工厂构建属性（无绕过路径），故在此声明的项对每只 BOSS 生效，
     * 新增 BOSS 只要复用本工厂即自动继承。
     *
     * <p>{@code KNOCKBACK_RESISTANCE = 1.0} = 完全击退免疫，覆盖近战、爆炸与活塞三类来源。
     * BOSS 被推动会破坏站桩输出节奏与弹幕走位，且允许玩家用原版武器推着 BOSS 走。
     */
    /**
     * 静态属性 supplier 的通用部分。
     *
     * <p>followRange <b>刻意不作为参数</b>：它 MUST 恒等于 {@link #LOCK_RADIUS}，
     * 否则就会出现「48~64 格内放弹幕但不追人」的双标准区间。早先它是第三个参数，
     * 于是四只残影里三只传 48、一只传 64，而弹幕锁定一直是硬编码的 64——
     * 三个数字互不相干。现在只有一个来源，且没有第二个入口能传别的值进来。
     *
     * <p>{@code KNOCKBACK_RESISTANCE = 1.0} = 完全击退免疫，覆盖近战、爆炸与活塞三类来源。
     * BOSS 被推动会破坏站桩输出节奏与弹幕走位，且允许玩家用原版武器推着 BOSS 走。
     */
    public static AttributeSupplier.Builder bossAttributes(double maxHealth, double damage) {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(ModAttributes.DANMAKU_DAMAGE, damage)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, LOCK_RADIUS);
    }


    // ------------------------------------------------------------------
    // 生命与伤害除数
    // ------------------------------------------------------------------

    /**
     * 有效生命 = 参照DPS × 秒。<b>确定值，无出生随机</b>（见 change {@code boss-stats-deterministic}）。
     *
     * <p>BOSS 是玩家要学习、复盘、反复挑战的一段内容，其时长与难度必须是可调的确定值；
     * 出生随机会让配平与实测无法归因，并使秒数目标失去意义。难度差异由 {@code bossHits}
     * 与轨道 {@code damageScale} 表达，而不是由随机寿命表达。
     */
    public double effectiveHp() {
        if (effectiveHp < 0.0D) {
            effectiveHp = MonsterStatBudget.referencePlayerDps(referenceTier())
                    * bossSeconds();
        }
        return effectiveHp;
    }

    /** 弹幕伤害除数：有效 HP = 实际 HP × 除数。 */
    public double damageScale() {
        double effective = effectiveHp();
        return effective <= VANILLA_MAX_HEALTH ? 1.0D : effective / VANILLA_MAX_HEALTH;
    }

    /**
     * 单发弹幕伤害 —— <b>读属性，不做计算</b>。
     *
     * <p>这是调弹幕平衡的<b>唯一旋钮</b>：{@code /attribute} 改一次，该 BOSS 全部符卡
     * 同步缩放，不碰配置、不用重开、不用改代码。属性默认值由 {@link #baseDanmakuDamage()}
     * 播种。
     */
    public float danmakuDamage() {
        return (float) this.getAttributeValue(ModAttributes.DANMAKU_DAMAGE);
    }

    /**
     * 属性基准值：参照玩家 EHP ÷ 参照挨弹数。
     *
     * <p>{@code hits} 现在只决定<b>倍率 1.0 的那些轨道</b>打多少；靠后的符卡靠
     * {@link com.bitsson.gensokyou.danmaku.track.Track#damageScale()} 往上加压，
     * 而不是各自重算一套伤害——加弹数会撞上 R3 密度预算。
     */
    public double baseDanmakuDamage() {
        return MonsterStatBudget.referencePlayerEhp(referenceTier()) / Math.max(1, bossHits());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(ModDamageTypes.DANMAKU)) {
            double scale = damageScale();
            if (scale > 1.0D) {
                amount /= (float) scale;
            }
        }
        return super.hurt(source, amount);
    }

    private void applyStats() {
        this.effectiveHp = -1.0D;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(
                Math.min(VANILLA_MAX_HEALTH, effectiveHp()));
        this.setHealth(this.getMaxHealth());
        // 先播种弹幕属性，再把它同步到近战属性（顺序不能反）
        this.getAttribute(ModAttributes.DANMAKU_DAMAGE).setBaseValue(baseDanmakuDamage());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(
                this.getAttributeValue(ModAttributes.DANMAKU_DAMAGE));
        double speed = 0.3D * Math.max(0.05D, moveSpeed());
        this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(speed);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        if (!this.statsApplied) {
            this.statsApplied = true;
            applyStats();
        }
        if (programContentId().isPresent()) {
            tickProgramBackend();
            if (this.getTarget() == null && !lockedTargets.isEmpty()) {
                this.setTarget(lockedTargets.get(0));
            }
            refreshTargets();
            faceRandomTarget();
            if (programBackend != null && programBackend.active()) {
                programBackend.tick(hpFraction(), this.level().getGameTime());
                announceProgramCard();
            }
            bossBar.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
            return;
        }
        if (this.getTarget() == null && !lockedTargets.isEmpty()) {
            this.setTarget(lockedTargets.get(0));
        }
        refreshTargets();
        faceRandomTarget();
        tickUnmigratedContent();
        bossBar.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
    }

    // ------------------------------------------------------------------
    // 正式程序后端（migrate-boss-cards-to-programs）
    //
    // 正式内容只运行程序战斗；安装失败报告并停用，不回退实体弹幕。
    // ------------------------------------------------------------------

    /**
     * 本 BOSS 的正式程序内容 id；返回空仅供排除迁移的占位子类。
     * 正式内容使用与实体和语言键同源的注册名。
     */
    protected Optional<String> programContentId() {
        return Optional.empty();
    }

    /** 仅供明确排除迁移的占位内容使用。 */
    protected void tickUnmigratedContent() {}

    private void tickProgramBackend() {
        if (programOpenFailed) return;
        if (programBackend != null) {
            if (!programBackend.active()) programBackend = null;
            return;
        }
        if (!(this.level() instanceof ServerLevel server)) return;
        String id = programContentId().orElse(null);
        if (id == null) return;
        BossProgramCards.Deck deck;
        try {
            deck = BossProgramCards.forOwner(id).orElse(null);
        } catch (RuntimeException invalidContent) {
            programOpenFailed = true;
            Gensokyou.LOGGER.error("[boss-program] invalid content for {}; encounter disabled", id, invalidContent);
            return;
        }
        if (deck == null) {
            programOpenFailed = true;
            Gensokyou.LOGGER.error("[boss-program] missing content for {}; encounter disabled", id);
            return;
        }
        try {
            programBackend = BossProgramBackend.open(server, getUUID(), deck, hpFraction(), server.getGameTime() + 1,
                    persistedBackend == null || !persistedBackend.programMode() ? 0 : persistedBackend.cardIndex());
            Gensokyou.LOGGER.info("[boss-program] opened owner={} content={} tick={}", getUUID(), id, server.getGameTime());
        } catch (RuntimeException failure) {
            programOpenFailed = true;
            programBackend = null;
            Gensokyou.LOGGER.error("[boss-program] failed to open {}; encounter disabled", id, failure);
        }
    }

    private double hpFraction() {
        return this.getMaxHealth() <= 0.0F ? 0.0D : this.getHealth() / this.getMaxHealth();
    }

    private void announceProgramCard() {
        if (programBackend == null) return;
        int index = programBackend.activeCardIndex();
        if (index < 1 || index - 1 == lastAnnouncedProgramCard) return;
        lastAnnouncedProgramCard = index - 1;
        onProgramCardChanged(index);
        ModNetworking.broadcastSpellCardName(this, index - 1);
        NetProfile.onCardChanged(this, index - 1);
    }

    /** 结束本场战斗的程序后端；死亡/移除/卸载时调用，不会影响同维度其他 BOSS。 */
    private void closeProgramBackend() {
        BossProgramBackend battle = programBackend;
        programBackend = null;
        if (battle != null) {
            try {
                battle.close();
            } catch (RuntimeException failure) {
                Gensokyou.LOGGER.warn("[boss-program] close failed for {}", getUUID(), failure);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        closeProgramBackend();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Only a real death/discard ends the battle. Chunk/dimension unload must NOT end it, so the
        // host can persist the live session for the next start (offline time is rebased on restore).
        if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) closeProgramBackend();
        super.remove(reason);
    }

    /**
     * 随机<b>看着</b>某一名被锁定的目标。
     *
     * <p>不是固定盯主目标：多人时 BOSS 视线在几人之间游走，读起来是「它知道你在场」，
     * 而不是「它认准了谁」。每 20 tick 换一个，给玩家反应时间。
     */
    private void faceRandomTarget() {
        if (lockedTargets.isEmpty()) {
            return;
        }
        if (tickCount % TARGET_REFRESH_TICKS == 0) {
            Player pick = lockedTargets.get(
                    getRandom().nextInt(Math.min(lockedTargets.size(), 5)));
            this.facingTarget = pick;
        }
        Player target = facingTarget != null && facingTarget.isAlive()
                ? facingTarget : lockedTargets.get(0);
        faceTowards(target);
        this.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
    }

    /** 程序符卡切换演出，index 从 1 开始。 */
    protected void onProgramCardChanged(int index) {}

    // ------------------------------------------------------------------
    // 多目标（≤5）
    // ------------------------------------------------------------------

    private void refreshTargets() {
        if (isSpellControlled()) {
            // 黑暗结界内：丢弃全部目标，且本 tick 不重新锁定
            if (!this.lockedTargets.isEmpty()) {
                this.lockedTargets = new ArrayList<>();
            }
            return;
        }
        if (tickCount - lastTargetScanTick < TARGET_REFRESH_TICKS) {
            return;
        }
        lastTargetScanTick = tickCount;
        int cap = Math.max(1, BalanceNumbers.BOSS_MAX_TARGETS);
        List<Player> found = new ArrayList<>();
        for (Player player : level().getEntitiesOfClass(Player.class,
                getBoundingBox().inflate(LOCK_RADIUS))) {
            if (player.isAlive() && !player.isSpectator() && !player.isCreative()) {
                found.add(player);
            }
        }
        found.sort(Comparator.comparingDouble(this::distanceToSqr));
        List<Player> capped = List.copyOf(found.subList(0, Math.min(cap, found.size())));
        if (!capped.equals(this.lockedTargets)) {
            this.lockedTargets = capped;
            for (Player player : capped) {
                if (player instanceof ServerPlayer server) {
                    bossBar.addPlayer(server);
                }
            }
        }
        if (!capped.isEmpty() && this.getTarget() == null) {
            this.setTarget(capped.get(0));
        }
    }

    public List<Player> lockedTargets() {
        return this.lockedTargets;
    }

    // ------------------------------------------------------------------
    // 移动：距离带 + 自由游走
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.getTarget() == null) {
            return;
        }
        if (movementLocked()) {
            // 位置锁死：清掉游走意图并把速度归零，但保留朝向（faceRandomTarget 在 aiStep）。
            // MUST NOT 只是「不再重选」——那会让它靠惯性漂完最后一段再永久停住。
            this.wanderTarget = null;
            this.moveControl.setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (wanderTarget == null
                || tickCount >= wanderRepickAt
                || this.position().distanceToSqr(wanderTarget) < RECOVER_RADIUS_SQR) {
            steerNow();
        }
        if (wanderTarget == null) {
            return;
        }
        double y = Mth.clamp(wanderTarget.y,
                hoverBase() + hoverFloor(), hoverBase() + hoverCeiling());
        // speedModifier MUST 来自 moveSpeed()（= config bossMoveSpeed）。
        // 曾经传字面量 1.0D，使 config 完全失效、BOSS 实际跑在约 10~12 格/秒。
        this.moveControl.setWantedPosition(wanderTarget.x, y, wanderTarget.z, moveSpeed() * spellSpeedFactor());
    }

    /**
     * 重选游走点（仅服务端）。目标点经 {@link SynchedEntityData} 同步给客户端，
     * 两端各自算出同一条运动学——不需要自定义包，也不会回弹。
     */
    private void steerNow() {
        LivingEntity primary = getTarget();
        if (primary == null) {
            return;
        }
        BossSteering.Choice choice = BossSteering.pick(
                this.level(), this.position(), this.getDeltaMovement(), this.getTarget().position(),
                lockedTargets, anchor, policy());
        wanderTarget = choice.point();
        wanderRepickAt = tickCount + Math.max(5, policy().repickTicks());
    }

    /**
     * 巡航空域带的基准高度：召唤锚点（祭坛核心）所在高度。
     *
     * <p>取锚点而非玩家——<b>BOSS 有自己的飞行高度</b>，玩家飞高它不追、玩家降落它不降。
     * 跟随玩家高度会让垂直轴这一维的躲避解等于不存在。
     */
    private double hoverBase() {
        return anchor != null ? anchor.y : this.getY();
    }

    /** 本 BOSS 的选点参数。子类可覆写以改变压迫感。 */
    protected BossSteering.Policy policy() {
        return BossSteering.Policy.of(moveMin(), moveMax(), hoverFloor(), hoverCeiling(),
                BalanceNumbers.BOSS_WANDER_AVOID_PLAYER,
                BalanceNumbers.BOSS_WANDER_AVOID_ANCHOR);
    }

    /** 距目标点这么近就提前重选，避免在一点上来回摆。 */
    private static final double RECOVER_RADIUS_SQR = 2.25D;

    /**
     * 移动锁：{@code true} 时<b>位置锁死</b>，不产生任何游走位移。
     *
     * <p>正式程序战斗的锁由控制轨声明（{@code lockMovement}）并在此读取。
     */
    protected boolean movementLocked() {
        BossProgramBackend program = programBackend;
        if (program != null && program.active()) {
            return program.movementLocked();
        }
        return false;
    }

    /** 记录召唤锚点，供游走点排除用。 */
    public void setAnchor(Vec3 anchor) {
        this.anchor = anchor;
    }

    /** 面向给定实体。GeckoLib 用 {@code yBodyRot} 渲染，必须显式写 yaw。 */
    public void faceTowards(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (dx * dx + dz * dz < 1.0E-6D) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
    }

    // ------------------------------------------------------------------
    // 血条
    // ------------------------------------------------------------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossBar.setName(this.getDisplayName());
    }

    // ------------------------------------------------------------------
    // 掉落
    // ------------------------------------------------------------------

    /** 新签名素材标签：只有标签成员才由击杀保底掉落（复用既有物的信物不在此列）。 */
    private static final TagKey<Item> BOSS_SIGNATURE_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("gensokyou", "boss_signature"));

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        dropStarFragments(level);
        dropSignature(level);
        dropThemedCard(level);
    }

    /**
     * 击杀保底掉落主题信物——仅限 {@code #gensokyou:boss_signature} 标签成员。
     *
     * <p>有主题归属但信物不在标签内的 BOSS（当前无）不掉；未声明归属者亦不掉。
     */
    protected void dropSignature(ServerLevel level) {
        int count = Math.max(0, BalanceNumbers.SIGNATURE_DROP_COUNT);
        if (count <= 0) {
            return;
        }
        signatureItem()
                .filter(item -> item.builtInRegistryHolder().is(BOSS_SIGNATURE_TAG))
                .ifPresent(item -> this.spawnAtLocation(new ItemStack(item, count)));
    }

    /** 低概率直掉主题成品道具卡（config 概率，0 关闭）。 */
    protected void dropThemedCard(ServerLevel level) {
        double chance = BalanceNumbers.BOSS_CARD_DROP_CHANCE;
        if (chance <= 0D || this.getRandom().nextDouble() >= chance) {
            return;
        }
        themedCard().ifPresent(item -> this.spawnAtLocation(new ItemStack(item)));
    }

    /** 四只召唤 BOSS 全部掉碎符卡星；数量可覆写。 */
    protected void dropStarFragments(ServerLevel level) {
        int count = starDropCount();
        if (count > 0) {
            this.spawnAtLocation(new ItemStack(
                    com.bitsson.gensokyou.registry.ModItems.BROKEN_SPELL_CARD_STAR.get(), count));
        }
    }

    /** 单只 BOSS 的碎符卡星掉落量。子类覆写。 */
    protected int starDropCount() {
        int min = Math.max(0, BalanceNumbers.BOSS_STAR_DROP_MIN);
        int max = Math.max(min, BalanceNumbers.BOSS_STAR_DROP_MAX);
        return min + getRandom().nextInt(max - min + 1);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.anchor != null) {
            tag.putDouble("AnchorX", this.anchor.x);
            tag.putDouble("AnchorY", this.anchor.y);
            tag.putDouble("AnchorZ", this.anchor.z);
        }
        if (programContentId().isPresent()) {
            new com.bitsson.gensokyou.danmaku.program.gameplay.ProgramBackendState(
                    true, Math.max(0, this.lastAnnouncedProgramCard + 1)).write(tag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // ⚠️ statsApplied 本身不落 NBT，但它<b>必须</b>在读档后置 true。
        //
        // 它不落 NBT 是对的（它不是数据，是"这个实体已经被改造过了吗"的标记），
        // 但标记本身在内存里，reload 就回到 false，于是首个 aiStep 会重跑 applyStats()，
        // 而 applyStats() 结尾是 setHealth(getMaxHealth()) —— 打了一半的 BOSS 直接满血。
        // 属性与最大生命此时已由 LivingEntity 从 NBT 的 "attributes" 恢复，读档路径
        // MUST NOT 再动它们。
        this.statsApplied = true;
        // 兼容迁移期间的旧标记；legacy 标记不再阻止正式内容使用程序战斗。
        this.persistedBackend = com.bitsson.gensokyou.danmaku.program.gameplay.ProgramBackendState.read(tag).orElse(null);
        this.programOpenFailed = false;
        // 旧存档可能残留 "BossSpawnRoll" 键——忽略即可（BOSS 数值已确定化，不再读它）。
        if (tag.contains("AnchorX")) {
            this.anchor = new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"),
                    tag.getDouble("AnchorZ"));
        }
    }

    // ------------------------------------------------------------------
    // 调试用覆写（/gs_boss）
    // ------------------------------------------------------------------

    private Double debugSeconds;
    private Integer debugHits;

    /** 调试：临时改本 BOSS 的战斗秒数（不落 NBT，重进世界即失效）。 */
    public void debugSetSeconds(double seconds) {
        this.debugSeconds = seconds;
    }

    /** 调试：临时改本 BOSS 的挨弹数。 */
    public void debugSetHits(int hits) {
        this.debugHits = hits;
    }

    /** 调试：重跑一次属性应用。 */
    public void debugApplyStats() {
        this.statsApplied = true;
        // 不重建攻击执行器；调试属性与战斗生命周期分离。
        applyStats();
    }

    /** 调试覆写优先，其次走子类的 config 读数。 */
    protected double bossSeconds() {
        return debugSeconds != null ? debugSeconds : configSeconds();
    }

    /** 调试覆写优先，其次走子类的 config 读数。 */
    protected int bossHits() {
        return debugHits != null ? debugHits : configHits();
    }
}
