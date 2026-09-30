package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹道形式分类、读档自愈判据与速度持久化（{@code danmaku-timeline-sync} 任务 2.1~2.5）。
 *
 * <p>这些断言守的是一个具体缺陷：一批弹在存档重进后<b>原地冻结</b>约 60 秒再按寿命消失。
 * 根因是 {@code deltaMovement} 从不入盘，而位置是否需要它<b>按弹种而异</b> ——
 * 编队弹与速率曲线弹不需要，直线弹与曲射弹需要。判据写错一个方向，
 * 症状就从「该修的没修」变成「给不该有的弹安上一个不存在的速度」。
 */
class DanmakuTrackKindsTest {

    private static final double EPS = 1.0E-9D;

    // ------------------------------------------------------------------
    // 弹道形式
    // ------------------------------------------------------------------

    @Test
    void formationAndProfileAreClosedForm() {
        assertEquals(DanmakuTrackKinds.Form.CLOSED_FORM,
                DanmakuTrackKinds.formOf(false, false, true), "编队帧 rig 每 tick 覆写位置");
        assertEquals(DanmakuTrackKinds.Form.CLOSED_FORM,
                DanmakuTrackKinds.formOf(false, true, false),
                "速率曲线的方向与速率都能从已持久化参数重建");
        assertEquals(DanmakuTrackKinds.Form.CLOSED_FORM,
                DanmakuTrackKinds.formOf(true, true, false), "两者兼有时取更强的那个");
    }

    @Test
    void straightAndCurveOnlyAreIncremental() {
        assertEquals(DanmakuTrackKinds.Form.INCREMENTAL,
                DanmakuTrackKinds.formOf(false, false, false), "直线弹是 pos += v");
        assertEquals(DanmakuTrackKinds.Form.INCREMENTAL,
                DanmakuTrackKinds.formOf(true, false, false),
                "曲射把旋转作用在上一步的速度上，没有闭式");
    }

    // ------------------------------------------------------------------
    // 读档自愈
    // ------------------------------------------------------------------

    @Test
    void straightAndCurveNeedVelocityPersisted() {
        assertTrue(DanmakuTrackKinds.needsVelocityPersistence(false, false, false),
                "直线弹缺速度即冻结 —— 四只 BOSS 普通弹全是这一类");
        assertTrue(DanmakuTrackKinds.needsVelocityPersistence(true, false, false),
                "曲射弹 rotateAbout(零向量) 恒为零，同样冻结");
    }

    @Test
    void formationAndProfileSurviveWithoutVelocity() {
        assertFalse(DanmakuTrackKinds.needsVelocityPersistence(false, false, true),
                "编队弹由 rig 覆写位置，不需要也不该有速度");
        assertFalse(DanmakuTrackKinds.needsVelocityPersistence(true, true, false),
                "曲射 + 速率曲线时 alongAxis 会回落到已持久化的方向轴，能自愈");
    }

    /**
     * 「形式」与「是否需要速度」是两个判据，不可互相替代。
     *
     * <p>反例就是曲射 + 速率曲线：它按 {@link DanmakuTrackKinds.Form#CLOSED_FORM} 归类
     * （因为有 profile），但自愈能力来自 profile 而非形式本身。若有人把两个判据
     * 合并成一个，这枚弹就会在读档后冻结。
     */
    @Test
    void curveWithProfileIsClosedFormYetStillSelfHeals() {
        assertEquals(DanmakuTrackKinds.Form.CLOSED_FORM,
                DanmakuTrackKinds.formOf(true, true, false));
        assertTrue(DanmakuTrackKinds.survivesReloadWithoutVelocity(true, true, false),
                "它必须靠 profile 自愈，而不是靠形式标签");
    }

    // ------------------------------------------------------------------
    // 速度持久化的 NBT 往返
    // ------------------------------------------------------------------

    @Test
    void velocityRoundTripsExactly() {
        CompoundTag tag = new CompoundTag();
        Vec3 velocity = new Vec3(0.35D, -0.125D, 0.7D);
        DanmakuTrackKinds.writeVelocity(tag, true, velocity);
        Vec3 restored = DanmakuTrackKinds.readVelocity(tag, true);
        assertEquals(velocity.x, restored.x, 0.0D, "逐项 putDouble，不该被量化再吃一次");
        assertEquals(velocity.y, restored.y, 0.0D);
        assertEquals(velocity.z, restored.z, 0.0D);
    }

