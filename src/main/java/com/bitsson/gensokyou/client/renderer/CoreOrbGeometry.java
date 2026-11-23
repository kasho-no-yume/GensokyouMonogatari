package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.item.weapon.CoreShellProfile;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 千机核心的<b>几何</b>（纯数学，无渲染依赖）。
 *
 * <p>刻意与 {@link CoreOrbRenderer} 分开：渲染器继承
 * {@code BlockEntityWithoutLevelRenderer}，加载它会连带初始化原版 {@code Blocks}
 * （单测环境会抛 "Not bootstrapped"，而 NeoForge 的引导又需要 FML 的 mod 列表）。
 * 几何本身只是向量运算，把它拆出来之后，各档案的法阵/星轨与球体形态就能被<b>离线预览与断言</b>。
 *
 * <p><b>设计理念</b>：无论是否装入弹幕核，千机核心本体始终保持优雅的<b>流光灵球</b>（{@link #DREAM_BODY}），
 * 不再变形为粗糙的低模飞刀/步枪/方板。装入不同弹幕核时：
 * <ul>
 *   <li>球体流光切换为该弹幕核对应的<b>品阶色</b>；</li>
 *   <li>球体周围与表面浮现该弹幕核专属的<b>东方幻想灵阵与星轨</b>（{@link #energy(CoreShellProfile)}）。</li>
 * </ul>
 */
public final class CoreOrbGeometry {

    // ==================== 结构色 ====================

    /**
     * 符纸的朱印色——<b>结构色</b>，不是品阶色。
     */
    public static final int SEAL_RED_COLOR = 0xE03A2F;

    /** 封印钉与符文的金色——同为结构色。 */
    public static final int GOLD_COLOR = 0xFFD24A;

    /**
     * 球心暗腔的深色（结构色）。
     */
    public static final int CAVITY_DARK_COLOR = 0x1E1730;

    // ==================== 尺寸 ====================

    /** 千机核心流光球半径。 */
    public static final float DREAM_RADIUS = 0.40F;

    /** 沉睡态火冠：火舌数量 / 根部所在纬度（度） / 高度 / 根部宽度 / 外倾。 */
    public static final int FLAME_TONGUES = 10;
    public static final float FLAME_LATITUDE = 26.0F;
    public static final float FLAME_HEIGHT = 0.26F;
    public static final float FLAME_WIDTH = 0.058F;
    public static final float FLAME_LEAN = 0.40F;

    /** 火冠绕轴自转（度/秒）。 */
    public static final float FLAME_SPIN = 22.0F;

    // ==================== 母线 ====================

    /** 球体母线（半径, z）：从 +Z 极到 -Z 极，6 段。 */
    private static final float[][] PROFILE_ROUND = {
            {0.00F, 1.00F}, {0.55F, 0.84F}, {0.90F, 0.44F}, {1.00F, 0.00F},
            {0.90F, -0.44F}, {0.55F, -0.84F}, {0.00F, -1.00F}};

    /** 腔体母线（半径, z）：从中心（z=0）向外张开的浅碟，正对相机。 */
    private static final float[][] PROFILE_CAVITY = {
            {0.00F, 0.00F}, {0.62F, 0.10F}, {1.00F, 0.26F}};

    // ==================== 数据结构 ====================

    /**
     * 一个顶点。
     *
     * @param p 位置
     * @param u 贴图 U
     * @param v 贴图 V
     * @param s 流动坐标：沿表面"能量行进方向"的参数（0..1）。色相与亮度带都由它驱动。
     * @param e 边缘度：0 = 中心，1 = 轮廓。沉睡态的色散靠它定位。
     */
    public record Vtx(Vec3 p, float u, float v, float s, float e) {
    }

    /**
     * 一个四边形。
     *
     * @param face 面序号（逐面虹彩微偏用）；火冠件复用它存"火舌序号"。
     * @param fixed 固定色（非 0 时使用该 RGB，忽略层色；用于符纸的朱印/封印钉等结构色）。
     */
    public record Quad(Vtx a, Vtx b, Vtx c, Vtx d, int face, int fixed) {
    }

    // ==================== 缓存 ====================

    /** 主体轮廓面片缓存：档案 → 面片表。 */
    private static final Map<CoreShellProfile, List<Quad>> BODY_CACHE = new EnumMap<>(CoreShellProfile.class);
    /** 武器自带灵阵/星轨缓存。 */
    private static final Map<CoreShellProfile, List<Quad>> ENERGY_CACHE = new EnumMap<>(CoreShellProfile.class);
    private static final Map<CoreShellProfile, List<Quad>> SEAL_CACHE = new EnumMap<>(CoreShellProfile.class);
    /** 主体轮廓的包围盒中心（球体恒为原点）。 */
    private static final Map<CoreShellProfile, Vec3> CENTER_CACHE = new EnumMap<>(CoreShellProfile.class);

    /** 千机核心流光球本体。 */
    public static final List<Quad> DREAM_BODY = buildDreamBody();
    /** 球心暗腔与流光涡旋。 */
    public static final List<Quad> DREAM_CAVITY = buildDreamCavity();

    // ==================== 对外入口 ====================

    /** 主体轮廓：所有形态均保持千机核心球体本尊。 */
    public static List<Quad> body(CoreShellProfile profile) {
        return BODY_CACHE.computeIfAbsent(profile, CoreOrbGeometry::buildBody);
    }

    /** 武器自带的法阵环与星轨表现（按弹幕核档案区分）。 */
    public static List<Quad> energy(CoreShellProfile profile) {
        return ENERGY_CACHE.computeIfAbsent(profile, CoreOrbGeometry::buildEnergy);
    }

    /** 固定在球面的弹核印记。环绕阵纹经过时，印记仍保持朝向。 */
    public static List<Quad> seal(CoreShellProfile profile) {
        return SEAL_CACHE.computeIfAbsent(profile, CoreOrbGeometry::buildSeal);
    }

    /** 轮廓包围盒中心（外晕绕它放大）。 */
    public static Vec3 center(CoreShellProfile profile) {
        return CENTER_CACHE.computeIfAbsent(profile, p -> Vec3.ZERO);
    }

    /** 环绕环：XY 平面上的一圈细带（圈数 = 武器等级）。 */
    public static List<Quad> ring(int index) {
        int segments = 24;
        float radius = 0.44F + 0.052F * index;
        float half = 0.010F;
        List<Quad> list = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double a0 = 2.0 * Math.PI * i / segments;
            double a1 = 2.0 * Math.PI * (i + 1) / segments;
            float x0 = (float) Math.cos(a0) * radius;
            float y0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float y1 = (float) Math.sin(a1) * radius;
            list.add(new Quad(
                    new Vtx(new Vec3(x0, y0, -half), (float) i / segments, 0.0F, (float) i / segments, 1.0F),
                    new Vtx(new Vec3(x1, y1, -half), (float) (i + 1) / segments, 0.0F, (float) (i + 1) / segments, 1.0F),
                    new Vtx(new Vec3(x1, y1, half), (float) (i + 1) / segments, 0.0F, (float) (i + 1) / segments, 1.0F),
                    new Vtx(new Vec3(x0, y0, half), (float) i / segments, 0.0F, (float) i / segments, 1.0F), i, 0));
        }
        return list;
    }

    /**
     * 逐帧生成火冠几何（相位与摇曳都在动，故不进缓存）。
     */
    public static List<Quad> flame(float time) {
        List<Quad> list = new ArrayList<>(FLAME_TONGUES * 2 * 3);
        double lat = Math.toRadians(FLAME_LATITUDE);
        float rootY = (float) Math.sin(lat) * DREAM_RADIUS;
        float rootR = (float) Math.cos(lat) * DREAM_RADIUS;
        for (int i = 0; i < FLAME_TONGUES; i++) {
            double az = 2.0 * Math.PI * i / FLAME_TONGUES + time * Math.toRadians(FLAME_SPIN);
            double cosLat = Math.cos(lat);
            Vec3 radial = new Vec3(Math.cos(az) * cosLat, Math.sin(lat), Math.sin(az) * cosLat);
            Vec3 root = new Vec3(radial.x * rootR, rootY, radial.z * rootR);
            Vec3 axis = new Vec3(radial.x * FLAME_LEAN, 1.0F, radial.z * FLAME_LEAN).normalize();
            Vec3 tangent = new Vec3(-Math.sin(az), 0.0F, Math.cos(az));
            Vec3 bitangent = axis.cross(tangent).normalize();
            int segs = 3;
            for (int k = 0; k < segs; k++) {
                float t0 = (float) k / segs;
                float t1 = (float) (k + 1) / segs;
                Vec3 p0 = root.add(axis.scale(FLAME_HEIGHT * t0)).add(radial.scale(0.09F * t0 * t0));
                Vec3 p1 = root.add(axis.scale(FLAME_HEIGHT * t1)).add(radial.scale(0.09F * t1 * t1));
                float w0 = FLAME_WIDTH * (float) Math.pow(1.0F - t0, 0.6F);
                float w1 = FLAME_WIDTH * (float) Math.pow(1.0F - t1, 0.6F);
                list.addAll(flameSegment(p0, p1, w0, w1, tangent, i, t0, t1));
                list.addAll(flameSegment(p0, p1, w0, w1, bitangent, i, t0, t1));
            }
        }
        return list;
    }

    // ==================== 主体轮廓与灵阵编排 ====================

    /** 主体轮廓：所有档案统一使用千机核心流光球。 */
    private static List<Quad> buildBody(CoreShellProfile profile) {
        return DREAM_BODY;
    }

    /** 各弹幕核专属的环球灵阵与星轨。 */
    private static List<Quad> buildEnergy(CoreShellProfile profile) {
        return switch (profile) {
            case DREAM -> List.of();
            case BALL -> buildBallAura();
            case SCATTER -> buildScatterAura();
            case DART -> buildDartAura();
            case BLADES -> buildBladesAura();
            case PAPER -> buildPaperAura();
            case GUN -> buildGunAura();
        };
    }

    private static List<Quad> buildSeal(CoreShellProfile profile) {
        String[] rows = switch (profile) {
            case DREAM -> new String[0];
            case BALL -> new String[]{"...++...", "..+##+..", ".+####+.", ".+#.+#+.",
                                      ".+####+.", ".+####+.", "..+##+..", "...++..."};
            case SCATTER -> new String[]{"........", "##....##", "##....##", "........",
                                         ".##..##.", ".++..++.", "...##...", "...++..."};
            case DART -> new String[]{"........", "...+#...", "..+.##..", ".+#####.",
                                      ".+#####.", "..+.##..", "...+#...", "........"};
            case BLADES -> new String[]{"......##", ".....##+", "....##+.", "...##+..",
                                        ".+.#+...", "..#+....", ".#+.+...", "++......"};
            case PAPER -> new String[]{"..####..", "..#++#..", "..#..#..", "..#++#..",
                                       "..#++#..", "..#..#..", "..#++#..", "..####.."};
            case GUN -> new String[]{"...++...", "..+##+..", "..#++#..", "++#++#++",
                                     "++#++#++", "..#++#..", "..+##+..", "...++..."};
        };
        List<Quad> quads = new ArrayList<>();
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                char pixel = rows[row].charAt(col);
                if (pixel == '.') {
                    continue;
                }
                float z = (col - 4) * 0.045F;
                float y = (4 - row) * 0.045F;
                int color = pixel == '#' ? 0xFFF0CD : 0xDBAB68;
                for (int side : new int[]{-1, 1}) {
                    // 镜像背面，使斜向飞刀印从两侧看都是同一方向。
                    Vtx a = sealVertex(side, y, -side * z);
                    Vtx b = sealVertex(side, y, -side * (z + 0.045F));
                    Vtx c = sealVertex(side, y - 0.045F, -side * (z + 0.045F));
                    Vtx d = sealVertex(side, y - 0.045F, -side * z);
                    quads.add(new Quad(a, d, c, b, 0, color));
                }
            }
        }
        return List.copyOf(quads);
    }

    private static Vtx sealVertex(int side, float y, float z) {
        double radius = DREAM_RADIUS + 0.018F;
        double x = side * Math.sqrt(radius * radius - y * y - z * z);
        return new Vtx(new Vec3(x, y, z), 0, 0, 0.5F, 0);
    }

    // ==================== 各档案的环球灵阵具体形态 ====================

    /** 流光球本体。 */
    private static List<Quad> buildDreamBody() {
        return revolve(PROFILE_ROUND, 24, DREAM_RADIUS, DREAM_RADIUS, DREAM_RADIUS);
    }

    /** 球心暗腔（正对相机的深色浅碟 + 两圈反向旋转的涡旋弧）。 */
    private static List<Quad> buildDreamCavity() {
        List<Quad> list = new ArrayList<>();
        list.addAll(revolve(PROFILE_CAVITY, 12, 0.17F, 0.17F, 0.26F, CAVITY_DARK_COLOR));
        list.addAll(arc(0.105F, 0.013F, 10, 220.0F, 0.10F));
        list.addAll(arc(0.145F, 0.011F, 10, 220.0F, 0.16F));
        return list;
    }

    /** 单发玉（BALL）：双道浑天交叉灵脉纬线 + 赤道聚灵环。 */
    private static List<Quad> buildBallAura() {
        List<Quad> list = new ArrayList<>();
        list.addAll(latticeBand(0.415F, 0.015F, 24, 32.0F));
        list.addAll(latticeBand(0.415F, 0.015F, 24, -32.0F));
        list.addAll(latticeBand(0.422F, 0.011F, 24, 90.0F));
        return list;
    }

    /** 散弹玉（SCATTER）：赤道星轨细环 + 五颗微缩伴生星珠环绕球体。 */
    private static List<Quad> buildScatterAura() {
        List<Quad> list = new ArrayList<>();
        list.addAll(latticeBand(0.430F, 0.009F, 24, 15.0F));
        float orbitR = 0.455F;
        for (int i = 0; i < 5; i++) {
            double ang = 2.0 * Math.PI * i / 5.0;
            float y = (float) Math.cos(ang) * orbitR;
            float z = (float) Math.sin(ang) * orbitR;
            list.addAll(orbAt(z, y, 0.0F, 0.044F));
        }
        return list;
    }

    /** 麟弹（DART）：三棱星芒法阵环 + 内圈护灵环（半径加大使三棱尖角突出于圆形外晕之外）。 */
    private static List<Quad> buildDartAura() {
        List<Quad> list = new ArrayList<>();
        list.addAll(polygonBand(0.515F, 0.016F, 3, 0.0F, 12.0F, 0));
        list.addAll(polygonBand(0.475F, 0.012F, 3, 60.0F, -12.0F, 0));
        list.addAll(latticeBand(0.412F, 0.010F, 18, 0.0F));
        return list;
    }

    /** 飞刀（BLADES）：六芒星交错剑阵环（双正三角反转交叠，六个剑尖鲜明突出于球体外缘）。 */
    private static List<Quad> buildBladesAura() {
        List<Quad> list = new ArrayList<>();
        list.addAll(polygonBand(0.525F, 0.015F, 3, 0.0F, 0.0F, 0));
        list.addAll(polygonBand(0.525F, 0.015F, 3, 180.0F, 0.0F, 0));
        list.addAll(latticeBand(0.425F, 0.009F, 24, 45.0F));
        list.addAll(latticeBand(0.425F, 0.009F, 24, -45.0F));
        return list;
    }

    /** 灵符（PAPER）：外围八角金边结界 + 内嵌菱形朱印法阵（金红交错，东方符箓感鲜明）。 */
    private static List<Quad> buildPaperAura() {
        List<Quad> list = new ArrayList<>();
        list.addAll(polygonBand(0.460F, 0.015F, 8, 22.5F, 0.0F, GOLD_COLOR));
        list.addAll(polygonBand(0.485F, 0.014F, 4, 45.0F, 0.0F, SEAL_RED_COLOR));
        list.addAll(polygonBand(0.435F, 0.011F, 4, 0.0F, 25.0F, GOLD_COLOR));
        return list;
    }

    /** 激光（GUN）：六角聚焦棱镜外环 + 双斜向偏振聚束环（魔法棱镜星盘）。 */
    private static List<Quad> buildGunAura() {
        List<Quad> list = new ArrayList<>();
        list.addAll(polygonBand(0.490F, 0.016F, 6, 0.0F, 0.0F, 0));
        list.addAll(polygonBand(0.445F, 0.012F, 6, 30.0F, 52.0F, 0));
        list.addAll(polygonBand(0.445F, 0.012F, 6, 30.0F, -52.0F, 0));
        list.addAll(latticeBand(0.410F, 0.011F, 24, 90.0F));
        return list;
    }

    // ==================== 几何基元 ====================

    /** 绕 Z 轴的旋转体。母线是 (半径, z) 序列。 */
    private static List<Quad> revolve(float[][] profile, int sectors,
                                      float xScale, float yScale, float zScale) {
        return revolve(profile, sectors, xScale, yScale, zScale, 0);
    }

    private static List<Quad> revolve(float[][] profile, int sectors,
                                      float xScale, float yScale, float zScale, int fixed) {
        List<Quad> list = new ArrayList<>(sectors * (profile.length - 1));
        int rings = profile.length;
        float maxRadius = 0.0F;
        for (float[] p : profile) {
            maxRadius = Math.max(maxRadius, p[0]);
        }
        if (maxRadius <= 0.0F) {
            maxRadius = 1.0F;
        }
        for (int i = 0; i < rings - 1; i++) {
            float r0 = profile[i][0];
            float z0 = profile[i][1];
            float r1 = profile[i + 1][0];
            float z1 = profile[i + 1][1];
            float v0 = (float) i / (rings - 1);
            float v1 = (float) (i + 1) / (rings - 1);
            for (int s = 0; s < sectors; s++) {
                int s2 = (s + 1) % sectors;
                double th0 = 2.0 * Math.PI * s / sectors;
                double th1 = 2.0 * Math.PI * s2 / sectors;
                float az0 = (float) (th0 / (2.0 * Math.PI));
                float az1 = (float) (th1 / (2.0 * Math.PI));
                Vtx a = new Vtx(new Vec3(Math.cos(th0) * r0 * xScale, Math.sin(th0) * r0 * yScale, z0 * zScale),
                        (float) s / sectors, v0, flow(0.76F * v0 + 0.24F * az0), edge(r0, maxRadius));
                Vtx b = new Vtx(new Vec3(Math.cos(th1) * r0 * xScale, Math.sin(th1) * r0 * yScale, z0 * zScale),
                        (float) s2 / sectors, v0, flow(0.76F * v0 + 0.24F * az1), edge(r0, maxRadius));
                Vtx c = new Vtx(new Vec3(Math.cos(th1) * r1 * xScale, Math.sin(th1) * r1 * yScale, z1 * zScale),
                        (float) s2 / sectors, v1, flow(0.76F * v1 + 0.24F * az1), edge(r1, maxRadius));
                Vtx d = new Vtx(new Vec3(Math.cos(th0) * r1 * xScale, Math.sin(th0) * r1 * yScale, z1 * zScale),
                        (float) s / sectors, v1, flow(0.76F * v1 + 0.24F * az0), edge(r1, maxRadius));
                list.add(new Quad(a, b, c, d, s - (sectors - 1) / 2, fixed));
            }
        }
        return list;
    }

    /** 小球（伴生星珠）。 */
    private static List<Quad> orbAt(float z, float y, float x, float radius) {
        List<Quad> quads = revolve(PROFILE_ROUND, 8, radius, radius, radius);
        List<Quad> out = new ArrayList<>(quads.size());
        for (Quad q : quads) {
            out.add(new Quad(shiftVtx(q.a(), x, y, z), shiftVtx(q.b(), x, y, z),
                    shiftVtx(q.c(), x, y, z), shiftVtx(q.d(), x, y, z), q.face(), q.fixed()));
        }
        return out;
    }

    /**
     * 环绕球面的一道连续圆环带（能量纬线）。
     *
     * <p><b>绕模型的 X 轴成环</b>（YZ 平面），侧视下正对屏幕成环。
     */
    private static List<Quad> latticeBand(float radius, float halfWidth, int segments, float tiltDeg) {
        return polygonBand(radius, halfWidth, segments, 0.0F, tiltDeg, 0);
    }

    /**
     * 环绕球面的正多边形法阵带（3=三角阵、4=方阵、6=六角星盘、8=八卦框、24=圆环）。
     *
     * @param sides    多边形边数
     * @param phaseDeg 平面内起始旋转角（度）
     * @param tiltDeg  环平面绕模型 Y 轴的倾角（度），使法阵在立体空间交错
     * @param fixed    固定结构色（0 = 使用 ENERGY 层色）
     */
    private static List<Quad> polygonBand(float radius, float halfWidth, int sides,
                                          float phaseDeg, float tiltDeg, int fixed) {
        List<Quad> list = new ArrayList<>(sides * 2);
        float t = (float) Math.toRadians(tiltDeg);
        float cos = (float) Math.cos(t);
        float sin = (float) Math.sin(t);
        double phase = Math.toRadians(phaseDeg);
        float rIn = Math.max(0.01F, radius - halfWidth);
        float rOut = radius + halfWidth;
        for (int i = 0; i < sides; i++) {
            double a0 = phase + 2.0 * Math.PI * i / sides;
            double a1 = phase + 2.0 * Math.PI * (i + 1) / sides;
            float cy0 = (float) Math.cos(a0);
            float cz0 = (float) Math.sin(a0);
            float cy1 = (float) Math.cos(a1);
            float cz1 = (float) Math.sin(a1);

            // 1. 轴向带（X 方向厚度，侧面可见）
            Vec3 p0 = tiltX(halfWidth, cy0 * radius, cz0 * radius, cos, sin);
            Vec3 p1 = tiltX(halfWidth, cy1 * radius, cz1 * radius, cos, sin);
            Vec3 p2 = tiltX(-halfWidth, cy1 * radius, cz1 * radius, cos, sin);
            Vec3 p3 = tiltX(-halfWidth, cy0 * radius, cz0 * radius, cos, sin);
            float s0 = (float) i / sides;
            float s1 = (float) (i + 1) / sides;
            list.add(new Quad(
                    new Vtx(p0, s0, 0.0F, s0, 1.0F),
                    new Vtx(p1, s1, 0.0F, s1, 1.0F),
                    new Vtx(p2, s1, 1.0F, s1, 1.0F),
                    new Vtx(p3, s0, 1.0F, s0, 1.0F), 0, fixed));

            // 2. 径向带（在法阵平面内张开宽度，正视屏幕清晰可见多边形轮廓）
            Vec3 r0In = tiltX(0.0F, cy0 * rIn, cz0 * rIn, cos, sin);
            Vec3 r1In = tiltX(0.0F, cy1 * rIn, cz1 * rIn, cos, sin);
            Vec3 r1Out = tiltX(0.0F, cy1 * rOut, cz1 * rOut, cos, sin);
            Vec3 r0Out = tiltX(0.0F, cy0 * rOut, cz0 * rOut, cos, sin);
            list.add(new Quad(
                    new Vtx(r0In, s0, 0.0F, s0, 1.0F),
                    new Vtx(r1In, s1, 0.0F, s1, 1.0F),
                    new Vtx(r1Out, s1, 1.0F, s1, 1.0F),
                    new Vtx(r0Out, s0, 1.0F, s0, 1.0F), 0, fixed));
        }
        return list;
    }

    private static Vec3 tiltX(float x, float y, float z, float cos, float sin) {
        float rx = x * cos - z * sin;
        float rz = x * sin + z * cos;
        return new Vec3(rx, y, rz);
    }

    /** 圆弧带（腔体的涡旋弧）：在 z 平面内张开一条窄带。 */
    private static List<Quad> arc(float radius, float halfWidth, int segments, float sweepDeg, float z) {
        List<Quad> list = new ArrayList<>(segments);
        double start = Math.toRadians(-sweepDeg / 2.0F);
        for (int i = 0; i < segments; i++) {
            double a0 = start + Math.toRadians(sweepDeg) * i / segments;
            double a1 = start + Math.toRadians(sweepDeg) * (i + 1) / segments;
            float x0 = (float) Math.cos(a0) * radius;
            float y0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float y1 = (float) Math.sin(a1) * radius;
            list.add(new Quad(
                    new Vtx(new Vec3(x0, y0, z - halfWidth), (float) i / segments, 0.0F, (float) i / segments, 0.8F),
                    new Vtx(new Vec3(x1, y1, z - halfWidth), (float) (i + 1) / segments, 0.0F, (float) (i + 1) / segments, 0.8F),
                    new Vtx(new Vec3(x1, y1, z + halfWidth), (float) (i + 1) / segments, 0.0F, (float) (i + 1) / segments, 0.8F),
                    new Vtx(new Vec3(x0, y0, z + halfWidth), (float) i / segments, 0.0F, (float) i / segments, 0.8F), i, 0));
        }
        return list;
    }

    /** 火舌的一段（沿给定横向轴）。 */
    private static List<Quad> flameSegment(Vec3 p0, Vec3 p1, float w0, float w1,
                                            Vec3 across, int tongue, float t0, float t1) {
        Vec3 o0 = across.scale(w0);
        Vec3 o1 = across.scale(w1);
        return List.of(new Quad(
                new Vtx(p0.subtract(o0), 0.0F, t0, t0, 1.0F),
                new Vtx(p0.add(o0), 1.0F, t0, t0, 1.0F),
                new Vtx(p1.add(o1), 1.0F, t1, t1, 1.0F),
                new Vtx(p1.subtract(o1), 0.0F, t1, t1, 1.0F), tongue, 0));
    }

    // ==================== 小工具 ====================

    /** 流动坐标归一化到 0..1。 */
    private static float flow(float s) {
        return Math.min(1.0F, Math.max(0.0F, s));
    }

    /** 边缘度：半径 / 最大半径。 */
    private static float edge(float radius, float maxRadius) {
        return Math.min(1.0F, Math.max(0.0F, radius / maxRadius));
    }

    /** 顶点平移。 */
    private static Vtx shiftVtx(Vtx v, float x, float y, float z) {
        return new Vtx(v.p().add(x, y, z), v.u(), v.v(), v.s(), v.e());
    }

    private CoreOrbGeometry() {
    }
}
