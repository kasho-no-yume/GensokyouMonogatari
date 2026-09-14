package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** S2C：仪式界面全量信息（开界面与状态变更后推送）。 */
public record RitualInfoPayload(long pos, String patternId, int tier, boolean enabled,
                                boolean toggleable, long stored, long capacity,
                                List<Action> actions, String activeRecipeId,
                                String statusKey, List<InfoLine> infoLines) implements CustomPacketPayload {

    /** 行为注入的自定义操作按钮（labelKey 客户端本地化；enabled=false 客户端置灰）。 */
    public record Action(int id, String labelKey, boolean enabled) {
    }

    public static final Type<RitualInfoPayload> TYPE =
            new Type<>(Gensokyou.id("ritual_info"));

    public static final StreamCodec<FriendlyByteBuf, RitualInfoPayload> STREAM_CODEC =
            StreamCodec.ofMember(RitualInfoPayload::write, RitualInfoPayload::read);

    private static void write(RitualInfoPayload payload, FriendlyByteBuf buf) {
        buf.writeLong(payload.pos);
        buf.writeUtf(payload.patternId);
        buf.writeVarInt(payload.tier);
        buf.writeBoolean(payload.enabled);
        buf.writeBoolean(payload.toggleable);
        buf.writeLong(payload.stored);
        buf.writeLong(payload.capacity);
        buf.writeVarInt(payload.actions.size());
        for (Action action : payload.actions) {
            buf.writeVarInt(action.id());
            buf.writeUtf(action.labelKey());
            buf.writeBoolean(action.enabled());
        }
        buf.writeUtf(payload.activeRecipeId);
        buf.writeUtf(payload.statusKey);
        buf.writeVarInt(payload.infoLines.size());
        for (InfoLine line : payload.infoLines) {
            InfoLine.STREAM_CODEC.encode(buf, line);
        }
    }

    private static RitualInfoPayload read(FriendlyByteBuf buf) {
        long pos = buf.readLong();
        String patternId = buf.readUtf();
        int tier = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        boolean toggleable = buf.readBoolean();
        long stored = buf.readLong();
        long capacity = buf.readLong();
        int actionCount = buf.readVarInt();
        List<Action> actions = new ArrayList<>(actionCount);
        for (int i = 0; i < actionCount; i++) {
            actions.add(new Action(buf.readVarInt(), buf.readUtf(), buf.readBoolean()));
        }
        String activeRecipeId = buf.readUtf();
        String statusKey = buf.readUtf();
        int lineCount = buf.readVarInt();
        List<InfoLine> infoLines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            infoLines.add(InfoLine.STREAM_CODEC.decode(buf));
        }
        return new RitualInfoPayload(pos, patternId, tier, enabled, toggleable,
                stored, capacity, List.copyOf(actions), activeRecipeId, statusKey,
                List.copyOf(infoLines));
    }

    /** 服务端快照：由核心 BE 当前态 + 行为侧信息行组装（清单/燃烧行等语义全在 behavior）。 */
    public static RitualInfoPayload snapshot(ServerLevel level, BlockPos pos,
                                             RitualCoreBlockEntity core, String statusKey) {
        RitualMatch match = core.activeMatch();
        List<Action> actions = new ArrayList<>();
        List<InfoLine> infoLines = new ArrayList<>();
        String patternId = "";
        int tier = 0;
        boolean toggleable = false;
        if (match != null) {
            patternId = match.patternId().toString();
            tier = match.level();
            Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(match.patternId());
            if (patternOpt.isPresent()) {
                toggleable = patternOpt.get().toggleable();
            }
            RitualBehaviors.get(match.patternId()).ifPresentOrElse(behavior -> {
                behavior.uiActions(level, pos, match, core).forEach(action ->
                        actions.add(new Action(action.id(), action.labelKey(), action.enabled())));
                infoLines.addAll(behavior.uiInfo(level, pos, match, core));
            }, () -> infoLines.addAll(RitualBehavior.defaultUiInfo(level, pos, match, core)));
        }
        return new RitualInfoPayload(pos.asLong(), patternId, tier, core.isEnabled(), toggleable,
                core.getStored(), core.getCapacity(), List.copyOf(actions),
                core.activeRecipeId() == null ? "" : core.activeRecipeId().toString(),
                statusKey, List.copyOf(infoLines));
    }

    public BlockPos blockPos() {
        return BlockPos.of(pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
