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
 * 几何本身只是向量运算，把它拆出来之后，轮廓形状就能被<b>离线预览与断言</b>——
 * "七个档案的剪影真的各不相同"是机器可验证的，不必只靠实机眼睛。
 *
 * <p>所有静态几何按档案 id 缓存；逐帧变形的只有火冠（相位与摇曳都在动）。
 */
public final class CoreOrbGeometry {

    // ==================== 结构色 ====================

    /**
     * 符纸的朱印色——<b>结构色</b>，不是品阶色。
     *
     * <p>与 BOSS 血条「朱红符」边框（{@code TALISMAN_BAR_COLOR_FRAME}）同源：符纸的身份识别色
     * 固定，不随品阶改变，故 MUST NOT 参与染色。
     */
    public static final int SEAL_RED_COLOR = 0xE03A2F;

    /** 封印钉与符文的金色——同为结构色。 */
    public static final int GOLD_COLOR = 0xFFD24A;

    /**
     * 沉睡态球心暗腔的深色（结构色）。
     *
     * <p>暗腔是"未装核"的可读性载体，同时是气焰的根部——故必须是<b>暗</b>的，
     * 否则球心会糊成一团、读不出"空"。
     */
    public static final int CAVITY_DARK_COLOR = 0x1E1730;

    // ==================== 尺寸 ====================

    /** 沉睡态流光球半径。 */
    public static final float DREAM_RADIUS = 0.40F;

    /** 沉睡态火冠：火舌数量 / 根部所在纬度（度） / 高度 / 根部宽度 / 外倾。 */
    public static final int FLAME_TONGUES = 10;
    public static final float FLAME_LATITUDE = 26.0F;
    public static final float FLAME_HEIGHT = 0.26F;
    public static final float FLAME_WIDTH = 0.058F;
    public static final float FLAME_LEAN = 0.40F;

    /** 火冠绕轴自转（度/秒）。 */
    public static final float FLAME_SPIN = 22.0F;

    /** 枪身半厚（X 方向）。 */
    public static final float GUN_HALF_THICKNESS = 0.055F;

    /** 符纸半厚（X 方向）。 */
    public static final float PAPER_HALF_THICKNESS = 0.012F;

    // ==================== 母线 ====================

    /** 球体母线（半径, z）：从 +Z 极到 -Z 极，6 段。 */
    private static final float[][] PROFILE_ROUND = {
            {0.00F, 1.00F}, {0.55F, 0.84F}, {0.90F, 0.44F}, {1.00F, 0.00F},
            {0.90F, -0.44F}, {0.55F, -0.84F}, {0.00F, -1.00F}};

    /** 锥形母线（半径, z）：前尖后钝。 */
    private static final float[][] PROFILE_DART = {
            {0.00F, 1.00F}, {0.26F, 0.80F}, {0.55F, 0.50F}, {0.82F, 0.18F},
            {1.00F, -0.10F}, {0.86F, -0.48F}, {0.00F, -0.86F}};

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

    /** 侧视轮廓上的一个点：z = 长度方向，y = 高度方向。 */
    public record P2(float z, float y) {
    }

    // ==================== 缓存 ====================

    /** 主体轮廓面片缓存：档案 → 面片表。静态几何每帧重建会白烧 CPU。 */
    private static final Map<CoreShellProfile, List<Quad>> BODY_CACHE = new EnumMap<>(CoreShellProfile.class);
    /** 武器自带能量件缓存。 */
    private static final Map<CoreShellProfile, List<Quad>> ENERGY_CACHE = new EnumMap<>(CoreShellProfile.class);
    /** 主体轮廓的包围盒中心（外晕绕它放大，避免长形武器的辉光偏心）。 */
    private static final Map<CoreShellProfile, Vec3> CENTER_CACHE = new EnumMap<>(CoreShellProfile.class);

    /** 沉睡态流光球。 */
    public static final List<Quad> DREAM_BODY = buildDreamBody();
    /** 沉睡态球心暗腔。 */
    public static final List<Quad> DREAM_CAVITY = buildDreamCavity();

    // ==================== 对外入口 ====================

    /** 主体轮廓。 */
    public static List<Quad> body(CoreShellProfile profile) {
        return BODY_CACHE.computeIfAbsent(profile, CoreOrbGeometry::buildBody);
    }

