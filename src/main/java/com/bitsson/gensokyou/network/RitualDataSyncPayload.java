package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualSmeltRuleLoader;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * S2C 全量仪式数据快照（guide-book 方案 B）。
 * 登录与 datapack 重载时下发 rituals / ritual_recipes / ritual_smelt_recipes 的原始 JSON。
 * 原始约 150KB，超过 STRING_UTF8 默认上限，故 GZIP 压缩为 byte[] 传输（压缩后约 15KB）。
 *
 * <p>三类数据都要走这条路：{@code AddReloadListenerEvent} 只在逻辑服务端触发，
 * 专用客户端不加载这些 reload listener，直接读 loader 在联机上恒为空。
 */
public record RitualDataSyncPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<RitualDataSyncPayload> TYPE =
            new Type<>(Gensokyou.id("ritual_data_sync"));

    public static final StreamCodec<FriendlyByteBuf, RitualDataSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.byteArray(1_048_576), RitualDataSyncPayload::data,
                    RitualDataSyncPayload::new);

    /** 服务端组装：patterns / recipes / smelt_rules 以 id → 原始 JSON 对象打包并 GZIP。 */
    public static RitualDataSyncPayload snapshot() {
        JsonObject patterns = new JsonObject();
        RitualPatternLoader.rawAll().forEach((id, json) -> patterns.add(id.toString(), json));
        JsonObject recipes = new JsonObject();
        RitualRecipeLoader.rawAll().forEach((id, json) -> recipes.add(id.toString(), json));
        JsonObject smeltRules = new JsonObject();
        RitualSmeltRuleLoader.rawAll().forEach((id, json) -> smeltRules.add(id.toString(), json));
        JsonObject root = new JsonObject();
        root.add("patterns", patterns);
        root.add("recipes", recipes);
        root.add("smelt_rules", smeltRules);
        return new RitualDataSyncPayload(compress(root.toString()));
    }

    /** 解压为 JSON 字符串（客户端重建缓存用）。 */
    public String json() {
        return decompress(data);
    }

    private static byte[] compress(String value) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(value.getBytes(StandardCharsets.UTF_8));
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compress ritual data snapshot", e);
        }
    }

    private static String decompress(byte[] value) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(value))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decompress ritual data snapshot", e);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