    /**
     * 不需要的弹种 MUST NOT 写盘。
     *
     * <p>写进去不只是浪费 6 个键：读的人会以为速度是编队弹的权威，而它其实由
     * {@code positionAt(age)} 决定 —— 两个真相源，迟早有一个赢而另一个开始撒谎。
     */
    @Test
    void unneededKindsWriteNothing() {
        CompoundTag tag = new CompoundTag();
        DanmakuTrackKinds.writeVelocity(tag, false, new Vec3(1, 2, 3));
        assertFalse(tag.contains(DanmakuTrackKinds.KEY_VELOCITY),
                "不需要速度的弹种 MUST NOT 出现速度键");
        assertEquals(Vec3.ZERO, DanmakuTrackKinds.readVelocity(tag, false),
                "读侧同样按判据跳过，MUST NOT 因为键存在就采用");
    }

    /**
     * 旧存档缺键 MUST 退化成「继续冻结」，而不是报错或读出别的东西。
     *
     * <p>不比迁移前更差是硬要求：本变更之前所有弹都不写速度，所以任何一份旧存档
     * 都没有这个键。若缺键时抛异常，读档会直接失败 —— 那比冻结严重得多。
     */
    @Test
    void missingKeyDegradesToTheOldBehaviour() {
        CompoundTag empty = new CompoundTag();
        assertEquals(Vec3.ZERO, DanmakuTrackKinds.readVelocity(empty, true),
                "旧存档 MUST 退化成零速度，即本变更之前的行为");
        assertEquals(Vec3.ZERO, DanmakuTrackKinds.readVelocity(null, true),
                "null 标签 MUST NOT 抛异常");
    }

