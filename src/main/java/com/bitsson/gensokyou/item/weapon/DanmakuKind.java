package com.bitsson.gensokyou.item.weapon;

/**
 * 弹幕类型（弹核决定的开火形态）。
 *
 * <p>与 {@link FirePattern#isLaser()} / {@code isTalisman()} 的区别：那两个是<b>行为判据</b>
 * （由 config 的实际数值决定，改 config 可能翻转），本枚举是<b>类型标称</b>，恒定不变，
 * 专供 tooltip / 文档 / 分类查询使用。MUST NOT 用本枚举替换 {@code isLaser()} 的判定逻辑。
 */
public enum DanmakuKind {

    SPHERE("sphere"),
    KNIFE("knife"),
    TALISMAN("talisman"),
    LASER("laser");

    private final String id;

    DanmakuKind(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String langKey() {
        return "danmaku.gensokyou." + this.id;
    }
}
