package com.bitsson.gensokyou.quest;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.bitsson.gensokyou.quest.QuestProgressTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestDefinitionTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.ensureStarted(); }
    @Test void unknownTypesDoNotPreventLaterDefinitionsFromDecoding() {
        String json = """
                {"id":"gensokyou:decode_test","kind":"commission","scope":"player",
                "title":"title","description":"description","provider":"gensokyou:akyuu","return_npc":"gensokyou:akyuu",
                "stages":[{"id":"one","title":"stage","objectives":[
                {"type":"gensokyou:unknown","item":"minecraft:paper","count":4,"description":"goal"}]}]}
                """;
        assertThrows(RuntimeException.class, () -> QuestDefinitionLoader.decode(id("decode_test"), JsonParser.parseString(json)));
        var def = QuestDefinitionLoader.decode(id("decode_test"), JsonParser.parseString(json.replace("gensokyou:unknown", "gensokyou:have_items")));
        assertEquals(1, def.stages().size());
        assertThrows(RuntimeException.class, () -> QuestDefinitionLoader.decode(id("wrong_file"), JsonParser.parseString(json)));
    }
    @Test void commissionsCannotHaveStagesAndMainlineCannotHavePrivateRewards() {
        var stages = List.of(stage("one", new QuestObjectives.HaveItems(PAPER, 1, "goal")),
                stage("two", new QuestObjectives.HaveItems(PAPER, 1, "goal")));
        assertThrows(IllegalArgumentException.class, () -> new QuestDefinition(id("bad_commission"), QuestDefinition.Kind.COMMISSION,
                QuestDefinition.Scope.PLAYER, "title", "description", NPC, NPC, stages, List.of(), -1, true));
        assertThrows(IllegalArgumentException.class, () -> new QuestDefinition(id("bad_scope"), QuestDefinition.Kind.SIDE,
                QuestDefinition.Scope.WORLD, "title", "description", NPC, NPC, stages, List.of(), -1, false));
        assertThrows(IllegalArgumentException.class, () -> new QuestDefinition(id("bad_rewards"), QuestDefinition.Kind.MAIN,
                QuestDefinition.Scope.WORLD, "title", "description", NPC, NPC, stages,
                List.of(new QuestDefinition.ItemAmount(PAPER, 1)), 0, false));
        assertThrows(IllegalArgumentException.class, () -> new QuestDefinition.Stage("empty", "stage", List.of(), Optional.empty()));
    }
    private record Extension(String description) implements QuestObjectives.Objective {
        public ResourceLocation type() { return id("extension_test"); }
        public int required() { return 1; }
        static final MapCodec<Extension> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("description").forGetter(Extension::description)).apply(i, Extension::new));
    }
    @Test void aNewHandlerUsesTheSameLifecycleAndJournalCodec() {
        QuestObjectives.register(id("extension_test"), new QuestObjectives.Handler<Extension>() {
            public MapCodec<Extension> codec() { return Extension.CODEC; }
            public int update(Extension goal, int progress, QuestObjectives.Event event, QuestObjectives.Context context) {
                return event instanceof QuestObjectives.Poll ? 1 : progress;
            }
        });
        var def = definition("extension", List.of(stage("one", new Extension("extension"))), List.of());
        assertTrue(QuestProgress.start(def).apply(def, new QuestObjectives.Poll(), item -> 0).ready());
        var encoded = QuestDefinition.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, def).getOrThrow();
        assertEquals(def, QuestDefinition.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
