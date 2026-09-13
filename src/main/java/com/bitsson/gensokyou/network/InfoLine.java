package com.bitsson.gensokyou.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import javax.annotation.Nullable;

/**
 * 仪式 GUI 信息行的描述性数据：行为侧拼行、客户端照画，Screen 不感知具体仪式语义。
 *
 * <p>一行可同时承载（均可空）：
 * <ul>
 *   <li>textKey + args —— 可本地化文本（key 无对应语言键时原样显示 key；args 逐个拼入）</li>
 *   <li>iconItemId —— 行首物品图标（注册表 id 字符串，空串=无）</li>
 *   <li>progress —— [0,1] 进度条（负值=不画）</li>
 *   <li>state —— 三态校验标记（null=无 / true=✓ / false=✗）</li>
     *   <li>actionId —— 可交互行的操作 id（0=纯展示；点击经菜单按钮通道回报行为 onUiAction）</li>
     *   <li>controlKind —— 控件类型（{@link #CONTROL_NONE}/{@link #CONTROL_LINK}）</li>
     *   <li>linkState —— 三态控件当前值（0=未选 / 1=入 / 2=出；仅 CONTROL_LINK 有意义）</li>
     *   <li>tipKey + tipArgs —— 悬浮 tooltip（可本地化一行；空串=无）。
     *       CONTROL_LINK 行客户端自动在 tip 首行前置对象名称</li>
     * </ul>
 * color 为 ARGB 文本色（0 = 默认面板色）。
 */
public record InfoLine(String textKey, String[] textArgs, String iconItemId, int color,
                       float progress, @Nullable Boolean state,
                       int actionId, int controlKind, int linkState,
                       String tipKey, String[] tipArgs) {

    public static final int CONTROL_NONE = 0;
    /** 三态行控件：textKey 为对象名称键；明细（坐标/距离/上限等）放 tipArgs。 */
    public static final int CONTROL_LINK = 1;

    public static final int LINK_NONE = 0;
    public static final int LINK_IN = 1;
    public static final int LINK_OUT = 2;

    public static final StreamCodec<FriendlyByteBuf, InfoLine> STREAM_CODEC =
            StreamCodec.ofMember(InfoLine::write, InfoLine::read);

    /** 纯展示行构造（既有行为侧调用点零改动）。 */
    public InfoLine(String textKey, String[] textArgs, String iconItemId, int color,
                    float progress, @Nullable Boolean state) {
        this(textKey, textArgs, iconItemId, color, progress, state, 0, CONTROL_NONE, LINK_NONE,
                "", new String[0]);
    }

    /** 三态链接行：可见 = 状态标记 + 名称；tipKey/tipArgs 悬浮明细。 */
    public static InfoLine linkRow(String nameKey, int color, int actionId, int linkState,
                                   String tipKey, String[] tipArgs) {
        return new InfoLine(nameKey, new String[0], "", color, -1F, null,
                actionId, CONTROL_LINK, linkState, tipKey, tipArgs);
    }

    /** 带悬浮明细的展示行（紧凑值可见，精确值进 tooltip）。 */
    public static InfoLine tipped(String textKey, String[] textArgs, int color,
                                  String tipKey, String[] tipArgs) {
        return new InfoLine(textKey, textArgs, "", color, -1F, null, 0, CONTROL_NONE,
                LINK_NONE, tipKey, tipArgs);
    }

    public boolean interactive() {
        return actionId > 0 && controlKind != CONTROL_NONE;
    }

    public boolean tipped() {
        return !tipKey.isEmpty();
    }

    /** 大数字紧凑化（≥1e6→x.xxM / ≥1e3→x.xxk / 否则原值）。信息行与固定头数值 MUST NOT raw long。 */
    public static String compact(long v) {
        double a = Math.abs(v);
        if (a >= 1_000_000D) {
            return trimZeros(String.format(java.util.Locale.ROOT, "%.2f", a / 1_000_000D)) + "M";
        }
        if (a >= 1_000D) {
            return trimZeros(String.format(java.util.Locale.ROOT, "%.2f", a / 1_000D)) + "k";
        }
        return String.valueOf(v);
    }

    private static String trimZeros(String s) {
        String r = s;
        while (r.endsWith("0")) {
            r = r.substring(0, r.length() - 1);
        }
        return r.endsWith(".") ? r.substring(0, r.length() - 1) : r;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.textKey);
        buf.writeVarInt(this.textArgs.length);
        for (String arg : this.textArgs) {
            buf.writeUtf(arg);
        }
        buf.writeUtf(this.iconItemId);
        buf.writeInt(this.color);
        buf.writeFloat(this.progress);
        if (this.state != null) {
            buf.writeBoolean(true);
            buf.writeBoolean(this.state);
        } else {
            buf.writeBoolean(false);
        }
        buf.writeVarInt(this.actionId);
        buf.writeVarInt(this.controlKind);
        buf.writeVarInt(this.linkState);
        buf.writeUtf(this.tipKey);
        buf.writeVarInt(this.tipArgs.length);
        for (String arg : this.tipArgs) {
            buf.writeUtf(arg);
        }
    }

    private static InfoLine read(FriendlyByteBuf buf) {
        String textKey = buf.readUtf();
        int argCount = buf.readVarInt();
        String[] args = new String[argCount];
        for (int i = 0; i < argCount; i++) {
            args[i] = buf.readUtf();
        }
        String icon = buf.readUtf();
        int color = buf.readInt();
        float progress = buf.readFloat();
        Boolean state = buf.readBoolean() ? buf.readBoolean() : null;
        int actionId = buf.readVarInt();
        int controlKind = buf.readVarInt();
        int linkState = buf.readVarInt();
        String tipKey = buf.readUtf();
        int tipCount = buf.readVarInt();
        String[] tipArgs = new String[tipCount];
        for (int i = 0; i < tipCount; i++) {
            tipArgs[i] = buf.readUtf();
        }
        return new InfoLine(textKey, args, icon, color, progress, state,
                actionId, controlKind, linkState, tipKey, tipArgs);
    }
}
