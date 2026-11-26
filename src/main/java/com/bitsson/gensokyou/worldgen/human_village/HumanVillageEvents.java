package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Locale;

@EventBusSubscriber(modid="gensokyou")
public final class HumanVillageEvents {
    private static final Map<String,String> DESTINATIONS=Map.of("dragon","hv_dragon_plaza","hieda","hv_hieda_residence",
            "suzunaan","hv_suzunaan","geidontei","hv_geidontei","kourindou","hv_kourindou","east_gate","hv_gate_east");
    private HumanVillageEvents() {}
    @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) return;
        var pos=event.getChunk().getPos();
        if(!HumanVillageManager.intersectsChunk(pos.x,pos.z)) return;
        var server=level.getServer();
        // Always enqueue: ChunkEvent.Load can run before generation locks have been released.
        server.tell(new TickTask(server.getTickCount(),()->HumanVillageManager.get(level).enqueue(pos)));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var level=event.getServer().getLevel(HakureiShrineLandmark.GENSOKYO_DIMENSION);
        var manager=level==null?null:HumanVillageManager.existing(level);
        if(manager!=null) manager.tick();
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && player.serverLevel().dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) HumanVillageManager.get(player.serverLevel()).sync(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && event.getTo().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) HumanVillageManager.get(player.serverLevel()).sync(player);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if(event.getLevel() instanceof ServerLevel level) HumanVillageManager.clear(level); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { HumanVillageManager.clearAll(); }
    @SubscribeEvent public static void preventPlacement(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) return;
        boolean creative=event.getEntity() instanceof ServerPlayer player && player.hasInfiniteMaterials();
        var manager=HumanVillageManager.existing(level);
        if(manager==null) return;
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            if(multi.getReplacedBlockSnapshots().stream().anyMatch(s->manager.isPlacementProtected(s.getPos(),creative))) event.setCanceled(true);
        } else if(manager.isPlacementProtected(event.getPos(),creative)) event.setCanceled(true);
    }
    @SubscribeEvent public static void preventBreaking(BlockEvent.BreakEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION) || event.getPlayer().hasInfiniteMaterials()) return;
        var manager=HumanVillageManager.existing(level);
        if(manager!=null && manager.isProtected(event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_village").requires(source->source.hasPermission(2))
                .then(Commands.literal("visit").executes(ctx->visit(ctx.getSource(),"dragon"))
                        .then(Commands.argument("landmark",StringArgumentType.word()).suggests((ctx,builder)->{
                            DESTINATIONS.keySet().stream().sorted().forEach(builder::suggest);return builder.buildFuture();
                        }).executes(ctx->visit(ctx.getSource(),StringArgumentType.getString(ctx,"landmark")))))
                .then(Commands.literal("stats").executes(ctx->stats(ctx.getSource()))));
    }
    private static int visit(CommandSourceStack source,String name) throws CommandSyntaxException {
        String id=DESTINATIONS.get(name);
        if(id==null) {source.sendFailure(Component.translatable("msg.gensokyou.village.unknown",name));return 0;}
        ServerLevel level=source.getServer().getLevel(HakureiShrineLandmark.GENSOKYO_DIMENSION);
        if(level==null) {source.sendFailure(Component.translatable("msg.gensokyou.village.missing_dimension"));return 0;}
        var manager=HumanVillageManager.get(level);
        var b=manager.plan().buildings().stream().filter(building->building.templateId().getPath().equals(id)).findFirst().orElseThrow();
        int x=b.hasDoorAnchor()?b.doorWorldX():id.equals("hv_dragon_plaza")?-184:-63;
        int z=b.hasDoorAnchor()?b.doorWorldZ():100;
        int y=b.hasDoorAnchor()?b.doorWorldY():manager.plan().layoutVersion()==1?72:71;
        // Explicit visit prepares only the arrival neighborhood. Normal exploration remains lazy.
        for(int cx=(x>>4)-1;cx<=(x>>4)+1;cx++) for(int cz=(z>>4)-1;cz<=(z>>4)+1;cz++) {
            level.getChunk(cx,cz);
            if(!manager.ensureChunkPopulated(new ChunkPos(cx,cz))) {
                source.sendFailure(Component.translatable("msg.gensokyou.village.placement_failed"));return 0;
            }
        }
        ServerPlayer player=source.getPlayerOrException();
        float yaw=(float)Math.toDegrees(-Math.atan2((b.minX()+b.maxX())*0.5-x,(b.minZ()+b.maxZ())*0.5-z));
        player.teleportTo(level,x+0.5,y+1,z+0.5,yaw,0);
        manager.sync(player);
        source.sendSuccess(()->Component.translatable("msg.gensokyou.village.visited",name),false);
        return 1;
    }
    private static int stats(CommandSourceStack source) {
        var level=source.getServer().getLevel(HakureiShrineLandmark.GENSOKYO_DIMENSION);
        if(level==null) {source.sendFailure(Component.translatable("msg.gensokyou.village.missing_dimension"));return 0;}
        var manager=HumanVillageManager.get(level);var plan=manager.plan();
        var homes=plan.buildings().stream().filter(b->b.category()==BuildingInstance.BuildingCategory.RESIDENTIAL).toList();
        var layout=com.bitsson.gensokyou.worldgen.GensokyoLayoutDensity.layoutOf(level.getChunkSource().randomState().sampler().continentalness());
        var field=new VillageSettlementField(layout);
        long core=homes.stream().filter(b->field.outward((b.minX()+b.maxX())/2,(b.minZ()+b.maxZ())/2)<.38).count();
        long middle=homes.stream().filter(b->{double t=field.outward((b.minX()+b.maxX())/2,(b.minZ()+b.maxZ())/2);return t>=.38 && t<.72;}).count(),outer=homes.size()-core-middle;
        var connected=VillageWalkability.reachableDoors(plan);
        long reached=homes.stream().filter(b->connected.contains(HumanVillagePlan.pack(b.doorWorldX(),b.doorWorldZ()))).count();
        String ratio=String.format(Locale.ROOT,"%.1f",100.0*reached/Math.max(1,homes.size()));
        int edge=homes.stream().mapToInt(b->field.edgeDistance((b.minX()+b.maxX())/2,(b.minZ()+b.maxZ())/2)).min().orElse(0);
        long paving=plan.roadCells().stream().filter(c->c.type()!=HumanVillagePlan.RoadType.EARTH).count();
        source.sendSuccess(()->Component.translatable("msg.gensokyou.village.stats",plan.buildings().size(),homes.size(),core,middle,outer,
                paving,ratio,manager.data().placedChunkCount(),manager.data().placedBuildingCount(),edge),false);
        return 1;
    }
}
