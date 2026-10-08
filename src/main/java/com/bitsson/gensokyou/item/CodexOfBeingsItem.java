package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.TouhouNpcEntity;
import com.bitsson.gensokyou.item.codex.CodexData;
import com.bitsson.gensokyou.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 众生典籍：右键合格 {@link Mob} 将其收容（无掉落），单槽累计同种 mob 数量。
 * 满 20 进入不可逆附魔形态，此后右键无功能。为「众生余录」仪式提供只读访问器。
 */
public class CodexOfBeingsItem extends Item {

    /** boss 实体类型标签：命中即不可收容（不依赖 boss 血条，后者无公开查询 API）。 */
    public static final TagKey<EntityType<?>> BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "bosses"));
    /** 追加不可收容类型标签（config 列表与之取并集）。 */
    public static final TagKey<EntityType<?>> UNCAPTURABLE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "uncapturable"));

    public CodexOfBeingsItem(Properties properties) {
        super(properties);
    }

    // ---- 只读访问器（供未来「众生余录」仪式调用，不消耗本书）----

    public static CodexData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CODEX_DATA.get(), CodexData.EMPTY);
    }

    public static Optional<ResourceLocation> getSpecies(ItemStack stack) {
        return data(stack).species();
    }

    public static int getCount(ItemStack stack) {
        return data(stack).count();
    }

    public static boolean isFull(ItemStack stack) {
        return data(stack).isFull();
    }

    public static Optional<EntityType<?>> getEntityType(ItemStack stack) {
        return data(stack).species().flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional);
    }

    // ---- 交互 ----

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
                                                  InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) {
            return entity instanceof Mob ? InteractionResult.sidedSuccess(true) : InteractionResult.PASS;
        }
        if (!(entity instanceof Mob mob)) {
            return InteractionResult.PASS;
        }
        // 创造模式下 Player#interactOn 传入的是手持物品副本，必须写回真实手持栈。
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof CodexOfBeingsItem)) {
            held = stack;
        }
        CodexData data = data(held);
        if (data.isFull()) {
            return InteractionResult.PASS;
        }
        if (!canCapture(mob)) {
            return InteractionResult.PASS;
        }
        if (isTamedAnimal(mob) && !data.tamedWarned()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.codex_tamed_warning").withStyle(ChatFormatting.YELLOW),
                    false);
            held.set(ModDataComponents.CODEX_DATA.get(), data.withTamedWarned());
            return InteractionResult.SUCCESS;
        }

        held.set(ModDataComponents.CODEX_DATA.get(), data.capture(EntityType.getKey(mob.getType())));
        if (CodexOfBeingsItem.isFull(held) && player instanceof ServerPlayer serverPlayer) {
            com.bitsson.gensokyou.event.AchievementAwards.award(serverPlayer, "codex_full");
        }
        mob.discard();

        ServerLevel serverLevel = (ServerLevel) level;
        serverLevel.sendParticles(ParticleTypes.PORTAL,
                mob.getX(), mob.getY() + 0.5D, mob.getZ(), 24, 0.3D, 0.5D, 0.3D, 0.05D);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    // ---- 资格判定 ----

    public static boolean canCapture(Mob mob) {
        if (mob instanceof TouhouNpcEntity) {
            return false;
        }
        EntityType<?> type = mob.getType();
        if (type.is(BOSSES) || type.is(UNCAPTURABLE)) {
            return false;
        }
        return !isConfigBlacklisted(type);
    }

    private static boolean isConfigBlacklisted(EntityType<?> type) {
        ResourceLocation key = EntityType.getKey(type);
        for (String raw : GensokyouConfig.CAPTURE_BLACKLIST.get()) {
            ResourceLocation parsed = ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
            if (parsed == null) {
                Gensokyou.LOGGER.debug("[codex-of-beings] skipping malformed blacklist entry: {}", raw);
                continue;
            }
            if (parsed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTamedAnimal(Mob mob) {
        if (mob instanceof TamableAnimal tamable && tamable.isTame()) {
            return true;
        }
        return mob instanceof AbstractHorse horse && horse.isTamed();
    }

    // ---- 表现 ----

    @Override
    public boolean isFoil(ItemStack stack) {
        return data(stack).isFull();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CodexData data = data(stack);
        if (data.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.gensokyou.codex_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        Component typeName = speciesName(data.species().orElseThrow());
        if (data.isFull()) {
            tooltip.add(Component.translatable("tooltip.gensokyou.codex_full", typeName)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.gensokyou.codex_progress",
                    typeName, data.count(), CodexData.MAX_CAPTURE).withStyle(ChatFormatting.GRAY));
        }
    }

    private static Component speciesName(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .<Component>map(EntityType::getDescription)
                .orElseGet(() -> Component.literal(id.toString()));
    }
}
