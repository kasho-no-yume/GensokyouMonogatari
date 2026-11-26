package com.bitsson.gensokyou.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A private view: only accepted personal tasks, the current world mainline, and this observer's markers. */
public record QuestSnapshot(List<Entry> entries, Optional<Destination> navigation, Map<ResourceLocation, String> markers) {
    public static final QuestSnapshot EMPTY = new QuestSnapshot(List.of(), Optional.empty(), Map.of());
    public static final Codec<QuestSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(QuestSnapshot::entries),
            Destination.CODEC.optionalFieldOf("navigation").forGetter(QuestSnapshot::navigation),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING).fieldOf("markers").forGetter(QuestSnapshot::markers)
    ).apply(i, QuestSnapshot::new));
    public QuestSnapshot { entries = List.copyOf(entries); markers = Map.copyOf(markers); }
    public record Entry(QuestDefinition definition, QuestProgress progress, boolean tracked) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                QuestDefinition.CODEC.fieldOf("definition").forGetter(Entry::definition),
                QuestProgress.CODEC.fieldOf("progress").forGetter(Entry::progress),
                Codec.BOOL.fieldOf("tracked").forGetter(Entry::tracked)
        ).apply(i, Entry::new));
    }
    public record Destination(ResourceLocation dimension, double x, double y, double z, String label) {
        public static final Codec<Destination> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(Destination::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(Destination::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Destination::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Destination::z),
                Codec.STRING.fieldOf("label").forGetter(Destination::label)
        ).apply(i, Destination::new));
    }
}
