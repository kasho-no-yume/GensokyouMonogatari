package com.bitsson.gensokyou.testkit.scenario;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.NpcQuestMarker;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Gensokyou.MODID)
@PrefixGameTestTemplate(false)
public final class NpcGameTests {
    private static final String TEMPLATE = "test/spirit_treatment";

    private NpcGameTests() {}

    @GameTest(template = TEMPLATE)
    public static void stationaryNpcAndQuestMarkerPersistence(GameTestHelper test) {
        var npc = test.spawn(ModEntityTypes.RINNOSUKE.get(), new BlockPos(3, 1, 3));
        Vec3 anchor = npc.position();
        npc.setAnchorPos(anchor);
        npc.setPos(anchor.x + 2, anchor.y + 1, anchor.z + 2);
        npc.moveTo(anchor.x - 2, anchor.y + 1, anchor.z - 2, 90, 0);
        npc.setDeltaMovement(new Vec3(1, 1, 1));
        test.assertValueEqual(npc.position(), anchor, "ordinary NPC remains anchored");
        test.assertValueEqual(npc.getDeltaMovement(), Vec3.ZERO, "ordinary NPC rejects motion");
        test.assertTrue(npc.isNoGravity(), "ordinary NPC has no gravity");
        test.assertValueEqual(npc.getQuestMarker(), NpcQuestMarker.NONE, "markers are hidden by default");

        var replica = ModEntityTypes.RINNOSUKE.get().create(test.getLevel());
        for (NpcQuestMarker marker : new NpcQuestMarker[] {
                NpcQuestMarker.EXCLAMATION, NpcQuestMarker.QUESTION, NpcQuestMarker.NONE}) {
            npc.setQuestMarker(marker);
            var updates = npc.getEntityData().packDirty();
            if (updates != null) {
                replica.getEntityData().assignValues(updates);
            }
            test.assertValueEqual(replica.getQuestMarker(), marker, "marker is included in entity data updates");
            CompoundTag saved = npc.saveWithoutId(new CompoundTag());
            replica.load(saved);
            test.assertValueEqual(replica.getQuestMarker(), marker, "marker survives NBT reload");
            test.assertValueEqual(replica.getAnchorPos(), anchor, "anchor survives NBT reload");
        }
        CompoundTag legacy = npc.saveWithoutId(new CompoundTag());
        legacy.remove("QuestMarker");
        replica.load(legacy);
        test.assertValueEqual(replica.getQuestMarker(), NpcQuestMarker.NONE, "legacy NPC has no marker");
        legacy.putString("QuestMarker", "unknown_future_marker");
        replica.load(legacy);
        test.assertValueEqual(replica.getQuestMarker(), NpcQuestMarker.NONE, "unknown marker is hidden");
        test.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void walkingNpcProtectionAndRestore(GameTestHelper test) {
        var npc = test.spawn(ModEntityTypes.REIMU.get(), new BlockPos(3, 1, 3));
        npc.setNoAi(true);
        Vec3 anchor = npc.position();
        npc.setAnchorPos(anchor);
        Vec3 walked = anchor.add(1, 0, 0);
        npc.setPos(walked.x, walked.y, walked.z);
        npc.setDeltaMovement(new Vec3(0.1, 0, 0));
        test.assertValueEqual(npc.position(), walked, "walking NPC may leave its anchor");
        test.assertTrue(npc.getDeltaMovement().x > 0, "walking NPC accepts AI motion");
        test.assertFalse(npc.isNoGravity(), "walking NPC stands on the ground");
        test.assertFalse(npc.hurt(test.getLevel().damageSources().generic(), 5), "NPC protection remains active");
        npc.setDeltaMovement(Vec3.ZERO);
        npc.setQuestMarker(NpcQuestMarker.QUESTION);
        var previousId = npc.getUUID();
        npc.die(test.getLevel().damageSources().generic());

        test.runAfterDelay(2, () -> {
            var copies = test.getEntities(ModEntityTypes.REIMU.get(), new BlockPos(3, 1, 3), 4);
            test.assertValueEqual(copies.size(), 1, "restoration creates one living copy");
            var copy = copies.getFirst();
            test.assertFalse(copy.getUUID().equals(previousId), "restored NPC gets a new UUID");
            test.assertValueEqual(copy.getAnchorPos(), anchor, "restoration preserves walking anchor");
            test.assertValueEqual(copy.getQuestMarker(), NpcQuestMarker.QUESTION, "restoration preserves marker");
            copy.setPos(anchor.x + 100, anchor.y, anchor.z);
            copy.tick();
            test.assertValueEqual(copy.position(), anchor, "out-of-bounds NPC returns to courtyard anchor");
            copy.discard();
            test.succeed();
        });
    }

}
