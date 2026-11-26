package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.dialogue.DialogueGraph;
import com.bitsson.gensokyou.dialogue.DialogueManager;
import com.bitsson.gensokyou.village.VillageDialogue;
import com.bitsson.gensokyou.village.VillageRole;
import com.bitsson.gensokyou.village.VillageTradeMenu;
import com.bitsson.gensokyou.village.VillageShops;
import com.bitsson.gensokyou.worldgen.human_village.BuildingInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
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

/** Four persistent service characters; all mutable home geometry survives the NPC restoration flow. */
public abstract class VillageNpcEntity extends TouhouNpcEntity implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private int residenceId = -1;
    private BlockPos homePivot;
    private Rotation homeRotation = Rotation.NONE;

    protected VillageNpcEntity(EntityType<? extends VillageNpcEntity> type, Level level) { super(type, level); }
    public abstract VillageRole villageRole();
    public static AttributeSupplier.Builder createAttributes() {
        return TouhouNpcEntity.createAttributes().add(Attributes.MOVEMENT_SPEED, .22D);
    }
    public int residenceId() { return residenceId; }
    public void setResidenceId(int id) { residenceId = id; }
    public void bindResidence(BuildingInstance building, int id) {
        boolean first = homePivot == null;
        residenceId = id; homePivot = building.placementPivot(); homeRotation = building.rotation();
        if (first) setAnchorPos(villageRole().anchor(building));
    }
    public AABB walkingBounds() {
        if (homePivot != null) return villageRole().room(homePivot, homeRotation);
        Vec3 p = getAnchorPos() == null ? position() : getAnchorPos();
        return new AABB(p.x - 3, p.y - .05, p.z - 3, p.x + 3, p.y + .65, p.z + 3);
    }
    @Override protected boolean isStationary() { return false; }
    @Override protected void registerGoals() {
        super.registerGoals(); goalSelector.addGoal(3, new IndoorWanderGoal());
    }
    public boolean isInteracting() {
        if (DialogueManager.isTalkingTo(this)) return true;
        var server = getServer();
        return server != null && server.getPlayerList().getPlayers().stream()
                .anyMatch(p -> p.containerMenu instanceof VillageTradeMenu menu && menu.npc() == this);
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && getAnchorPos() != null) {
            if (isInteracting()) { getNavigation().stop(); setDeltaMovement(Vec3.ZERO); }
            if (!walkingBounds().contains(position())) {
                getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
                Vec3 home = getAnchorPos();
                AABB box = getBoundingBox().move(home.subtract(position()));
                if (level().noCollision(this, box)) setAnchorPos(home);
            }
        }
    }
    @Override public DialogueGraph dialogueGraph() { return VillageDialogue.base(villageRole()); }
    @Override public void openTrade(ServerPlayer player) { VillageShops.open(player, this); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("ResidenceId", residenceId);
        if (homePivot != null) { tag.putLong("VillageHome", homePivot.asLong()); tag.putInt("VillageRotation", homeRotation.ordinal()); }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        residenceId = tag.contains("ResidenceId") ? tag.getInt("ResidenceId") : -1;
        if (tag.contains("VillageHome")) homePivot = BlockPos.of(tag.getLong("VillageHome"));
        int r = tag.getInt("VillageRotation"); homeRotation = r >= 0 && r < 4 ? Rotation.values()[r] : Rotation.NONE;
        setNoGravity(false); // Old stationary Akyuu saves contain NoGravity=true.
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String prefix = "animation." + villageRole().key() + ".";
        RawAnimation idle = RawAnimation.begin().thenLoop(prefix + "idle");
        RawAnimation walk = RawAnimation.begin().thenLoop(prefix + "walk");
        RawAnimation occupation = RawAnimation.begin().thenLoop(prefix + villageRole().occupation());
        controllers.add(new AnimationController<>(this, "controller", 5, state -> state.setAndContinue(
                state.isMoving() ? walk : (tickCount / 120 + getId()) % 3 == 0 ? occupation : idle)));
    }
    private final class IndoorWanderGoal extends Goal {
        private Path path;
        private int nextWalkTick;
        private IndoorWanderGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (tickCount < nextWalkTick || isInteracting()) return false;
            nextWalkTick = tickCount + 60 + getRandom().nextInt(101);
            AABB room = walkingBounds();
            for (int attempt = 0; attempt < 12; attempt++) {
                BlockPos target = BlockPos.containing(room.minX + getRandom().nextDouble() * room.getXsize(),
                        room.minY + .05, room.minZ + getRandom().nextDouble() * room.getZsize());
                path = getNavigation().createPath(target, 0);
                if (path != null && path.canReach() && inside(path, room)) return true;
            }
            return false;
        }
        private boolean inside(Path candidate, AABB room) {
            for (int i = 0; i < candidate.getNodeCount(); i++) {
                var node = candidate.getNode(i);
                if (!room.contains(node.x + .5, node.y, node.z + .5)) return false;
            }
            return true;
        }
        @Override public boolean canContinueToUse() {
            return !getNavigation().isDone() && !isInteracting() && inside(path, walkingBounds());
        }
        @Override public void start() { getNavigation().moveTo(path, .7D); }
        @Override public void stop() {
            getNavigation().stop(); nextWalkTick = tickCount + 60 + getRandom().nextInt(101);
        }
    }
}
