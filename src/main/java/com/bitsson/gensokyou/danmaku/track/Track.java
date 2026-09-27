package com.bitsson.gensokyou.danmaku.track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 一条轨道 = 一张符卡内的一条独立节拍时间轴。
 *
 * <p>轨道是本 mod 弹幕编排的最小单位：<b>一张符卡 = 一个阶段 = 1~3 条并发轨道</b>。
 * 轨道之间互不等待、不同步，各自按自己的 tick 推进。
 *
 * <p><b>无终止轨道</b>（{@code terminates = false}）是「缺結」型符卡的机制载体：
 * 它的拍会无限重复，<b>不</b>存在到点收束的分支。
 *
 * <p><b>视觉独占标识</b>：构造时必须给一个 {@code identity} 描述（色相/速度/尺寸/行为），
 * 同符卡内两轨的 identity 组合 MUST 至少有一项不同（由 {@link TrackLint} 静态校验）。
 * 色相由 {@link #color()} 给出，取自该 BOSS 的 {@link SignaturePalette}。
 */
public record Track(
        String name,
        int color,
        boolean terminates,
        int repeatEvery,
        int phaseStepDeg,
        double damageScale,
        List<Beat> beats,
        /** 视觉独占标识：四项各一个量化档位，供 lint 断言。 */
        VisualIdentity identity) {

    /** 标识量化档位：colorStep / speedStep / sizeStep / shapeIndex。 */
    public record VisualIdentity(int colorStep, int speedStep, int sizeStep, int shapeIndex) {
    }

    /**
     * 一拍：{@code tick} 时刻发一次 {@code shape}。
     *
     * <p>{@code phaseStepDeg} 让同轨的重复拍之间错开角度（环自转、扇推进）。
     */
    public record Beat(int tick, Shape shape, Shape.Params params, TargetMode targetMode) {
    }

    public static Builder of(String name, int color) {
        return new Builder(name, color);
    }

    /** 轨道是否在当前 tick 有一拍要发。 */
    public boolean firesAt(int tick) {
        for (Beat beat : beats) {
            if (beat.tick() == tick) {
                return true;
            }
        }
        return false;
    }

    /** 当前 tick 该轨道的累计相位（度）。无终止轨道靠它自转。 */
    public double phaseAt(int tick) {
        if (phaseStepDeg == 0) {
            return 0.0D;
        }
        if (terminates) {
            return phaseStepDeg * (tick / Math.max(1, repeatEvery));
        }
        return phaseStepDeg * (tick / Math.max(1, repeatEvery));
    }

    /** 构造器。校验放在 {@link TrackLint}，此处不抛异常以便加载期容错。 */
    public static final class Builder {
        private final String name;
        private final int color;
        private boolean terminates = true;
        private int repeatEvery = 0;
        private int phaseStepDeg = 0;
        private double damageScale = 1.0D;
        private int colorStep = 0;
        private int speedStep = 0;
        private int sizeStep = 0;
        private final List<Beat> beats = new ArrayList<>();

        private Builder(String name, int color) {
            this.name = name;
            this.color = color;
        }

        /** 声明本轨有终止条件。缺結型 MUST NOT 调用。 */
        public Builder terminates() {
            this.terminates = true;
            return this;
        }

        /** 声明本轨无终止条件（缺結型）。 */
        public Builder endless() {
            this.terminates = false;
            return this;
        }

        /** 拍之间的周期（tick）。0 = 只有显式拍，无重复。 */
        public Builder repeatEvery(int ticks) {
            this.repeatEvery = Math.max(0, ticks);
            return this;
        }

        /** 每次重复的角度推进（度）。环自转 / 扇推进。 */
        public Builder phaseStep(int degrees) {
            this.phaseStepDeg = degrees;
            return this;
        }

        /**
         * 本轨道的伤害倍率，作用在 BOSS 的 {@code danmaku_damage} 属性之上。
         *
         * <p>单发伤害 = 属性值 × 本倍率。所以调平衡有两层：改属性缩放<b>整只 BOSS</b>，
         * 改倍率缩放<b>这一条轨道</b>（用于让靠后的符卡更疼，而不靠加弹数——
         * 加弹数会撞上 R3 密度预算）。
         */
        public Builder damageScale(double scale) {
            this.damageScale = Math.max(0.0D, scale);
            return this;
        }

        public Builder identity(int colorStep, int speedStep, int sizeStep) {
            this.colorStep = colorStep;
            this.speedStep = speedStep;
            this.sizeStep = sizeStep;
            return this;
        }

        public Builder at(int tick, Shape shape, Shape.Params params, TargetMode mode) {
            this.beats.add(new Beat(tick, shape, params, mode));
            return this;
        }

        public Builder at(int tick, Shape shape, TargetMode mode) {
            return at(tick, shape, Shape.Params.defaults(), mode);
        }

        public Track build() {
            int shapeIndex = beats.isEmpty() ? 0 : beats.get(0).shape().ordinal();
            return new Track(name, color, terminates, repeatEvery, phaseStepDeg, damageScale,
                    beats.stream().sorted(Comparator.comparingInt(Beat::tick)).toList(),
                    new VisualIdentity(colorStep, speedStep, sizeStep, shapeIndex));
        }
    }
}
