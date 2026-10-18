package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.ClockAnchors;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Explicit dimension-local preview; members never become Minecraft entities. */
@EventBusSubscriber(modid=Gensokyou.MODID)
public final class ProgramPreviewHost {
    private record Preview(UUID viewer,ArmorStand owner,ProgramSession session,
                            com.bitsson.gensokyou.danmaku.program.author.BossProgramController controller) {}
    private static final Map<ServerLevel,Preview> PREVIEWS=new IdentityHashMap<>();
    private ProgramPreviewHost() {}

    public static boolean start(ServerPlayer player) {
        return start(player,"sphere");
    }
    public static boolean start(ServerPlayer player,String projectile) {
        return start(player,"blue1_slice",projectile);
    }
    public static boolean start(ServerPlayer player,String card,String projectile) {
        ServerLevel level=player.serverLevel();
        if (ProgramBenchmarkHost.active(level)) return false;
        stop(level,"preview-restart");
        var installed=switch (card) {
            case "author_volley", "author_flower", "author_carriers", "author_hidden" -> {
                var template=switch(card) {
                    case "author_volley" -> com.bitsson.gensokyou.danmaku.program.author.AuthoringTemplates.volley();
                    case "author_flower" -> com.bitsson.gensokyou.danmaku.program.author.AuthoringTemplates.flower();
                    case "author_hidden" -> com.bitsson.gensokyou.danmaku.program.author.AuthoringTemplates.hiddenState();
                    default -> com.bitsson.gensokyou.danmaku.program.author.AuthoringTemplates.carriers();
                };
                yield new ProgramPreviewPrograms.Installed(template.definition(),template.execution());
            }
            case "blue1" -> ProgramPreviewPrograms.blue1(projectile);
            case "tail_pearls" -> ProgramPreviewPrograms.tailPearls();
            case "flower_sea" -> ProgramPreviewPrograms.flowerSea();
            case "split_emit" -> ProgramPreviewPrograms.splitGenerations(false);
            case "split_replace" -> ProgramPreviewPrograms.splitGenerations(true);
            case "sphere_split" -> ProgramPreviewPrograms.splitGenerations(true,true);
            case "moving_carriers" -> ProgramMotionPreviewPrograms.movingCarriers();
            case "composite_motion" -> ProgramPreviewPrograms.compositeMotion();
            case "partial_release" -> ProgramPreviewPrograms.compositeMotion(true);
            case "petal_release" -> ProgramPreviewPrograms.compositeMotion(true,true);
            case "motion_lab" -> ProgramMotionPreviewPrograms.motionLab();
            case "moving_tornado" -> ProgramMotionPreviewPrograms.movingTornado();
            case "roaming_tower" -> ProgramMotionPreviewPrograms.movingTornado(true);
            case "rotating_laser" -> ProgramMotionPreviewPrograms.rotatingLaser();
            case "dynamic_warning" -> ProgramMotionPreviewPrograms.rotatingLaser(true);
            case "high_frequency_circle" -> ProgramMotionPreviewPrograms.highFrequencyCircle();
            case "blue6" -> ProgramPreviewPrograms.blue6(projectile);
            case "blue4" -> ProgramPreviewPrograms.blue4(projectile);
            case "blue1_slice" -> ProgramPreviewPrograms.blue1Slice(projectile);
            case "formal_big_fairy" -> formalCard("big_fairy");
            case "formal_yamame" -> formalCard("yamame");
            case "formal_yakumo_ran" -> formalCard("yakumo_ran");
            default -> throw new IllegalArgumentException("unknown program preview");
        };
        // Blue4 places a node ten blocks towards the viewer; 12 blocks violates its six-block birth clearance.
        var anchor=player.getEyePosition().add(player.getLookAngle().scale("blue4".equals(card)?22:12));
        var owner=new ArmorStand(EntityType.ARMOR_STAND,level);
        owner.setInvisible(true); owner.setNoGravity(true); owner.setSilent(true); owner.setInvulnerable(true);
        owner.setPos(anchor.x,anchor.y-1,anchor.z);
        if (!level.addFreshEntity(owner)) return false;
        var controller=BossProgramHost.openEphemeral(level,owner.getUUID(),BossProgramSettings.standard());
        var session=BossProgramHost.session(level);
        var runtime=session.runtime();
        var encounter=controller.encounter();
        long first=level.getGameTime()+1;
        controller.card(new com.bitsson.gensokyou.danmaku.program.author.CardProgram.Compiled(installed.definition(),installed.execution()),first);
        var preview=new Preview(player.getUUID(),owner,session,controller);
        PREVIEWS.put(level,preview);
        Gensokyou.LOGGER.info("[program-preview] START viewer={} owner={} epoch={} definition={}",player.getUUID(),owner.getUUID(),encounter.epoch(),installed.definition().hash());
        return true;
    }
    /** First formal card of a migrated deck, on the same runtime the live battle uses. */
    private static ProgramPreviewPrograms.Installed formalCard(String owner) {
        var deck = com.bitsson.gensokyou.danmaku.program.content.BossProgramCards.forOwner(owner)
                .orElseThrow(() -> new IllegalArgumentException("no formal content for " + owner));
        var compiled = deck.card(1).program();
        return new ProgramPreviewPrograms.Installed(compiled.definition(), compiled.execution());
    }
    public static boolean stop(ServerLevel level,String reason) {        Preview preview=PREVIEWS.remove(level); if (preview==null) return false;
        var serverStats=ProgramServerHost.stats(level);
        var timing=ProgramServerHost.timing(level);
        var networkStats=ProgramNetworkHost.stats(level);
        BossProgramHost.close(level,preview.controller());
        Gensokyou.LOGGER.info("[program-preview-timing] reason={} recentHost={}",reason,timing);
        preview.owner().discard();
        Gensokyou.LOGGER.info("[program-preview] STOP reason={} sharedRuntime={} server={} networkBeforeEnd={}",reason,
                preview.session().runtime().stats(),serverStats,networkStats);
        return true;
    }
    public static String stats(ServerLevel level) {
        Preview p=PREVIEWS.get(level); if (p==null) return "program-preview: idle";
        return "program-preview: tick="+p.session().runtime().tick()+" sharedRuntime="+p.session().runtime().stats()
                 +" server="+ProgramServerHost.stats(level)
                 +" recentHost="+ProgramServerHost.timing(level)+" network="+ProgramNetworkHost.stats(level);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Preview p=PREVIEWS.get(level); if (p==null) return;
        var viewer=level.getServer().getPlayerList().getPlayer(p.viewer());
        if (viewer==null || !viewer.isAlive() || viewer.serverLevel()!=level || !p.owner().isAlive()) stop(level,"preview-owner-or-viewer-unavailable");
        else if (p.session().checkpoint().instances().stream().noneMatch(i->i.encounter().equals(p.controller().encounter()))) stop(level,"preview-complete");
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) stop(level,"dimension-unload");
    }
}
