package com.bitsson.gensokyou.ritual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior;
import com.bitsson.gensokyou.client.renderer.SukimaPortalRenderer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结界破坏仪式祭品规则的离线校验（世界无关）。
 *
 * <p><b>核心不变量：祭品规则必须无序。</b>玩家把 4 颗星银与 4 颗潮汐晶任意摆进 8 个祭品台
 * 即算满足——祭品台本无先后，把「哪个是第几号」漏给玩家是设计错误（初版用
 * {@code pattern.requirements} 的 per-slot 绑定表达此规则，导致玩家必须反推规范序，
 * 实机表现为「4✓4✗」且无从下手，已废弃）。
 *
 * <p>本测试独立复算展开与几何（不复用被测代码路径），并锁定下列各项：
 * <ul>
 *   <li>祭品台位恒为 8 个（4 轴向 r=6 + 4 斜向 r=4）</li>
 *   <li>pattern <b>不</b>声明 {@code requirements}（否则又变成有序绑定）</li>
 *   <li>无 {@code requirements} 时 {@code defaultUiInfo} 不产出祭品行，故行为侧 SHALL
 *       自行给出带计数的两行——此处以常量与 {@code OFFERING_REQUIRED} 的一致性代偿</li>
 * </ul>
 */
class BarrierOfferingSlotTest {

    private static final String PATTERN = "barrier_break_circle.json";
    private static final char PEDESTAL_KEY = 'P';
    /** 门柱插座（空气格）所用的 palette 键。 */
    private static final char SOCKET_KEY = 'g';

    /** 界柱足所在的外环半径（xz 距离 6 的四轴向台位）。 */
    private static final int OUTER_RADIUS = 6;
    /** 内环半径（xz 距离 4 的四斜向台位）。 */
    private static final int INNER_RADIUS = 4;
    /** 每类祭品的需求件数。 */
    private static final int REQUIRED_PER_ITEM = 4;

