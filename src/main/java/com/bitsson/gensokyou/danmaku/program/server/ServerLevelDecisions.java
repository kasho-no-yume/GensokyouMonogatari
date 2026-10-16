package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.Actions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Explicitly constructed adapter; does not enable the new backend for any BOSS. */
public final class ServerLevelDecisions implements DecisionProvider {
    private final ServerLevel level;
    public ServerLevelDecisions(ServerLevel level) { this.level=Objects.requireNonNull(level); }
    private static Vec3 vector(net.minecraft.world.phys.Vec3 p) { return new Vec3(p.x,p.y,p.z); }
    @Override public List<Player> players() {
        return level.players().stream().map(p->new Player(p.getUUID(),vector(p.position()),vector(p.getEyePosition()),
                p.getBbHeight(),p.isAlive(),p.isCreative(),p.isSpectator(),p.getBbWidth()/2.0)).toList();
    }
    @Override public Optional<Actions.Pose> entity(UUID uuid) {
        var entity=level.getEntity(uuid);
        if (entity==null || !entity.isAlive() || !loaded(vector(entity.position()))) return Optional.empty();
        return Optional.of(new Actions.Pose(Frame.along(vector(entity.position()),vector(entity.getLookAngle()),Vec3.Y),vector(entity.getDeltaMovement())));
    }
    @Override public boolean loaded(Vec3 point) {
        int y=(int)StrictMath.floor(point.y());
        return y>=level.getMinBuildHeight() && y<level.getMaxBuildHeight()
                && level.hasChunkAt(new BlockPos((int)StrictMath.floor(point.x()),y,(int)StrictMath.floor(point.z())));
    }
    @Override public Block block(int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);
        if (!loaded(new Vec3(x,y,z))) return new Block(false,false,false,0);
        var state=level.getBlockState(pos);
        var shape=state.getCollisionShape(level,pos);
        return new Block(true,state.isAir(),!shape.isEmpty(),shape.isEmpty()?0:shape.max(Direction.Axis.Y));
    }
}
