package com.bitsson.gensokyou.item.weapon;

/**
 * 核心的外壳档案：描述一枚弹核（或"尚未装核"的状态）在核心中的呈现形态。
 *
 * <p>档案分两类：
 * <ul>
 *   <li><b>沉睡态</b>（{@link #DREAM}）——slot1 为空时核心呈现的样子：全彩流光球 + 火冠气焰。
 *       它<b>没有品阶归属</b>（品阶随装入的核而来），故不受品阶配色环约束。</li>
 *   <li><b>武装态</b>——装入弹核后，核心本体保持流光球本尊，基色取该核的品阶色，
 *       外围浮现该核对应的东方幻想灵阵与星轨。</li>
 * </ul>
 *
 * <p>档案由 {@link DanmakuKind} 提供默认值（见 {@link #byKind}），**单个核 MAY 覆盖**——
 * 同属 {@code SPHERE} 的单发玉（饱满大玉球）与散弹玉（五珠扇）SHALL 呈现不同形态，
 * 否则"外形随核变化"在同档核之间就失效了。
 *
 * <p>各档案的具体几何由渲染器按档案 id 构建并<b>缓存</b>（静态几何每帧重建会白烧 CPU）；
 * 本枚举只承载"是哪一种形态"这一身份，不承载顶点数据。
 *
 * <p>本枚举是<b>编译期常量</b>（{@code BulletCoreItem} 上一个 {@code private final} 字段），
 * <b>不落 NBT</b>，故枚举项更名不影响任何存量数据。
 */
public enum CoreShellProfile {

    /** 沉睡态：全彩流光球 + 火冠气焰 + 球心暗腔（尚未装核，无品阶归属）。 */
    DREAM,

    /** 锥矢：三棱星芒破空法阵（麟弹核）。 */
    DART,

    /** 浑天双纬聚灵环（单发玉核）。 */
    BALL,

    /** 五曜伴生星珠阵（散弹玉核）。 */
    SCATTER,

    /** 六芒星交错剑阵（飞刀核）。 */
    BLADES,

    /** 八角金边朱印结界阵（灵符核）。 */
    PAPER,

    /** 六角棱镜聚焦星盘（激光核）。 */
    GUN;

    /**
     * 该弹幕种类的默认外壳档案。
     *
     * <p>MUST NOT 在别处另写一份映射——外壳档案是"种类 → 形态"的唯一事实源。
     */
    public static CoreShellProfile byKind(DanmakuKind kind) {
        return switch (kind) {
            case SPHERE -> BALL;
            case KNIFE -> BLADES;
            case TALISMAN -> PAPER;
            case LASER -> GUN;
            case CONE -> DART;
        };
    }

    /** 该档案是否为"尚未装核"的沉睡态（无品阶归属，走全光谱流光）。 */
    public boolean isDormant() {
        return this == DREAM;
    }
}