    /** 武器自带的能量表现。 */
    public static List<Quad> energy(CoreShellProfile profile) {
        return ENERGY_CACHE.computeIfAbsent(profile, CoreOrbGeometry::buildEnergy);
    }

    /** 轮廓包围盒中心（外晕绕它放大）。 */
    public static Vec3 center(CoreShellProfile profile) {
        return CENTER_CACHE.computeIfAbsent(profile, CoreOrbGeometry::computeCenter);
    }

    /** 环绕环：XY 平面上的一圈细带（圈数 = 武器等级）。 */
    public static List<Quad> ring(int index) {
        int segments = 24;
        float radius = 0.42F + 0.055F * index;
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
     *
     * <p>每条火舌由两片交叉的条带组成（十字交叉），保证从任何角度看都不会消失。
     * {@link Quad#face()} 复用为"火舌序号"，供着色时取该舌的根部流光色。
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
            // 舌轴：向上并略向外倾
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

    // ==================== 主体轮廓 ====================

    /** 主体轮廓。 */
    private static List<Quad> buildBody(CoreShellProfile profile) {
        return switch (profile) {
            case DREAM -> DREAM_BODY;
            case DART -> buildDart();
            case BALL -> buildBall();
            case SCATTER -> buildScatter();
            case BLADES -> buildBlades();
            case PAPER -> buildPaper();
            case GUN -> buildGun();
        };
    }

    /** 武器自带的能量表现。 */
    private static List<Quad> buildEnergy(CoreShellProfile profile) {
        return switch (profile) {
            case DREAM -> List.of();
            case DART -> buildDartTrails();
            case BALL -> buildBallLatitudes();
            case SCATTER -> buildScatterTrails();
            case BLADES -> buildBladeTrails();
            case PAPER -> buildTalismanScript();
            case GUN -> buildGunAccents();
        };
    }

    /** 轮廓包围盒中心（外晕绕它放大）。 */
    private static Vec3 computeCenter(CoreShellProfile profile) {
        List<Quad> quads = body(profile);
        if (quads.isEmpty()) {
            return Vec3.ZERO;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Quad q : quads) {
            for (Vtx v : new Vtx[]{q.a(), q.b(), q.c(), q.d()}) {
                minX = Math.min(minX, v.p().x);
                maxX = Math.max(maxX, v.p().x);
                minY = Math.min(minY, v.p().y);
                maxY = Math.max(maxY, v.p().y);
                minZ = Math.min(minZ, v.p().z);
                maxZ = Math.max(maxZ, v.p().z);
            }
        }
        return new Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
    }

    // ==================== 各档案的具体形态 ====================

    /** 沉睡态：全彩流光球。 */
    private static List<Quad> buildDreamBody() {
        return revolve(PROFILE_ROUND, 24, DREAM_RADIUS, DREAM_RADIUS, DREAM_RADIUS);
    }

    /** 沉睡态：球心暗腔（正对相机的深色浅碟 + 两圈反向旋转的涡旋弧）。 */
    private static List<Quad> buildDreamCavity() {
        List<Quad> list = new ArrayList<>();
        // 深色浅碟（结构色）：读作"未装核的空腔"
        list.addAll(revolve(PROFILE_CAVITY, 12, 0.17F, 0.17F, 0.26F, CAVITY_DARK_COLOR));
        // 两圈反向旋转的涡旋弧（用层色 → 随流光变彩，在暗腔里像在转）
        list.addAll(arc(0.105F, 0.013F, 10, 220.0F, 0.10F));
        list.addAll(arc(0.145F, 0.011F, 10, 220.0F, 0.16F));
        return list;
    }

    /** 麟弹：三枚锥矢斜向前一字排开（尖端朝前）。 */
    private static List<Quad> buildDart() {
        List<Quad> list = new ArrayList<>();
        list.addAll(dartAt(0.16F, 0.0F, 0.0F, 0F));
        list.addAll(dartAt(-0.02F, 0.115F, -0.03F, 10F));
        list.addAll(dartAt(-0.02F, -0.115F, 0.03F, -10F));
        return list;
    }

    /** 单枚锥矢（局部：尖端朝 +Z）。 */
    private static List<Quad> dartAt(float z, float y, float x, float tiltDeg) {
        List<Quad> quads = revolve(PROFILE_DART, 9, 0.072F, 0.072F, 0.21F);
        float r = (float) Math.toRadians(tiltDeg);
        List<Quad> out = new ArrayList<>(quads.size());
        for (Quad q : quads) {
            out.add(new Quad(rotateVtx(q.a(), r, x, y, z), rotateVtx(q.b(), r, x, y, z),
                    rotateVtx(q.c(), r, x, y, z), rotateVtx(q.d(), r, x, y, z), q.face(), q.fixed()));
        }
        return out;
    }

    /** 麟弹：每枚锥矢后的渐隐流光尾。 */
    private static List<Quad> buildDartTrails() {
        List<Quad> list = new ArrayList<>();
        list.addAll(trail(0.16F, 0.0F, 0.0F, 0.055F, 0.34F, 0.0F));
        list.addAll(trail(-0.02F, 0.115F, -0.03F, 0.048F, 0.30F, 10F));
        list.addAll(trail(-0.02F, -0.115F, 0.03F, 0.048F, 0.30F, -10F));
        return list;
    }

    /** 单发玉：饱满大玉球。 */
    private static List<Quad> buildBall() {
        return revolve(PROFILE_ROUND, 24, 0.40F, 0.40F, 0.40F);
    }

    /** 单发玉：表面两道旋转能量纬线。 */
    private static List<Quad> buildBallLatitudes() {
        List<Quad> list = new ArrayList<>();
        list.addAll(latticeBand(0.402F, 0.011F, 24, 0F));
        list.addAll(latticeBand(0.402F, 0.010F, 24, 62F));
        return list;
    }

    /** 散弹玉：五颗小珠呈扇形散开。 */
    private static List<Quad> buildScatter() {
        List<Quad> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double ang = Math.toRadians(-64.0F + i * 32.0F);
            float z = (float) Math.cos(ang) * 0.30F;
            float y = (float) Math.sin(ang) * 0.30F;
            list.addAll(orbAt(z, y, 0.0F, 0.064F));
        }
        return list;
    }

    /** 散弹玉：五颗小珠各自的短拖尾。 */
    private static List<Quad> buildScatterTrails() {
        List<Quad> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double ang = Math.toRadians(-64.0F + i * 32.0F);
            float z = (float) Math.cos(ang) * 0.30F;
            float y = (float) Math.sin(ang) * 0.30F;
            list.addAll(trail(z, y, 0.0F, 0.026F, 0.14F, 0.0F));
        }
        return list;
    }

