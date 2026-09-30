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
        /**
         * 每次重复的角度推进（度）。
         *
         * <p>取 {@code double}：「一圈 48 颗逐颗点亮」需要每拍推进 {@code 360/48 = 7.5°}，
         * 取整会把整圈扫成 {@code 8° × 48 = 384°}，多扫一整格。
         */
        double phaseStepDeg,
        double damageScale,
        List<Beat> beats,
        /**
         * 本轨的编队帧声明（需求②③④）。
         *
         * <p><b>编队是轨级而非拍级</b>：一批弹来自同一轨的多次发射，它们必须共享同一份
         * 编队帧。若挂到每一拍，同一条轨的 20 次重复发射就各算一次出生偏移，
         * 队形在两次发射之间就断了。
         *
         * <p>默认 {@link Behaviour.Formation#NONE}：绝大多数轨道不需要编队。
         */
        Behaviour.Formation formation,
        /** 视觉独占标识：四项各一个量化档位，供 lint 断言。 */
        VisualIdentity identity) {


    /**
     * 视觉独占标识：五项各一个量化档位，供 lint 断言。
     *
     * <p>spec 列的四轴是「色相 / 速度 / 尺寸 / <b>行为</b>」，而改前的实现只有
     * {@code colorStep / speedStep / sizeStep / shapeIndex}——<b>行为维度从未参与断言</b>，
     * 且「形状」不在 spec 的四轴里。行为解耦后这层错位一并修正：形状与行为各自成轴，
     * 「悬停的环」与「静止的环」因此能被 lint 区分开。
     */
    public record VisualIdentity(int colorStep, int speedStep, int sizeStep,
                                 int shapeIndex, int behaviourIndex) {
    }

    /**
     * 一拍：{@code tick} 时刻发一次 {@code shape}，并施加 {@code behaviour}。
     *
     * <p><b>几何 × 行为</b>：{@code shape} 决定「发哪些弹、在什么方位」，
     * {@code behaviour} 决定「这些弹怎么动、怎么显、怎么死」。二者正交，
     * 任何行为可与任何几何组合。
     *
     * <p>{@code phaseStepDeg} 让同轨的重复拍之间错开角度（环自转、扇推进）。
     *
     * @param lifetimeTicks 本批弹的寿命（tick）。{@code 0} = 沿用弹体默认寿命。
     *        <b>存在的理由</b>：默认寿命是 60 秒，而「每 N 秒发一批」的编排会按
     *        {@code N × 60} 累积在场弹数，几批就撞上实体硬上限。有了它，
     *        「一批弹在存活若干秒后消失」才能被编排表达。
     * @param harmlessTicks 本批弹在多少 tick 内<b>不对任何玩家生效</b>。
     *        <b>存在的理由</b>：它是全向无约束随机唯一被接受的兑现方式——
     *        「生成点在不在视野锥内」不影响玩家能否公平躲避，只要弹在开火前够久不生效，
     *        玩家就有完整的观察窗口（见 {@code TrackLint} 的 R1 判据）。
     * @param anchor 生成点的世界锚定方式。几何层 MUST NOT 接触世界，
     *        故「往下找地面 / 找第一个空气」由翻译层按本字段求值。
     * @param centreDamage 本批里那颗<b>中心弹</b>的伤害乘数（仅 {@link Shape#FLOWER} 读）。
     * @param centreSize 本批里那颗<b>中心弹</b>的直径乘数（仅 {@link Shape#FLOWER} 读）。
     */
    public record Beat(int tick, Shape shape, Shape.Params params, Behaviour behaviour,
                       TargetMode targetMode, Projectile projectile,
                       int lifetimeTicks, int harmlessTicks,
                       SpawnAnchor anchor, double centreDamage, double centreSize) {

        /** 生成点锚定方式。 */
        public enum SpawnAnchor {
            /** 不锚定：生成点即几何给出的点。 */
            NONE,
            /** 从生成点<b>向下</b>找第一个空气块，把生成点放在那里（「头顶 60 格下雨」）。 */
            FIRST_AIR_BELOW,
            /** 从生成点<b>向下</b>找地面，把生成点放在地面之上（「从地面向上打激光」）。 */
            GROUND_BELOW,
            /**
             * 先取一名被锁定玩家的 xz 柱，再向下找地面。
             *
             * <p>「每 5 次至少一次压在玩家头上」这条规则因此是<b>数据</b>而不是代码里的
             * 计数器：编排表把每第 5 拍标成这个值，其余拍用随机点。
             */
            PLAYER_GROUND
        }

        /** 球弹的简写构造（绝大多数拍）。 */
        public Beat(int tick, Shape shape, Shape.Params params, Behaviour behaviour,
                    TargetMode targetMode) {
            this(tick, shape, params, behaviour, targetMode, Projectile.SPHERE,
                    0, 0, SpawnAnchor.NONE, 1.0D, 1.0D);
        }

        /** 带弹种的构造。 */
        public Beat(int tick, Shape shape, Shape.Params params, Behaviour behaviour,
                    TargetMode targetMode, Projectile projectile) {
            this(tick, shape, params, behaviour, targetMode, projectile,
                    0, 0, SpawnAnchor.NONE, 1.0D, 1.0D);
        }

        /** 该拍是否声明了「开火前不生效」的窗口。 */
        public boolean hasHarmlessWindow() {
            return harmlessTicks > 0;
        }

        /** 行为维度标识档位，供 {@link VisualIdentity} 断言「同符卡内两轨行为不重复」。 */
        public int behaviourIndex() {
            return behaviour.motion().kind().ordinal() * 100
                    + (behaviour.split().active() ? 10 : 0)
                    + (behaviour.visibility().periodicallyHarmless() ? 1 : 0);
        }
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
        private double phaseStepDeg = 0.0D;
        private double damageScale = 1.0D;
        private int colorStep = 0;
        private int speedStep = 0;
        private int sizeStep = 0;
        private Behaviour.Formation formation = Behaviour.Formation.NONE;
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

        /**
         * 每次重复的角度推进（度）。环自转 / 扇推进。
         *
         * <p>取 {@code double} 而非 {@code int}：「一圈 48 颗逐颗点亮」需要每拍推进
         * {@code 360/48 = 7.5°}，取整会把整圈扫成 8°×48 = 384°，多扫 24°。
         */
        public Builder phaseStep(double degrees) {
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

        /**
         * 给本轨挂一个编队装置。本轨所有拍的弹都会挂上它。
         *
         * <p>每条轨<b>至多一个</b>装置，故重复调用是「覆盖」而非「追加」——
         * 装置数与轨道数同阶，这正是需求「装置数不超过 1~3」的字面含义。
         */
        public Builder formation(Behaviour.Formation formation) {
            this.formation = formation == null ? Behaviour.Formation.NONE : formation;
            return this;
        }

        public Builder at(int tick, Shape shape, Shape.Params params, TargetMode mode) {
            return at(tick, shape, params, Behaviour.NONE, mode);
        }

        /** 完整形式：几何参数 + 行为 + 目标模式。 */
        public Builder at(int tick, Shape shape, Shape.Params params, Behaviour behaviour,
                          TargetMode mode) {
            return at(tick, shape, params, behaviour, mode, Projectile.SPHERE);
        }

        /** 带弹种的完整形式。 */
        public Builder at(int tick, Shape shape, Shape.Params params, Behaviour behaviour,
                          TargetMode mode, Projectile projectile) {
            return at(tick, shape, params, behaviour, mode, projectile,
                    0, 0, Beat.SpawnAnchor.NONE, 1.0D, 1.0D);
        }

        /** 激光版简写：几何 + 弹种，不带行为。 */
        public Builder laserAt(int tick, Shape shape, Shape.Params params, Projectile laser,
                              TargetMode mode) {
            return at(tick, shape, params, Behaviour.NONE, mode, laser);
        }

        /** 无行为、无几何参数的简写。 */
        public Builder at(int tick, Shape shape, TargetMode mode) {
            return at(tick, shape, Shape.Params.defaults(), Behaviour.NONE, mode);
        }

        /**
         * 完整形式：几何参数 + 行为 + 目标模式 + 弹种 + 寿命 + 无害窗口 + 锚定 + 中心弹旋钮。
         *
         * <p>后五项都有「零值即沿用既有语义」的缺省，故绝大多数拍仍用较短的重载。
         */
        public Builder at(int tick, Shape shape, Shape.Params params, Behaviour behaviour,
                          TargetMode mode, Projectile projectile, int lifetimeTicks,
                          int harmlessTicks, Beat.SpawnAnchor anchor,
                          double centreDamage, double centreSize) {
            this.beats.add(new Beat(tick, shape, params, behaviour, mode,
                    projectile == null ? Projectile.SPHERE : projectile,
                    Math.max(0, lifetimeTicks), Math.max(0, harmlessTicks),
                    anchor == null ? Beat.SpawnAnchor.NONE : anchor,
                    Math.max(0.0D, centreDamage), Math.max(0.0D, centreSize)));
            return this;
        }

        /** 带寿命与无害窗口的简写（最常用的一档扩展）。 */
        public Builder at(int tick, Shape shape, Shape.Params params, Behaviour behaviour,
                          TargetMode mode, int lifetimeTicks, int harmlessTicks) {
            return at(tick, shape, params, behaviour, mode, Projectile.SPHERE,
                    lifetimeTicks, harmlessTicks, Beat.SpawnAnchor.NONE, 1.0D, 1.0D);
        }

        public Track build() {
            // 形状轴与行为轴由首个拍自动导出：符卡作者只需声明色相/速度/尺寸三档，
            // 「用哪个几何、带什么行为」是派生的，不该手写重复。
            int shapeIndex = beats.isEmpty() ? 0 : beats.get(0).shape().ordinal();
            int behaviourIndex = beats.isEmpty() ? 0 : beats.get(0).behaviourIndex();
            return new Track(name, color, terminates, repeatEvery, phaseStepDeg, damageScale,
                    beats.stream().sorted(Comparator.comparingInt(Beat::tick)).toList(),
                    formation,
                    new VisualIdentity(colorStep, speedStep, sizeStep, shapeIndex, behaviourIndex));
        }
    }
}
