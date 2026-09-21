package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.BalanceTestBossEntity;
import com.bitsson.gensokyou.entity.TestBossPattern;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 测试 BOSS 的弹幕编排：每 {@code testPatternIntervalTicks}（默认 3 秒）随机切换一种模式，
 * 按生命阈值分三阶段（模式池 / 间隔 / 弹速）。
 */
public class TestBossPatternGoal extends Goal {

    private final BalanceTestBossEntity boss;
    private TestBossPattern current;
    private int slotTick;
    private int lastOrdinal = -1;
    private int lastPhase = 1;

    public TestBossPatternGoal(BalanceTestBossEntity boss) {
        this.boss = boss;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.current = null;
        this.slotTick = 0;
    }

    @Override
    public void stop() {
        this.current = null;
    }

    @Override
    public void tick() {
        int phase = phase();
        if (phase != this.lastPhase) {
            this.lastPhase = phase;
            this.boss.triggerCast();
            LivingEntity target = this.boss.getTarget();
            if (target instanceof net.minecraft.world.entity.player.Player player) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.test_boss_phase", phase), true);
            }
        }
        int interval = intervalTicks(phase);
        if (this.current == null) {
            this.current = pick(phase);
            this.slotTick = 0;
            this.boss.triggerCast();
        }
        this.current.fire(this.boss, this.slotTick, interval, speedMult(phase));
        this.slotTick++;
        if (this.slotTick >= interval) {
            this.current = null;
        }
    }

    private int phase() {
        float max = Math.max(1F, this.boss.getMaxHealth());
        float fraction = this.boss.getHealth() / max;
        if (fraction <= GensokyouConfig.TEST_PHASE3_HP.get().floatValue()) {
            return 3;
        }
        if (fraction <= GensokyouConfig.TEST_PHASE2_HP.get().floatValue()) {
            return 2;
        }
        return 1;
    }

    private int intervalTicks(int phase) {
        double base = GensokyouConfig.TEST_PATTERN_INTERVAL_TICKS.get();
        double mult = switch (phase) {
            case 2 -> GensokyouConfig.TEST_PHASE2_INTERVAL_MULT.get();
            case 3 -> GensokyouConfig.TEST_PHASE3_INTERVAL_MULT.get();
            default -> 1.0D;
        };
        return Math.max(5, (int) Math.round(base * mult));
    }

    private double speedMult(int phase) {
        return switch (phase) {
            case 2 -> GensokyouConfig.TEST_PHASE2_SPEED_MULT.get();
            case 3 -> GensokyouConfig.TEST_PHASE3_SPEED_MULT.get();
            default -> 1.0D;
        };
    }

    private int[] pool(int phase) {
        return switch (phase) {
            case 2 -> new int[]{3, 4, 5, 6, 7, 8};
            case 3 -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            default -> new int[]{0, 1, 2, 3, 4};
        };
    }

    private TestBossPattern pick(int phase) {
        int[] pool = pool(phase);
        int index = this.boss.getRandom().nextInt(pool.length);
        int ordinal = pool[index];
        if (pool.length > 1 && ordinal == this.lastOrdinal) {
            ordinal = pool[(index + 1) % pool.length];
        }
        this.lastOrdinal = ordinal;
        return TestBossPattern.values()[ordinal];
    }
}
