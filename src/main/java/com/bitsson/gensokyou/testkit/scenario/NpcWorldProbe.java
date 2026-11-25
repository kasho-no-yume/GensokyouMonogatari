package com.bitsson.gensokyou.testkit.scenario;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.ReimuEntity;
import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in integration check: GameTest's flat world does not include datapack dimensions. */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class NpcWorldProbe {
    private static final Path RESULT = Path.of("npc-probe-result.txt");
    private static ServerLevel level;
    private static ReimuEntity resident;
    private static int ticks;
    private static double maxDistanceSquared;

    private NpcWorldProbe() {}

    @SubscribeEvent
    public static void start(ServerStartedEvent event) throws IOException {
        if (!Boolean.getBoolean("gensokyou.npcProbe")) {
            return;
        }
        Files.writeString(RESULT, "RUNNING\n");
        level = event.getServer().getLevel(HakureiShrineLandmark.GENSOKYO_DIMENSION);
        check(level != null, "Gensokyo dimension must exist in the integration server");
        check(HakureiShrineLandmark.ensurePlaced(level), "Hakurei Shrine must be placed");
        HakureiShrineLandmark.ensurePlaced(level);
        forceCourtyard(true);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) throws IOException {
        if (event.getLevel() != level || level == null) {
            return;
        }
        ticks++;
        if (ticks == 20) {
            var residents = level.getEntities(ModEntityTypes.REIMU.get(), Entity::isAlive);
            check(residents.size() == 1, "Repeated shrine placement must spawn exactly one Reimu");
            resident = residents.getFirst();
            resident.getRandom().setSeed(12345L);
        }
        if (resident == null) {
            return;
        }
        check(resident.getWalkingBounds().contains(resident.position()), "Reimu must stay in the courtyard");
        maxDistanceSquared = Math.max(maxDistanceSquared, resident.position().distanceToSqr(resident.getAnchorPos()));
        if (ticks >= 420) {
            check(maxDistanceSquared > 1.0D, "Reimu must actually walk on the generated courtyard floor");
            String result = "PASS singleton spawn, courtyard walking, bounds; maxDistance="
                    + Math.sqrt(maxDistanceSquared) + "\n";
            Files.writeString(RESULT, result);
            System.out.println("NPC_WORLD_PROBE " + result.strip());
            forceCourtyard(false);
            level.getServer().halt(false);
            level = null;
        }
    }

    private static void forceCourtyard(boolean forced) {
        var spawn = HakureiShrineLandmark.REIMU_SPAWN;
        for (int x = (spawn.getX() - 9) >> 4; x <= (spawn.getX() + 9) >> 4; x++) {
            for (int z = (spawn.getZ() - 10) >> 4; z <= (spawn.getZ() + 10) >> 4; z++) {
                level.setChunkForced(x, z, forced);
            }
        }
    }

    private static void check(boolean valid, String message) throws IOException {
        if (!valid) {
            Files.writeString(RESULT, "FAIL " + message + "\n");
            throw new AssertionError(message);
        }
    }
}
