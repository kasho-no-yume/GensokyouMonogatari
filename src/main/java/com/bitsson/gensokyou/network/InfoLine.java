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
 * </ul>
 * color 为 ARGB 文本色（0 = 默认面板色）。
 */
public record InfoLine(String textKey, String[] textArgs, String iconItemId, int color,
                       float progress, @Nullable Boolean state) {

    public static final StreamCodec<FriendlyByteBuf, InfoLine> STREAM_CODEC =
            StreamCodec.ofMember(InfoLine::write, InfoLine::read);

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
        return new InfoLine(textKey, args, icon, color, progress, state);
    }
}
