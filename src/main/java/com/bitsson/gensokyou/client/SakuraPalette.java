package com.bitsson.gensokyou.client;

/**
 * 樱符主题调色板 —— <b>Java 侧唯一色源</b>。
 *
 * <p>色值出处：{@code tools/textures/gui_sakura.py} 的 {@code PAL}（该文件的头部声明
 * 「本文件的 PAL 是唯一色源」）。本类与那份 PAL <b>逐值一致</b>，
 * 由 {@code SakuraPaletteTest} 断言（改了 Python 忘了改 Java（或反之）会红）。
 *
 * <p><b>为什么要有这个类</b>：调色板原先只存在于 Python 侧，Java 侧各处散落硬编码
 * （樱粉一度同时存在 0xC77A92 / 0xC87A92 / 0xCB7A92 三个值），
 * 跨面板对不上颜色时无从查起。先例：{@code TierPalette}（品阶色单一色源）。
 *
 * <p>取值约定：
 * <ul>
 *   <li>「纸 / 面 / 凹」是<b>和纸底</b>三档，用在面板上（浅）。
 *   <li>「樱粉 / 樱深红 / 金」是<b>装饰色</b>，用在框线、饰件、强调。
 *   <li>「米白 / 墨」是<b>文字色</b>：米白配深底（覆盖窗、HUD），墨配浅纸（面板）。
 *   <li>{@link #DARK} 是<b>HUD 专用深底</b> —— HUD 压在世界上（不是压在和纸上），
 *       任何底衬都必须保证对比度，故沿用原版「深色半透明 + 浅色前景」的思路。
 * </ul>
 */
public final class SakuraPalette {

    // —— 纸与面（和纸底三档，浅）——

    /** 纸底（象牙樱）。 */
    public static final int PAPER = 0xFFFDF0F2;
    /** 卡片面底（比纸底暗一档）。 */
    public static final int CARD = 0xFFF9E4E8;
    /** 槽内凹底。 */
    public static final int RECESS = 0xFFF3D8DF;

    // —— 樱色系（装饰色）——

    /** 樱粉主线（卡框 / 分隔线 / 描边）。 */
    public static final int PINK = 0xFFCB7A92;
    /** 樱粉柔线（物品栏格线 / 次级描边）。 */
    public static final int PINK_SOFT = 0xFFCFA0AE;
    /** 樱粉暗调（凹线 / 未激活态）。 */
    public static final int PINK_DARK = 0xFFBA687E;
    /** 樱深红（强调 / 符头 / 按钮主体）。 */
    public static final int ROSE = 0xFFBA4A62;
    /** 金（角饰 / 高光 / 焦点描边）。 */
    public static final int GOLD = 0xFFD8BA84;

    // —— 文字 ——

    /** 米白（深底上的文字：覆盖窗、HUD）。 */
    public static final int CREAM = 0xFFF0DCE2;
    /** 墨 / 深紫红（浅纸上的文字：面板内计数、输入框正文）。 */
    public static final int INK = 0xFF4A1A2C;

    // —— HUD 深底 ——

    /** HUD 深底（轨槽 / 技能格）。HUD 压在世界上，必须比纸底暗得多。 */
    public static final int DARK = 0xFF2E1E28;

    // —— 滚动条 ——

    /** 滚动条轨道（樱粉柔）。 */
    public static final int SCROLL_TRACK = 0xFFD8B4C2;
    /** 滚动条滑块（樱粉暗）。 */
    public static final int SCROLL_THUMB = 0xFFB0738C;

    private SakuraPalette() {
    }
}