    /** 飞刀：三把飞刀（品字形，1 前 2 后），刃尖朝外。 */
    private static List<Quad> buildBlades() {
        List<Quad> list = new ArrayList<>();
        list.addAll(knifeAt(0.14F, 0.0F, 0.0F, 0F));
        list.addAll(knifeAt(-0.10F, 0.125F, 0.02F, 16F));
        list.addAll(knifeAt(-0.10F, -0.125F, -0.02F, -16F));
        return list;
    }

    /** 飞刀：穿透拖痕（每把刀后一条）。 */
    private static List<Quad> buildBladeTrails() {
        List<Quad> list = new ArrayList<>();
        list.addAll(trail(0.14F, 0.0F, 0.0F, 0.045F, 0.20F, 0F));
        list.addAll(trail(-0.10F, 0.125F, 0.02F, 0.038F, 0.17F, 16F));
        list.addAll(trail(-0.10F, -0.125F, -0.02F, 0.038F, 0.17F, -16F));
        return list;
    }

    /** 灵符：方形符纸（四角切角）+ 四角封印钉 + 中央朱印。 */
    private static List<Quad> buildPaper() {
        List<Quad> list = new ArrayList<>();
        List<P2> outline = List.of(
                new P2(0.18F, -0.15F), new P2(0.12F, -0.22F), new P2(-0.12F, -0.22F), new P2(-0.18F, -0.15F),
                new P2(-0.18F, 0.15F), new P2(-0.12F, 0.22F), new P2(0.12F, 0.22F), new P2(0.18F, 0.15F));
        list.addAll(extrude(outline, PAPER_HALF_THICKNESS));
        // 四角封印钉（结构色）
        float[][] nails = {{0.15F, -0.17F}, {-0.15F, -0.17F}, {-0.15F, 0.17F}, {0.15F, 0.17F}};
        for (float[] n : nails) {
            list.addAll(mark(n[0], n[1], 0.016F, 0.013F, GOLD_COLOR));
        }
        // 中央朱印（结构色）
        list.addAll(mark(0.0F, 0.0F, 0.045F, 0.052F, SEAL_RED_COLOR));
        return list;
    }