    /**
     * 残缺存档逐项降级，而不是整体丢弃。
     *
     * <p>写侧是逐项 {@code putDouble} 的。若读侧要求三个键齐全，一份只丢了 Z 的
     * 存档就会退化成完全冻结，且不留任何痕迹 —— 而「半速飞行」比「完全冻住」
     * 更容易被误认为别的 bug。
     */
    @Test
    void partialTagDegradesPerComponent() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(DanmakuTrackKinds.KEY_VELOCITY, 0.5D);
        tag.putDouble(DanmakuTrackKinds.KEY_VELOCITY + "Y", 0.25D);
        Vec3 restored = DanmakuTrackKinds.readVelocity(tag, true);
        assertEquals(0.5D, restored.x, 0.0D);
        assertEquals(0.25D, restored.y, 0.0D);
        assertEquals(0.0D, restored.z, 0.0D, "缺失分量 MUST 逐项降级为 0");
    }

    @Test
    void nullVelocityIsNotWritten() {
        CompoundTag tag = new CompoundTag();
        DanmakuTrackKinds.writeVelocity(tag, true, null);
        assertFalse(tag.contains(DanmakuTrackKinds.KEY_VELOCITY));
    }

    // ------------------------------------------------------------------
    // 超越函数纪律（任务 2.2）
    // ------------------------------------------------------------------

    /**
     * 速率曲线与编队帧 MUST NOT 引入超越函数。
     *
     * <p>纪律的理由是<b>双端各自求值</b>：客户端与服务端跑同一段代码，只有当这段代码
     * 只用四则运算与取绝对值时，才能保证跨平台逐位一致。偷偷加一个 {@code Math.sin}
     * 不会立刻出错，但两端的弹道会从某一 tick 起分家 —— 而那正是本项目最难归因的
     * 一类症状（见 {@code docs/danmaku-sync-architecture-and-open-problems.md}）。
     *
     * <p>检查方式是扫编译后的常量池：{@code sin}/{@code cos} 作为<b>独立</b> UTF8
     * 条目出现，即说明有方法引用。{@code using} 这类包含它们的普通标识符长度不同，
     * 不会被误判。
     */
    @Test
    void speedProfileAndFormationFrameStayTranscendentalFree() throws IOException {
        for (String className : List.of(
                "com/bitsson/gensokyou/danmaku/motion/DanmakuSpeedProfile",
                "com/bitsson/gensokyou/danmaku/motion/FormationFrame")) {
            List<String> found = methodRefsInConstantPool(className);
            assertTrue(found.isEmpty(),
                    className + " 引入了超越函数 " + found
                            + " —— 双端各自求值时 MUST NOT 依赖 sin/cos 的跨平台一致性");
        }
    }

    /**
     * Rodrigues 旋转是<b>已记录</b>的例外，且不得扩大。
     *
     * <p>它已在曲射路径上被双端使用，编队帧复用它不引入新的风险类别。所以这条断言
     * 不是「它不该有 sin/cos」，而是「它<b>确实</b>有，且仅此一处」—— 万一有人把
     * 纪律顺手扩展到 Rotation，弹道会立刻改变，而没人会想到原因是这个。
     */
    @Test
    void rotationIsTheOneRecordedException() throws IOException {
        List<String> found = methodRefsInConstantPool(
                "com/bitsson/gensokyou/danmaku/motion/Rotation");
        assertTrue(found.contains("cos") || found.contains("sin"),
                "Rotation 的 Rodrigues 旋转 MUST 仍用 sin/cos —— 若这条失败，"
                        + "说明它已被改写，编队弹轨迹会随之改变");
    }

    /** 扫编译后 class 的常量池，取出等于给定名字的 UTF8 条目。 */
    private static List<String> methodRefsInConstantPool(String className) throws IOException {
        byte[] bytes;
        try (InputStream in = DanmakuTrackKindsTest.class.getClassLoader()
                .getResourceAsStream(className + ".class")) {
            if (in == null) {
                throw new IOException("找不到已编译的类：" + className);
            }
            bytes = in.readAllBytes();
        }
        List<String> wanted = List.of("sin", "cos", "tan", "asin", "acos", "atan", "atan2",
                "sinh", "cosh", "tanh", "sqrt", "cbrt", "pow", "exp", "log");
        List<String> hits = new ArrayList<>();
        for (String name : wanted) {
            // 常量池 UTF8 条目：tag=0x01，随后两字节大端长度，再是内容。
            // 只匹配「长度正好等于名字长度」的条目，故 "using" 之类不会命中。
            byte[] n = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            for (int i = 0; i + 3 + n.length <= bytes.length; i++) {
                if ((bytes[i] & 0xFF) != 0x01) {
                    continue;
                }
                int len = ((bytes[i + 1] & 0xFF) << 8) | (bytes[i + 2] & 0xFF);
                if (len != n.length) {
                    continue;
                }
                boolean same = true;
                for (int k = 0; k < n.length; k++) {
                    if (bytes[i + 3 + k] != n[k]) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    hits.add(name);
                    break;
                }
            }
        }
        return hits;
    }

    // ------------------------------------------------------------------
    // 闭式求值（任务 2.3）
    // ------------------------------------------------------------------

    /**
     * 编队弹的位置 MUST 每 tick 从解析值重来，因此长时间运行不累积误差。
     *
     * <p>这是 rig 存在的<b>全部</b>理由：位置逐位落在 {@code positionAt(age)} 上，
     * 于是「误差不累积」不是近似而是恒等。若哪天有人把它改成「在上一 tick 位置上
     * 再叠加增量」，浮点误差就会开始单调增长 —— 而症状是弹道缓慢发散，
     * 几乎不可能被归因到这一行。
     */
    @Test
    void formationPositionIsClosedFormSoErrorDoesNotAccumulate() {
        // 参数序：center(3) / offset(3) / axisYaw / axisPitch / rotRate /
        //         scaleBase / scaleAmp / scalePeriod / orbitYaw / orbitPitch /
        //         orbitRadius / orbitRate
        FormationFrame frame = new FormationFrame(1.0D, 2.0D, 0.0D,
                3.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 2.0D,
                1.0D, 0.4D, 60.0D,
                0.0D, 0.0D, 0.0D, 0.0D);
        Vec3 axis = new Vec3(0, 0, 1);
        int first = 5;
        int last = 400;
        Vec3 atFirst = frame.positionAt(first, axis, 0.0D);
        // 隔很久之后再求同一个年龄：必须是同一个值，且与中间求过多少次无关。
        for (int t = first; t <= last; t++) {
            frame.positionAt(t, axis, 0.0D);
        }
        Vec3 atFirstAgain = frame.positionAt(first, axis, 0.0D);
        assertEquals(atFirst.x, atFirstAgain.x, 0.0D,
                "同一龄的解析位置 MUST 与求值历史无关");
        assertEquals(atFirst.y, atFirstAgain.y, 0.0D);
        assertEquals(atFirst.z, atFirstAgain.z, 0.0D);
        assertNotEquals(Vec3.ZERO, atFirst,
                "本用例需要一个非平凡位置，否则上面的断言没有区分力");
    }
}
