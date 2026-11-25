package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.dialogue.DialogueGraph;
import com.bitsson.gensokyou.dialogue.DialogueManager;
import com.bitsson.gensokyou.dialogue.DialogueNode;
import com.bitsson.gensokyou.dialogue.DialogueOption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 博丽灵梦：在出生锚点周围的庭院内散步，其余行为复用普通 NPC。 */
public class ReimuEntity extends TouhouNpcEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.reimu.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.reimu.walk");
    private static final DialogueGraph DIALOGUE = new DialogueGraph(Gensokyou.id("reimu"), Map.of(
            "greet", new DialogueNode(Component.translatable("dialogue.gensokyou.reimu.greet"), List.of(
                    new DialogueOption(Component.translatable("dialogue.gensokyou.reimu.opt.shrine"),
                            Optional.of("shrine"), Optional.empty()),
                    new DialogueOption(Component.translatable("dialogue.gensokyou.reimu.opt.farewell"),
                            Optional.empty(), Optional.empty()))),
            "shrine", new DialogueNode(Component.translatable("dialogue.gensokyou.reimu.shrine"), List.of(
                    new DialogueOption(Component.translatable("dialogue.gensokyou.reimu.opt.back"),
                            Optional.of("greet"), Optional.empty()),
                    new DialogueOption(Component.translatable("dialogue.gensokyou.reimu.opt.farewell"),
                            Optional.empty(), Optional.empty())))), "greet");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public ReimuEntity(EntityType<? extends ReimuEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TouhouNpcEntity.createAttributes().add(Attributes.MOVEMENT_SPEED, 0.22D);
    }

    @Override
    protected boolean isStationary() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(3, new CourtyardWanderGoal());
    }

    /** 与自然出生点配合，覆盖主庭院的空地，避开西侧台阶、北翼境界台和东侧拜殿。 */
    public AABB getWalkingBounds() {
        Vec3 center = this.getAnchorPos() == null ? this.position() : this.getAnchorPos();
        return new AABB(center.x - 8.0D, center.y - 0.5D, center.z - 9.0D,
                center.x + 8.0D, center.y + 1.5D, center.z + 9.0D);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 anchor = this.getAnchorPos();
        if (!this.level().isClientSide && anchor != null && !this.getWalkingBounds().contains(this.position())) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setAnchorPos(anchor);
        }
    }

    @Override
    public DialogueGraph dialogueGraph() {
        return DIALOGUE;
    }

    private boolean isInteracting() {
        return this.getTradingPlayer() != null || DialogueManager.isTalkingTo(this);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
    }

    private final class CourtyardWanderGoal extends Goal {
        private Path path;
        private int nextWalkTick;

        private CourtyardWanderGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (ReimuEntity.this.tickCount < nextWalkTick || isInteracting()) {
                return false;
            }
            nextWalkTick = ReimuEntity.this.tickCount + 40 + getRandom().nextInt(61);
            AABB bounds = getWalkingBounds();
            for (int attempt = 0; attempt < 10; attempt++) {
                BlockPos target = BlockPos.containing(
                        bounds.minX + 1.0D + getRandom().nextDouble() * (bounds.getXsize() - 2.0D),
                        bounds.minY + 0.5D,
                        bounds.minZ + 1.0D + getRandom().nextDouble() * (bounds.getZsize() - 2.0D));
                path = getNavigation().createPath(target, 0);
                if (path != null && path.canReach() && staysInCourtyard(path, bounds)) {
                    return true;
                }
            }
            return false;
        }

        private boolean staysInCourtyard(Path candidate, AABB bounds) {
            for (int i = 0; i < candidate.getNodeCount(); i++) {
                var node = candidate.getNode(i);
                if (!bounds.contains(node.x + 0.5D, node.y, node.z + 0.5D)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && !isInteracting();
        }

        @Override
        public void start() {
            getNavigation().moveTo(path, 0.7D);
        }

        @Override
        public void stop() {
            getNavigation().stop();
            nextWalkTick = ReimuEntity.this.tickCount + 40 + getRandom().nextInt(61);
        }
    }
}