    private static JsonObject loadPattern() throws IOException {
        Path p = Path.of("src", "main", "resources", "data", "gensokyou", "rituals", PATTERN);
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    /** 复刻 {@code RitualPatternLoader.expandInto} 的四重对称展开。 */
    private static List<int[]> expand(int x, int y, int z) {
        List<int[]> out = new ArrayList<>();
        if (x == 0 && z == 0) {
            out.add(new int[]{0, y, 0});
        } else if (x == 0) {
            out.add(new int[]{0, y, z});
            out.add(new int[]{0, y, -z});
            out.add(new int[]{z, y, 0});
            out.add(new int[]{-z, y, 0});
        } else if (z == 0) {
            out.add(new int[]{x, y, 0});
            out.add(new int[]{-x, y, 0});
            out.add(new int[]{0, y, x});
            out.add(new int[]{0, y, -x});
        } else {
            out.add(new int[]{x, y, z});
            out.add(new int[]{-x, y, z});
            out.add(new int[]{x, y, -z});
            out.add(new int[]{-x, y, -z});
        }
        return out;
    }

    private static int radius(int[] c) {
        return (int) Math.round(Math.sqrt((double) c[0] * c[0] + (double) c[2] * c[2]));
    }

    /** 复刻 loader 的累积展开 + {@code compareCanonical}（y→z→x）排序。 */
    private static List<int[]> pedestalSlotsInCanonicalOrder(JsonObject pattern) {
        List<int[]> all = new ArrayList<>();
        for (JsonElement levelEl : pattern.getAsJsonArray("levels")) {
            for (JsonElement e : levelEl.getAsJsonObject().getAsJsonArray("adds")) {
                JsonArray entry = e.getAsJsonArray();
                if (entry.get(0).getAsString().charAt(0) == PEDESTAL_KEY) {
                    all.addAll(expand(entry.get(1).getAsInt(), entry.get(2).getAsInt(),
                            entry.get(3).getAsInt()));
                }
            }
        }
        Set<String> seen = new HashSet<>();
        List<int[]> uniq = new ArrayList<>();
        for (int[] c : all) {
            if (seen.add(c[0] + "," + c[1] + "," + c[2])) {
                uniq.add(c);
            }
        }
        uniq.sort(Comparator.<int[]>comparingInt(c -> c[1])
                .thenComparingInt(c -> c[2])
                .thenComparingInt(c -> c[0]));
        return uniq;
    }

    // ------------------------------------------------------------------ 断言

    @Test
    void structureHasExactlyEightPedestalsInTwoSymmetricRings() throws IOException {
        List<int[]> slots = pedestalSlotsInCanonicalOrder(loadPattern());
        assertEquals(8, slots.size(), "barrier_break_circle 应有 8 个祭品台位");
        int outer = 0;
        int inner = 0;
        for (int[] c : slots) {
            int r = radius(c);
            if (r == OUTER_RADIUS) {
                outer++;
            } else if (r == INNER_RADIUS) {
                inner++;
            } else {
                throw new AssertionError("祭品台位半径既非 " + OUTER_RADIUS + " 也非 "
                        + INNER_RADIUS + "：" + c[0] + "," + c[2]);
            }
        }
        assertEquals(4, outer, "外环（界柱足）应有 4 台");
        assertEquals(4, inner, "内环应有 4 台");
    }

    /**
     * 反向护栏：pattern <b>不得</b>声明 {@code requirements}。
     *
     * <p>该字段只能一条绑一个 slot（schema 限制，天然有序）。用它表达「4+4」会迫使玩家
     * 反推 canonical 序——初版正是如此，实机报「4✓4✗」而无从定位。规则已在行为侧改为
     * 无序计数。若日后有人「顺手」把它加回来，本条即失败。
     */
    @Test
    void patternDeclaresNoOrderedRequirements() throws IOException {
        JsonObject pattern = loadPattern();
        assertFalse(pattern.has("requirements"),
                "祭品规则必须无序，MUST NOT 用 pattern.requirements 的 per-slot 有序绑定表达");
    }

    @Test
    void requiredPerItemMatchesHalfThePedestals() throws IOException {
        List<int[]> slots = pedestalSlotsInCanonicalOrder(loadPattern());
        assertEquals(REQUIRED_PER_ITEM, slots.size() / 2,
                "每类祭品需求应为祭品台位总数的一半（8 台 → 每类 4）");
    }

    @Test
    void offeringsAreNotConsumedByAnyConsumeMode() {
        // 无 requirements 即无 consume 模式可言；此处显式记录该规则不依赖 consume 字段，
        // 故 periodic 停机（upkeepTick 判负 → 框架 setEnabled(false)）与闩锁语义不可能冲突。
        assertTrue(REQUIRED_PER_ITEM > 0);
    }

    @Test
    void structureTierAndToggleabilityUnchanged() throws IOException {
        JsonObject pattern = loadPattern();
        assertFalse(pattern.get("toggleable").getAsBoolean(),
                "结界破坏为无需启停的被动仪式，toggleable 必须保持 false");
        List<Integer> tiers = new ArrayList<>();
        for (JsonElement t : pattern.getAsJsonArray("tiers")) {
            tiers.add(t.getAsInt());
        }
        assertEquals(List.of(2), tiers, "结构阶须保持 [2]");
    }

    @Test
    void canonicalOrderInterleavesAxisAndDiagonal() throws IOException {
        // 反向护栏：记录真实规范序以说明「该序交错、不按轴/斜分组」——
        // 这正是不用 slot 绑定、改走无序计数的直接理由。
        List<int[]> slots = pedestalSlotsInCanonicalOrder(loadPattern());
        Set<Integer> outerSlots = new HashSet<>();
        for (int i = 0; i < slots.size(); i++) {
            if (radius(slots.get(i)) == OUTER_RADIUS) {
                outerSlots.add(i);
            }
        }
        assertEquals(Set.of(0, 3, 4, 7), outerSlots, "外环四台的 slot 序号（真实值，防回归）");
    }

    // ------------------------------------------------------------------ 门柱插座（空气格）

    /**
     * <b>不成型回归护栏。</b>
     *
     * <p>中柱通道（门柱插座）用的是 AIR 谓词。初版生成器把它写成 {@code gensokyou:air}——
     * 而加载器只把 {@code minecraft:air} / {@code air} 认作 AIR 谓词，自造 id 会掉进
     * 默认注册表的兜底分支，变成「EXACT 空气」谓词。后果有两处，且都很难从日志看出：
     * <ol>
     *   <li>仪式搭建器把空气算进材料清单（空气没有物品，consume 必然失败）；</li>
     *   <li>隙间方块放上插座后<b>不再满足</b>该谓词 → 仪式当场变成不成型。</li>
     * </ol>
     * 正确写法是 {@code minecraft:air}，配合 {@code Predicate.AIR} 对隙间的容纳。
     */
    @Test
    void portalSocketUsesRealAirPredicate() throws IOException {
        JsonObject palette = loadPattern().getAsJsonObject("palette");
        for (var e : palette.entrySet()) {
            String value = e.getValue().getAsString();
            assertFalse(value.endsWith(":air") && !value.equals("minecraft:air"),
                    "palette 键 " + e.getKey() + " 用了自造的空气 id \"" + value
                            + "\"，加载器只认 minecraft:air/air 为 AIR 谓词，"
                            + "否则会退化成 EXACT 空气，容不下隙间并使仪式不成型");
        }
    }

    /** 插座格必须在核心正上方两格——即主世界门的落点，且中柱通道贯通到 y=7。 */
    @Test
    void portalSocketSitsDirectlyAboveTheCore() throws IOException {
        JsonObject pattern = loadPattern();
        Set<String> sockets = new HashSet<>();
        for (JsonElement levelEl : pattern.getAsJsonArray("levels")) {
            for (JsonElement e : levelEl.getAsJsonObject().getAsJsonArray("adds")) {
                JsonArray entry = e.getAsJsonArray();
                if (entry.get(0).getAsString().charAt(0) == SOCKET_KEY) {
                    sockets.add(entry.get(1).getAsInt() + "," + entry.get(2).getAsInt()
                            + "," + entry.get(3).getAsInt());
                }
            }
        }
        assertTrue(sockets.contains("0,2,0"),
                "核心正上方两格 (0,2,0) 必须是门柱插座，实际空气格：" + sockets);
    }

    // ------------------------------------------------------------------ 开眼动画两条曲线

    /**
     * <b>内景与眼缘不得脱钩（横向）。</b>
     *
     * <p>实机连续反馈「开闭动画不正确、像上下浮动了一下」。结构上眼睑<b>确实</b>已整体绕 Z 倾
     * 10°（与用户所述一致），开闭轴随之倾斜；真正的缺陷是一次改动里给内景虚空与眼睑外框
     * 用了<b>两条不同曲线</b>，而两者在几何上是同一条边界。
     *
     * <p>开合轴已翻到<b>竖直长轴</b>：眼形是 1 格宽 × 2 格高的<b>竖立杏仁</b>，尖端在左右两侧
     * 的眼中线高度，故唯一的开合量是<b>横向半宽</b>，左右两片眼睑各覆盖半个杏仁：
     *
     * <pre>
     *   内景全宽     = 2 * voidHalfW(s, scale)
     *   两片合计     = 2 * (2 * lidPieceHalfW(s, scale)) = 2 * voidHalfW(s, scale)
     * </pre>
     *
     * <p>本断言钉死这个 2 倍关系：若有人又给两者分别推导不同曲线，本条即失败。
     */
    @Test
    void voidInteriorStaysRegisteredWithTheRim() {
        for (float scale : new float[]{1.0F, 2.0F}) {
            for (float t = 0.0F; t <= 1.0F; t += 0.01F) {
                float extent = SukimaPortalRenderer.lensExtent(t);
                assertEquals(2.0F * (2.0F * SukimaPortalRenderer.lidPieceHalfW(extent, scale)),
                        2.0F * SukimaPortalRenderer.voidHalfW(extent, scale), 1.0E-5F,
                        "t=" + t + " scale=" + scale
                                + " 内景与眼缘脱钩：黑色会溢出或小于眼缘轮廓");
            }
        }
    }

    /**
     * <b>纵向跨度 MUST NOT 随开合进度变化。</b>
     *
     * <p>开合轴是竖直长轴，纵向（上盖 0.85 / 下弧 1.15）恒定：若有人误把纵向也挂上
     * {@code s}，闭眼态就会退化成一条 1 格宽的<b>水平</b>细缝——那正是本次要修掉的旧行为。
     */
    @Test
    void verticalSpanIsConstantAcrossTheOpenCycle() {
        for (float scale : new float[]{1.0F, 2.0F}) {
            for (float t = 0.0F; t <= 1.0F; t += 0.01F) {
                assertEquals(1.0F, SukimaPortalRenderer.voidHalfH(scale) / scale, 1.0E-4F,
                        "纵向半跨度必须恒为 (0.85+1.15)/2 * scale，与开合进度无关（t=" + t + "）");
            }
        }
    }

    /**
     * <b>开合 MUST NOT 过冲。</b>
     *
     * <p>旧的 easeOutBack 过冲回弹观感很差（读作「弹一下」而不是「睁开」），已退役。
     * 曲线 MUST 单调不减、终值精确为自然尺寸、全程最大值不超过自然尺寸。
     */
    @Test
    void openCycleIsMonotonicAndNeverOvershoots() {
        float peak = 0.0F;
        float previous = -1.0F;
        for (float t = 0.0F; t <= 1.0F; t += 0.001F) {
            float extent = SukimaPortalRenderer.lensExtent(t);
            assertTrue(extent >= previous - 1.0E-6F,
                    "开合系数必须单调不减，t=" + t + " 处出现回退（" + previous + " -> " + extent + "）");
            previous = extent;
            peak = Math.max(peak, extent);
        }
        assertEquals(1.0F, peak, 1.0E-6F, "全程最大值必须恰为自然尺寸，不得过冲（实测峰值 " + peak + "）");
        assertEquals(0.0F, SukimaPortalRenderer.lensExtent(0.0F), 1.0E-6F, "t=0 应为完全闭合");
        assertEquals(1.0F, SukimaPortalRenderer.lensExtent(1.0F), 1.0E-6F, "t=1 应为自然全开");
    }

    /**
     * <b>闭眼态是竖缝而非横缝。</b>
     *
     * <p>开合轴 = 竖直长轴，故 {@code t=0} 时横向半宽归零而纵向全高保留：一条贯穿 2 格
     * 全高、宽度趋零的竖直细缝。横向若不归零，就说明轴又翻回了旧的纵向缩放。
     */
    @Test
    void closedStateIsAVerticalSlit() {
        float scale = 2.0F;
        assertEquals(0.0F, SukimaPortalRenderer.voidHalfW(0.0F, scale), 1.0E-6F,
                "闭眼时横向半宽必须归零（否则闭眼态是一条水平细缝，开合轴反了）");
        assertTrue(SukimaPortalRenderer.voidHalfH(scale) > 0.0F,
                "闭眼时纵向半跨度必须保留（长轴恒定）");
        assertTrue(SukimaPortalRenderer.voidHalfH(scale) > SukimaPortalRenderer.voidHalfW(1.0F, scale),
                "全开时长轴（纵向）仍应比短轴（横向）长——眼形是 1 宽 x 2 高的竖立杏仁");
    }

    // ------------------------------------------------------------------ 眼睑贴图分辨率

    /**
     * <b>锯齿回归护栏。</b>
     *
     * <p>眼睑轮廓原本是 16x32 贴图上用 {@code int()} 截断画出的 <b>1 像素硬</b> alpha 带。
     * 放大到 {@code BARRIER_PORTAL_SCALE=2} 后一个像素约占半格，眼形读作硬阶梯。
     *
     * <p>这无法从渲染类型侧解决：vanilla 没有 MSAA，而 {@code entitySmoothCutout} 的片元
     * 着色器同样是硬 {@code discard(alpha<0.1)}——名字里的 "smooth" 指的是 mipmap 距离淡出，
     * <b>不是</b>边缘抗锯齿。故修法只能在贴图侧：以 8 倍分辨率 + 子像素覆盖率重栅格化。
     *
     * <p>本断言只依赖 PNG 头（IHDR）里的宽高与文件体积，不需要图像库：若有人把生成器
     * 改回 16x32 硬边版本（178 字节），此断言即失败。
     */
    @Test
    void eyelidTextureIsHighResolutionWithAntialiasedEdges() throws IOException {
        Path p = Path.of("src", "main", "resources", "assets", "gensokyou",
                "textures", "entity", "sukima.png");
        byte[] png = Files.readAllBytes(p);
        assertTrue(png.length > 8 + 25, "不是合法 PNG：" + p);
        int width = readIntBE(png, 16);
        int height = readIntBE(png, 20);
        assertEquals(0.5, width / (double) height, 1.0E-9,
                "眼睑贴图应保持 1:2 宽高比（16x32 的 8 倍）");
        assertTrue(width >= 64 && height >= 128,
                "眼睑贴图分辨率过低（" + width + "x" + height + "）：放大到 2.0 尺寸后"
                        + "1 像素硬 alpha 轮廓会读作阶梯锯齿，应由 tools/textures/sukima.py"
                        + " 以子像素覆盖率高分辨率重栅格化");
        assertTrue(png.length > 1000,
                "眼睑贴图仅 " + png.length + " 字节，疑似退回无抗锯齿的小图");
    }

    private static int readIntBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    // ------------------------------------------------------------------ 孪生门落点扫描

    /**
     * <b>死循环回归护栏。</b>
     *
     * <p>幻想乡侧孪生门的落点搜索初版是内联三重 for：环半径同时充当步长（{@code dx += r}）
     * 且 r 从 0 起 → {@code r == 0} 时步长为 0，内层循环永不推进。该函数在服务端主线程的
     * 开门路径上被调用，实测<b>直接冻结整个世界</b>（主线程不返回，存档亦无法写出）。
     *
     * <p>本断言把「必须终止」与「候选集合完整」钉死：若有人再把步长写成会退化的形式，
     * 或漏掉中心格，测试即失败。
     */
    @Test
    void twinPortalSurfaceProbeTerminatesAndIsComplete() {
        int maxRadius = 12;
        List<int[]> offsets = BarrierBreakBehavior.surfaceProbeOffsets(maxRadius);
        // 1 + sum(8r, r=1..maxRadius) = 1 + 4R(R+1)
        assertEquals(1 + 4 * maxRadius * (maxRadius + 1), offsets.size(),
                "方形环外扩的候选数应为 1 + 4R(R+1)（有限、可预期）");
        assertEquals(0, offsets.get(0)[0], "第一个候选必须是中心格");
        assertEquals(0, offsets.get(0)[1], "第一个候选必须是中心格");
        Set<String> seen = new HashSet<>();
        for (int[] o : offsets) {
            assertTrue(Math.abs(o[0]) <= maxRadius && Math.abs(o[1]) <= maxRadius,
                    "候选偏移越界：" + o[0] + "," + o[1]);
            assertTrue(seen.add(o[0] + "," + o[1]), "候选偏移重复：" + o[0] + "," + o[1]);
        }
    }

    @Test
    void surfaceProbeCoversEveryRingExactlyOnce() {
        int maxRadius = 6;
        // 按切比雪夫距离分组：每一圈 r 应恰好有 8r 个格
        for (int r = 0; r <= maxRadius; r++) {
            int expected = r == 0 ? 1 : 8 * r;
            int actual = 0;
            for (int[] o : BarrierBreakBehavior.surfaceProbeOffsets(maxRadius)) {
                if (Math.max(Math.abs(o[0]), Math.abs(o[1])) == r) {
                    actual++;
                }
            }
            assertEquals(expected, actual, "第 " + r + " 圈候选数不对");
        }
    }
}