    /** 灵符：横穿纸面流动的金色符文。 */
    private static List<Quad> buildTalismanScript() {
        List<Quad> list = new ArrayList<>();
        // 三条横带，各由七段组成；段的流动坐标逐段递增，于是光带沿纸面横向游走
        float[] rows = {0.13F, 0.0F, -0.13F};
        for (int r = 0; r < rows.length; r++) {
            for (int i = 0; i < 7; i++) {
                float z = -0.14F + i * 0.047F;
                float flow = 0.12F + i * 0.11F + r * 0.04F;
                list.addAll(scriptStroke(z, rows[r], 0.030F, 0.017F, flow));
            }
        }
        return list;
    }

    /**
     * 激光枪：高科技发射器侧影。
     *
     * <p><b>刻意拆成若干块而不是一整块多边形</b>：一整块的轮廓在 16px 物品格里会读成
     * "一根长板"；分件之后枪管 / 机匣 / 握把 / 枪托各自的轮廓都在，小尺寸也读得出是枪。
     */
    private static List<Quad> buildGun() {
        List<Quad> list = new ArrayList<>();
        // 枪管（细长）
        list.addAll(extrude(box(0.12F, 0.42F, 0.065F, 0.115F), GUN_HALF_THICKNESS * 0.75F));
        // 消焰器 / 聚焦头（前端加粗，读作"枪口"）
        list.addAll(extrude(box(0.37F, 0.45F, 0.030F, 0.150F), GUN_HALF_THICKNESS * 0.85F));
        // 机匣（高厚，主体）
        list.addAll(extrude(box(-0.05F, 0.17F, 0.050F, 0.235F), GUN_HALF_THICKNESS));
        // 枪托（后部横杆）
        list.addAll(extrude(box(-0.40F, -0.05F, 0.060F, 0.160F), GUN_HALF_THICKNESS * 0.85F));
        // 握把（斜挂）
        list.addAll(extrude(List.of(
                new P2(-0.11F, 0.055F), new P2(-0.24F, 0.055F),
                new P2(-0.28F, -0.130F), new P2(-0.15F, -0.130F)), GUN_HALF_THICKNESS * 0.8F));
        // 机匣散热鳍
        for (int i = 0; i < 3; i++) {
            float z = 0.00F + i * 0.042F;
            list.addAll(extrude(box(z, z + 0.013F, 0.235F, 0.285F), GUN_HALF_THICKNESS * 0.6F));
        }
        return list;
    }

    /** 轴对齐矩形轮廓（z0..z1 × y0..y1），按逆时针给出。 */
    private static List<P2> box(float z0, float z1, float y0, float y1) {
        return List.of(new P2(z0, y0), new P2(z1, y0), new P2(z1, y1), new P2(z0, y1));
    }

    /** 激光枪：发光电池（机匣侧面的能量带）+ 枪口聚焦棱镜 + 驻轴光轴。 */
    private static List<Quad> buildGunAccents() {
        List<Quad> list = new ArrayList<>();
        float face = GUN_HALF_THICKNESS + 0.004F;
        // 驻轴光轴（沿枪管中线，两侧各一条）
        list.addAll(strip(0.39F, 0.090F, 0.15F, 0.090F, 0.010F, face));
        list.addAll(strip(0.39F, 0.090F, 0.15F, 0.090F, 0.010F, -face));
        // 机匣发光电池（两侧各一条）
        list.addAll(panel(0.06F, 0.145F, 0.055F, 0.055F, face));
        list.addAll(panel(0.06F, 0.145F, 0.055F, 0.055F, -face));
        // 枪口聚焦棱镜
        list.addAll(strip(0.435F, 0.090F, 0.385F, 0.090F, 0.030F, face));
        list.addAll(strip(0.435F, 0.090F, 0.385F, 0.090F, 0.030F, -face));
        return list;
    }

    // ==================== 几何基元 ====================

    /** 绕 Z 轴的旋转体。母线是 (半径, z) 序列。 */
    private static List<Quad> revolve(float[][] profile, int sectors,
                                      float xScale, float yScale, float zScale) {
        return revolve(profile, sectors, xScale, yScale, zScale, 0);
    }

