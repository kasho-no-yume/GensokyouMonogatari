package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;

/**
 * 残影 BOSS 的渲染注册点（{@code add-remnant-touhou-bosses} 的「替换零成本」落点）。
 *
 * <p>狐火与傩神楽面两只残影当前<b>没有正式模型</b>，这是<b>占位而非终态</b>。故渲染器不在实体类里
 * 硬编，也不写在 {@code GensokyouClient} 的注册 lambda 里，而是由本表按
 * {@link EntityType} 解析：
 *
 * <pre>{@code
 *   // 正式美术到位时，改这里指向 GeckoLib renderer 工厂即可，
 *   // 实体类与召唤通路一行都不用动。
 *   REMNANTS.put(ModEntityTypes.KITSUNEBI.get(), new Binding(
 *           GensokyouTextures.KITSUNEBI, 0.5F, 0.8F));
 * }</pre>
 *
 * <p>满足 {@code project.md} §5.2「替换零成本原则」：占位期保证未来替换时不改任何
 * 代码与引用，路径按最终命名建立，正式资源到位后仅覆盖文件内容。
 */
public final class RemnantBossRenderer {

    /** 一个残影的渲染绑定：贴图路径 + 阴影半径 + 缩放。 */
    public record Binding(ResourceLocation texture, float shadowRadius, float scale) {
    }

    private static final Map<EntityType<?>, Binding> REMNANTS = new HashMap<>();

    static {
        REMNANTS.put(ModEntityTypes.KITSUNEBI.get(),
                new Binding(com.bitsson.gensokyou.client.GensokyouTextures.KITSUNEBI, 0.5F, 0.8F));
        REMNANTS.put(ModEntityTypes.NOMEN_MASK.get(),
                new Binding(com.bitsson.gensokyou.client.GensokyouTextures.NOMEN_MASK, 0.6F, 1.0F));
    }

    private RemnantBossRenderer() {
    }

    /** 该实体是否走残影渲染通路。 */
    public static boolean isRemnant(EntityType<?> type) {
        return REMNANTS.containsKey(type);
    }

    public static Binding bindingOf(EntityType<?> type) {
        return REMNANTS.get(type);
    }

    /** 为某实体类型创建渲染器。调用方只需转发，无类型特判。 */
    public static <T extends Mob> SkinMobRenderer<T> create(EntityType<T> type,
                                                              EntityRendererProvider.Context context) {
        Binding binding = REMNANTS.get(type);
        if (binding == null) {
            throw new IllegalArgumentException("未登记的残影实体：" + type);
        }
        return new SkinMobRenderer<>(context, binding.shadowRadius(), binding.scale(),
                binding.texture());
    }

    /** 已登记的残影（调试用）。 */
    public static Map<EntityType<?>, Binding> all() {
        return Map.copyOf(REMNANTS);
    }
}