    /**
     * 绕 Z 轴的旋转体。
     *
     * @param fixed 固定结构色（0 = 用层色；非 0 时该面片用此 RGB，忽略层色）
     */
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
                // face = 扇区序号相对<b>中心</b>的偏移（奇数扇区时中心在 0）。
                // 直接用扇区序号会把色相从中心一路拉偏（24 扇区 → ±135°），球就变成品阶外的杂色。
                list.add(new Quad(a, b, c, d, s - (sectors - 1) / 2, fixed));
            }
        }
        return list;
    }

    /** 侧视轮廓沿 X 挤出的棱柱（侧壁 + 前后端面）。 */
    private static List<Quad> extrude(List<P2> outline, float halfThickness) {
        List<Quad> list = new ArrayList<>();
        int n = outline.size();
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (P2 p : outline) {
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        float span = Math.max(1.0E-4F, maxY - minY);
        for (int i = 0; i < n; i++) {
            P2 p0 = outline.get(i);
            P2 p1 = outline.get((i + 1) % n);
            float s0 = (p0.y() - minY) / span;
            float s1 = (p1.y() - minY) / span;
            // 侧壁
            list.add(new Quad(
                    new Vtx(new Vec3(halfThickness, p0.y(), p0.z()), 0.0F, 1.0F - s0, s0, 1.0F),
                    new Vtx(new Vec3(halfThickness, p1.y(), p1.z()), 1.0F, 1.0F - s1, s1, 1.0F),
                    new Vtx(new Vec3(-halfThickness, p1.y(), p1.z()), 1.0F, 1.0F - s1, s1, 1.0F),
                    new Vtx(new Vec3(-halfThickness, p0.y(), p0.z()), 0.0F, 1.0F - s0, s0, 1.0F), 0, 0));
            // 前后端面（扇形三角化）。
            // face MUST 恒为 0：逐面色相微偏是给"旋转体的角向面"用的，
            // 端面扇形三角形若各带不同 face，相邻三角形就会在接缝处跳一个色相 —— 画成斜纹。
            for (int k = 1; k < n - 1; k++) {
                P2 p2 = outline.get(k);
                P2 p3 = outline.get(k + 1);
                float s2 = (p2.y() - minY) / span;
                float s3 = (p3.y() - minY) / span;
                list.add(new Quad(
                        new Vtx(new Vec3(halfThickness, p0.y(), p0.z()), 0.0F, 1.0F - s0, s0, 0.35F),
                        new Vtx(new Vec3(halfThickness, p2.y(), p2.z()), 0.5F, 1.0F - s2, s2, 0.35F),
                        new Vtx(new Vec3(halfThickness, p3.y(), p3.z()), 1.0F, 1.0F - s3, s3, 0.35F),
                        new Vtx(new Vec3(halfThickness, p0.y(), p0.z()), 0.0F, 1.0F - s0, s0, 0.35F), 0, 0));
                list.add(new Quad(
                        new Vtx(new Vec3(-halfThickness, p0.y(), p0.z()), 0.0F, 1.0F - s0, s0, 0.35F),
                        new Vtx(new Vec3(-halfThickness, p3.y(), p3.z()), 1.0F, 1.0F - s3, s3, 0.35F),
                        new Vtx(new Vec3(-halfThickness, p2.y(), p2.z()), 0.5F, 1.0F - s2, s2, 0.35F),
                        new Vtx(new Vec3(-halfThickness, p0.y(), p0.z()), 0.0F, 1.0F - s0, s0, 0.35F), 0, 0));
            }
        }
        return list;
    }

    /** 小球（玉珠 / 散珠）。 */
    private static List<Quad> orbAt(float z, float y, float x, float radius) {
        List<Quad> quads = revolve(PROFILE_ROUND, 10, radius, radius, radius);
        List<Quad> out = new ArrayList<>(quads.size());
        for (Quad q : quads) {
            out.add(new Quad(shiftVtx(q.a(), x, y, z), shiftVtx(q.b(), x, y, z),
                    shiftVtx(q.c(), x, y, z), shiftVtx(q.d(), x, y, z), q.face(), q.fixed()));
        }
        return out;
    }

    /** 飞刀刀身（侧视轮廓沿 X 挤出）。 */
    private static List<Quad> knifeAt(float z, float y, float x, float tiltDeg) {
        // 窄身匕首：尖端锐、刃背直、护手窄
        List<P2> outline = List.of(
                new P2(0.27F, 0.0F), new P2(0.08F, 0.030F), new P2(-0.12F, 0.034F), new P2(-0.16F, 0.052F),
                new P2(-0.22F, 0.048F), new P2(-0.22F, -0.048F), new P2(-0.16F, -0.052F), new P2(-0.12F, -0.034F),
                new P2(0.08F, -0.030F));
        float r = (float) Math.toRadians(tiltDeg);
        List<P2> rotated = new ArrayList<>(outline.size());
        for (P2 p : outline) {
            rotated.add(rotateP2(p, r));
        }
        List<Quad> quads = extrude(rotated, 0.014F);
        List<Quad> out = new ArrayList<>(quads.size());
        for (Quad q : quads) {
            out.add(new Quad(shiftVtx(q.a(), x, y, z), shiftVtx(q.b(), x, y, z),
                    shiftVtx(q.c(), x, y, z), shiftVtx(q.d(), x, y, z), q.face(), q.fixed()));
        }
        return out;
    }

    /** 渐隐流光尾：沿 -Z 的锥形条带。 */
    private static List<Quad> trail(float z, float y, float x, float halfWidth, float length, float tiltDeg) {
        List<Quad> list = new ArrayList<>();
        int segs = 5;
        float r = (float) Math.toRadians(tiltDeg);
        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float z0 = z - length * t0;
            float z1 = z - length * t1;
            float w0 = halfWidth * (1.0F - t0);
            float w1 = halfWidth * (1.0F - t1);
            float y0 = y + (float) Math.tan(r) * length * t0;
            float y1 = y + (float) Math.tan(r) * length * t1;
            list.add(new Quad(
                    new Vtx(new Vec3(x, y0 - w0, z0), 0.0F, t0, 0.30F + 0.70F * t0, 1.0F),
                    new Vtx(new Vec3(x, y0 + w0, z0), 1.0F, t0, 0.30F + 0.70F * t0, 1.0F),
                    new Vtx(new Vec3(x, y1 + w1, z1), 1.0F, t1, 0.30F + 0.70F * t1, 1.0F),
                    new Vtx(new Vec3(x, y1 - w1, z1), 0.0F, t1, 0.30F + 0.70F * t1, 1.0F), i, 0));
        }
        return list;
    }

    /**
     * 环绕球面的一道带（能量纬线）。
     *
     * <p><b>必须绕模型的 X 轴成环</b>：模型的 Z 轴在屏幕上指向右（长轴），绕 Z 的环在侧视下
     * 会退化成一条竖线；绕 X 的环才是屏幕上的一圈。
     *
     * @param tiltDeg 环平面绕模型 Z 轴的倾角（度）
     */
    private static List<Quad> latticeBand(float radius, float halfWidth, int segments, float tiltDeg) {
        List<Quad> list = new ArrayList<>(segments);
        float t = (float) Math.toRadians(tiltDeg);
        float cos = (float) Math.cos(t);
        float sin = (float) Math.sin(t);
        for (int i = 0; i < segments; i++) {
            double a0 = 2.0 * Math.PI * i / segments;
            double a1 = 2.0 * Math.PI * (i + 1) / segments;
            float y0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float y1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            float ry0 = y0 * cos - z0 * sin;
            float rz0 = y0 * sin + z0 * cos;
            float ry1 = y1 * cos - z1 * sin;
            float rz1 = y1 * sin + z1 * cos;
            list.add(new Quad(
                    new Vtx(new Vec3(-halfWidth, ry0, rz0), (float) i / segments, 0.0F, (float) i / segments, 1.0F),
                    new Vtx(new Vec3(-halfWidth, ry1, rz1), (float) (i + 1) / segments, 0.0F, (float) (i + 1) / segments, 1.0F),
                    new Vtx(new Vec3(halfWidth, ry1, rz1), (float) (i + 1) / segments, 0.0F, (float) (i + 1) / segments, 1.0F),
                    new Vtx(new Vec3(halfWidth, ry0, rz0), (float) i / segments, 0.0F, (float) i / segments, 1.0F), 0, 0));
        }
        return list;
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

    /** 结构色标记（符纸的封印钉 / 朱印）：贴在纸面两侧的一小块。 */
    private static List<Quad> mark(float z, float y, float halfZ, float halfY, int color) {
        float x = PAPER_HALF_THICKNESS + 0.004F;
        List<Quad> list = new ArrayList<>(2);
        for (float side : new float[]{x, -x}) {
            list.add(new Quad(
                    new Vtx(new Vec3(side, y - halfY, z - halfZ), 0.0F, 0.0F, 0.5F, 0.0F),
                    new Vtx(new Vec3(side, y + halfY, z - halfZ), 0.0F, 0.0F, 0.5F, 0.0F),
                    new Vtx(new Vec3(side, y + halfY, z + halfZ), 1.0F, 0.0F, 0.5F, 0.0F),
                    new Vtx(new Vec3(side, y - halfY, z + halfZ), 1.0F, 0.0F, 0.5F, 0.0F), 0, color));
        }
        return list;
    }

    /** 符文笔画（一小段金线）。 */
    private static List<Quad> scriptStroke(float z, float y, float halfZ, float halfY, float flow) {
        float x = PAPER_HALF_THICKNESS + 0.006F;
        List<Quad> list = new ArrayList<>(2);
        for (float side : new float[]{x, -x}) {
            list.add(new Quad(
                    new Vtx(new Vec3(side, y - halfY, z - halfZ), 0.0F, 0.0F, flow, 0.0F),
                    new Vtx(new Vec3(side, y + halfY, z - halfZ), 0.0F, 0.0F, flow, 0.0F),
                    new Vtx(new Vec3(side, y + halfY, z + halfZ), 1.0F, 0.0F, flow, 0.0F),
                    new Vtx(new Vec3(side, y - halfY, z + halfZ), 1.0F, 0.0F, flow, 0.0F), 0, GOLD_COLOR));
        }
        return list;
    }

    /** 枪身上的窄条（驻轴光轴 / 枪口棱镜）：沿 Z 的线段。 */
    private static List<Quad> strip(float z0, float y0, float z1, float y1, float halfWidth, float x) {
        float dz = z1 - z0;
        float dy = y1 - y0;
        float len = Math.max(1.0E-4F, (float) Math.hypot(dz, dy));
        float oz = -dy / len * halfWidth;
        float oy = dz / len * halfWidth;
        return List.of(new Quad(
                new Vtx(new Vec3(x, y0 - oy, z0 - oz), 0.0F, 0.0F, 0.0F, 1.0F),
                new Vtx(new Vec3(x, y0 + oy, z0 + oz), 1.0F, 0.0F, 0.0F, 1.0F),
                new Vtx(new Vec3(x, y1 + oy, z1 + oz), 1.0F, 0.0F, 1.0F, 1.0F),
                new Vtx(new Vec3(x, y1 - oy, z1 - oz), 0.0F, 0.0F, 1.0F, 1.0F), 0, 0));
    }

    /** 机匣侧面的发光电池（贴在 x = ±halfThickness 处的一块竖面板）。 */
    private static List<Quad> panel(float centerZ, float centerY, float halfZ, float halfY, float x) {
        return List.of(new Quad(
                new Vtx(new Vec3(x, centerY - halfY, centerZ - halfZ), 0.0F, 0.0F, 0.0F, 1.0F),
                new Vtx(new Vec3(x, centerY - halfY, centerZ + halfZ), 1.0F, 0.0F, 0.0F, 1.0F),
                new Vtx(new Vec3(x, centerY + halfY, centerZ + halfZ), 1.0F, 0.0F, 1.0F, 1.0F),
                new Vtx(new Vec3(x, centerY + halfY, centerZ - halfZ), 0.0F, 0.0F, 1.0F, 1.0F), 0, 0));
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

    /** 侧视轮廓的平面旋转（弧度）。 */
    private static P2 rotateP2(P2 p, float rad) {
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        return new P2(p.z() * c - p.y() * s, p.z() * s + p.y() * c);
    }

    /** 顶点平移。 */
    private static Vtx shiftVtx(Vtx v, float x, float y, float z) {
        return new Vtx(v.p().add(x, y, z), v.u(), v.v(), v.s(), v.e());
    }

    /** 顶点在侧视平面内旋转（绕 x 轴）后平移。 */
    private static Vtx rotateVtx(Vtx v, float rad, float x, float y, float z) {
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        Vec3 p = v.p();
        float pz = (float) (p.z * c - p.y * s);
        float py = (float) (p.z * s + p.y * c);
        return new Vtx(new Vec3(p.x + x, py + y, pz + z), v.u(), v.v(), v.s(), v.e());
    }

    private CoreOrbGeometry() {
    }
}
