package com.bitsson.gensokyou.danmaku.program.preview;

import com.bitsson.gensokyou.danmaku.program.ClockAnchors;
import com.bitsson.gensokyou.danmaku.program.ProgramLimits;
import com.bitsson.gensokyou.danmaku.program.author.BossProgramController;
import com.bitsson.gensokyou.danmaku.program.author.StandardProgramOperations;
import com.bitsson.gensokyou.danmaku.program.content.BossProgramCards;
import com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramSettings;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramMotionBounds;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramVisuals;
import com.bitsson.gensokyou.danmaku.program.math.Frame;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.network.NetworkLimits;
import com.bitsson.gensokyou.danmaku.program.network.ProgramReplica;
import com.bitsson.gensokyou.danmaku.program.network.ProgramRenderView;
import com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer;
import com.bitsson.gensokyou.danmaku.program.network.ProgramWire;
import com.bitsson.gensokyou.danmaku.program.network.ReplicaState;
import com.bitsson.gensokyou.danmaku.program.ops.Actions;
import com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile;
import com.bitsson.gensokyou.danmaku.program.ops.Pattern;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import com.bitsson.gensokyou.danmaku.program.server.DecisionProvider;
import com.bitsson.gensokyou.danmaku.program.server.ProgramSession;
import com.bitsson.gensokyou.danmaku.visual.DanmakuGeometry;
import com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile;
import com.bitsson.gensokyou.client.renderer.DanmakuBulletTextures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Offline projection export for the preview site.
 *
 * <p><b>What this is</b>: the real authority runtime, the real wire and the real client replica,
 * with only {@link DecisionProvider} replaced by a frozen scene. Every recorded number comes out of
 * {@link ProgramReplica#renderView}, which is the exact projection the client renders; nothing here
 * re-evaluates motion, visibility or damage.
 *
 * <p><b>Scene constants (design D10)</b>: changing any of them invalidates every committed baseline
 * file, so they live here and nowhere else. Both the boss anchor and the stub player are static —
 * that is what makes the export a pure function of (code, card, seed).
 *
 * <p>Dev-only: lives in the test source set and never enters the mod jar.
 */
public final class PreviewExport {
    // ---- frozen scene (design D10) ------------------------------------------------------------
    static final String DIMENSION = "preview:arena";
    /** Boss eye anchor: every group frame is built from this pose. */
    static final Vec3 BOSS_EYE = new Vec3(0, 80, 0);
    /** Stub player feet. Aimed groups converge here; there is no player physics in the preview. */
    static final Vec3 STUB_FEET = new Vec3(0, 64, 10);
    static final double STUB_HEIGHT = 1.8;
    static final double STUB_EYE_HEIGHT = 1.62;
    /** Block layer y<=63 is solid with its top face at y=64; everything above is air. */
    static final double GROUND_TOP = 64.0;
    static final UUID OWNER = new UUID(0x5EED0000L, 0x1L);
    static final UUID STUB = new UUID(0x570B0000L, 0x2L);
    /** Sample every N ticks. Born ticks are always sampled regardless of stride. */
    static int stride = 2;
    static final int MAX_TICKS = 6000;
    /** Polyline simplification tolerance (design D8). Reconstruction stays inside these bounds. */
    static final double EPS_POSITION = 0.05;
    static final double EPS_ALPHA = 0.02;
    static final double EPS_BRIGHTNESS = 0.02;
    static final double EPS_SIZE = 0.02;
    static final double EPS_BEAM = 0.05;
    static final double EPS_DIRECTION = 0.02;
    static final long MAX_SPAN_TICKS = 20;

    private PreviewExport() {}

    // ---- entry point -------------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        Path out = Path.of("preview/data");
        boolean check = false;
        boolean stub = true;
        String only = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--check" -> check = true;
                case "--no-stub" -> stub = false;
                case "--out" -> out = Path.of(args[++i]);
                case "--only" -> only = args[++i];
                case "--stride" -> stride = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (stride < 1) throw new IllegalArgumentException("stride must be >= 1");
        int written = 0, unchanged = 0;
        List<String> problems = new ArrayList<>();
        List<Summary> summaries = new ArrayList<>();
        for (BossProgramCards.Deck deck : BossProgramCards.all()) {
            for (BossProgramCards.Card card : deck.cards()) {
                if (only != null && !only.equals(deck.ownerId() + ":" + card.index())) continue;
                long started = System.nanoTime();
                Baked baked = bake(deck, card, stub);
                long elapsed = System.nanoTime() - started;
                Path file = out.resolve(deck.ownerId() + "_" + card.index() + ".json");
                String text = baked.json();
                boolean identical = Files.isRegularFile(file) && text.equals(
                        Files.readString(file, StandardCharsets.UTF_8));
                if (check) {
                    if (!identical) problems.add(file.getFileName() + " differs" + (Files.isRegularFile(file) ? "" : " (missing)"));
                } else if (!identical) {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, text, StandardCharsets.UTF_8);
                }
                if (identical) unchanged++; else written++;
                summaries.add(new Summary(deck.ownerId(), card.index(), card.nameKey(), baked.ticks(),
                        baked.members(), baked.samples(), text.length(), elapsed));
            }
        }
        if (only == null) {
            // Index is regenerated from the same run so it can never drift from the files.
            Path index = out.resolve("index.json");
            String text = index(summaries);
            boolean identical = Files.isRegularFile(index) && text.equals(Files.readString(index, StandardCharsets.UTF_8));
            if (check) {
                if (!identical) problems.add("index.json differs");
            } else if (!identical) {
                Files.createDirectories(out);
                Files.writeString(index, text, StandardCharsets.UTF_8);
            }
            if (identical) unchanged++; else written++;
            problems.addAll(unexpectedFiles(out, summaries));
        }
        print(summaries);
        if (!problems.isEmpty()) {
            System.out.println("\nPREVIEW BASELINE STALE:");
            for (String problem : problems) System.out.println("  - " + problem);
            System.out.println("Run `tools\\gradle_task.ps1 exportDanmakuPreview` and commit the result.");
            System.exit(1);
        }
        System.out.println(check ? "\ncheck: baseline matches (" + unchanged + " files)"
                : "\nwrote " + written + " file(s), " + unchanged + " unchanged");
    }

    /** Coverage is decided by the registry: a baseline file with no registered card is an error. */
    private static List<String> unexpectedFiles(Path out, List<Summary> summaries) {
        Set<String> expected = new HashSet<>();
        for (Summary summary : summaries) expected.add(summary.owner + "_" + summary.index + ".json");
        expected.add("index.json");
        List<String> extra = new ArrayList<>();
        if (!Files.isDirectory(out)) return extra;
        try (var stream = Files.list(out)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !expected.contains(name))
                    .sorted()
                    .forEach(name -> extra.add(name + " has no registered card"));
        } catch (IOException failure) {
            extra.add("cannot list " + out + ": " + failure.getMessage());
        }
        return extra;
    }

    private record Summary(String owner, int index, String nameKey, long ticks, int members,
                           long samples, int bytes, long nanos) {}

    private record Baked(String json, long ticks, int members, long samples) {}

    // ---- one card ----------------------------------------------------------------------------
    static Baked bake(BossProgramCards.Deck deck, BossProgramCards.Card card, boolean stub) {
        BossProgramHostShim.requireStub(stub);
        var settings = BossProgramSettings.standard();
        var world = new PreviewWorld(stub);
        var runtime = new DanmakuWorldRuntime(DIMENSION, settings.runtime(), world);
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        runtime.advance(0);
        controller.card(card.program(), 1);

        var wire = new ProgramWire(settings.network());
        var pending = new ArrayList<byte[]>();
        var server = new ProgramSyncServer(wire, pending::add, settings.clockPeriod());
        var replica = new ProgramReplica(new ProgramWire(settings.network()),
                StandardProgramOperations.registry(), StandardProgramOperations.limits());
        var scope = new ProgramReplica.Scope(DIMENSION, OWNER);

        var accumulator = new Accumulator();
        // Two full cycles, then one full member lifetime past the last planned spawn: a looping
        // card never empties, so "run until no members" would run forever (or to the cap).
        long deadline = 2L * card.cycleTicks();
        long tick = 0;
        while (true) {
            tick++;
            session.advance(tick, settings.maxEvents());
            accumulator.acceptEvents(runtime.drainEvents());
            var projection = ReplicaState.capture(session.checkpoint(), wire);
            // Margin 1e6: the preview is not a viewer with a view distance; interest must not clip.
            server.publish(projection, ProgramMotionBounds.groups(projection), STUB_FEET.add(Vec3.Y.multiply(STUB_EYE_HEIGHT)), 1.0E6);
            for (byte[] bytes : pending) replica.receive(bytes, (int) tick);
            pending.clear();
            var view = replica.renderView(scope, tick);
            if (view.isPresent()) accumulator.acceptTick(tick, view.get());
            if (tick >= deadline + accumulator.maxLife() + 2) break;
            if (tick >= MAX_TICKS) break;
        }
        if (controller.active()) controller.close();
        return accumulator.finish(deck, card, tick, replica.failures());
    }

    /** A synthetic stub player is required and must be present: without it aimed groups never fire. */
    private static final class BossProgramHostShim {
        static void requireStub(boolean stub) {
            if (!stub) throw new IllegalStateException(
                    "preview requires a stub player: without DecisionProvider.Player targets() samples nobody "
                            + "and aim groups never fire, which would export a card that does not exist");
            if (STUB.equals(OWNER)) throw new IllegalStateException("stub and owner must differ");
        }
    }

    // ---- frozen world ------------------------------------------------------------------------
    private static final class PreviewWorld implements DecisionProvider {
        private final List<Player> players;
        PreviewWorld(boolean stub) {
            this.players = stub
                    ? List.of(new Player(STUB, STUB_FEET, STUB_FEET.add(Vec3.Y.multiply(STUB_EYE_HEIGHT)),
                            STUB_HEIGHT, true, false, false, 0.3))
                    : List.of();
        }
        @Override public List<Player> players() { return players; }
        @Override public Optional<Actions.Pose> entity(UUID uuid) {
            if (!uuid.equals(OWNER)) return Optional.empty();
            return Optional.of(new Actions.Pose(
                    new Frame(BOSS_EYE, Vec3.X, Vec3.Y, Vec3.Z), Vec3.ZERO));
        }
        @Override public boolean loaded(Vec3 point) { return true; }
        @Override public Block block(int x, int y, int z) {
            return y <= 63 ? new Block(true, false, true, 1.0) : new Block(true, true, false, 0.0);
        }
    }

    // ---- accumulation ------------------------------------------------------------------------
    private record MemberKey(long group, long batch, int index) implements Comparable<MemberKey> {
        @Override public int compareTo(MemberKey other) {
            int byGroup = Long.compare(group, other.group);
            if (byGroup != 0) return byGroup;
            int byBatch = Long.compare(batch, other.batch);
            return byBatch != 0 ? byBatch : Integer.compare(index, other.index);
        }
    }

    private static final class Track {
        final MemberKey key;
        long born = Long.MIN_VALUE;
        long life;
        int style;
        boolean laser;
        final List<float[]> samples = new ArrayList<>();
        Track(MemberKey key) { this.key = key; }
    }

    private static final class Style {
        final String mesh;
        final String tint;
        final double radius;
        final double scale;
        final String texture;
        final double tumbleAmp;
        final int tumblePeriod;
        final double[] outline;
        Style(Pattern.Style style) {
            DanmakuVisualProfile.Profile profile = ProgramVisuals.profile(style);
            this.mesh = profile.geometry() == DanmakuGeometry.STAR_PRISM ? "star" : "quad";
            this.tint = String.format(Locale.ROOT, "#%06X", ProgramVisuals.tint(style) & 0xFFFFFF);
            this.radius = style.visualRadius();
            this.scale = profile.visualScale();
            this.texture = textureFor(profile);
            this.tumbleAmp = profile.tumbleAmpDeg();
            this.tumblePeriod = profile.tumblePeriod();
            this.outline = mesh.equals("star") ? DanmakuGeometry.starPrismOutline(1.0) : new double[0];
        }
        String key() { return mesh + "|" + tint + "|" + radius + "|" + scale + "|" + texture + "|" + tumbleAmp + "|" + tumblePeriod; }
    }

    /** Renderer-side texture resolution, mirrored from {@code ProgramDanmakuRenderer}. */
    private static String textureFor(DanmakuVisualProfile.Profile profile) {
        // The program renderer draws the DEFAULT profile with the shared bullet variant
        // (DanmakuBulletTextures.currentLocation()), not profile.texture(). A fresh export JVM
        // never switches the variant, so this is deterministic.
        if (profile == DanmakuVisualProfile.DEFAULT) return DanmakuBulletTextures.currentLocation().toString();
        return profile.texture().toString();
    }

    // Laser assets are private constants on the client renderer; the exporter may not touch
    // production code (spec: 本变更 MUST NOT 改动生产玩法代码), so they are repeated here verbatim.
    private static final String LASER_TEXTURE = "gensokyou:textures/entity/laser_danmaku.png";
    private static final String LASER_CAP_TEXTURE = "gensokyou:textures/entity/laser_cap.png";
    private static final String LASER_CIRCLE_TEXTURE = "gensokyou:textures/entity/laser_magic_circle.png";

    private static final class Accumulator {
        private final Map<MemberKey, Track> tracks = new TreeMap<>();
        private final Map<String, Integer> styleIndex = new LinkedHashMap<>();
        private final List<Style> styles = new ArrayList<>();
        private final Map<String, Warning> warnings = new LinkedHashMap<>();
        private final Map<String, Billboard> billboards = new LinkedHashMap<>();
        private final Map<String, Guide> guides = new LinkedHashMap<>();
        private final List<Event> events = new ArrayList<>();
        private long samples;
        private long lastTick;
        private long maxLife;

        long maxLife() { return maxLife; }

        // The projection is assembled from hash maps; their iteration order may differ across JVMs.
        // Everything emitted in a list MUST be sorted by content, or the baseline is not reproducible.
        List<Warning> sortedWarnings() {
            List<Warning> out = new ArrayList<>(warnings.values());
            out.sort(Comparator.comparingLong(Warning::since)
                    .thenComparingLong(Warning::group)
                    .thenComparingLong(Warning::batch)
                    .thenComparingLong(Warning::until));
            return out;
        }

        List<Billboard> sortedBillboards() {
            List<Billboard> out = new ArrayList<>(billboards.values());
            out.sort(Comparator.comparingLong(Billboard::tick)
                    .thenComparing(Billboard::texture)
                    .thenComparingDouble(b -> b.position().x())
                    .thenComparingDouble(b -> b.position().y())
                    .thenComparingDouble(b -> b.position().z())
                    .thenComparingDouble(Billboard::age));
            return out;
        }

        List<Guide> sortedGuides() {
            List<Guide> out = new ArrayList<>(guides.values());
            out.sort(Comparator.comparingLong(Guide::tick).thenComparing(guide -> fixedKey(guide.points())));
            return out;
        }

        void acceptTick(long tick, ProgramRenderView.Snapshot snapshot) {
            lastTick = tick;
            for (ProgramRenderView.Member member : snapshot.members()) {
                if (!member.visible()) continue;
                var key = new MemberKey(member.id().group(), member.id().batch(), member.id().index());
                var track = tracks.computeIfAbsent(key, Track::new);
                int style = style(member.style());
                if (track.born == Long.MIN_VALUE) {
                    track.born = Math.round(tick - member.age());
                    track.life = Math.round(member.lifetime());
                    track.style = style;
                    track.laser = member.collisionProfile() instanceof CollisionProfile.Laser;
                    maxLife = Math.max(maxLife, track.life);
                }
                if ((tick - track.born) % stride != 0) continue;
                float[] sample = track.laser ? new float[11] : new float[7];
                sample[0] = tick;
                sample[1] = (float) member.position().x();
                sample[2] = (float) member.position().y();
                sample[3] = (float) member.position().z();
                sample[4] = (float) member.alpha();
                sample[5] = (float) member.brightness();
                sample[6] = (float) member.sizeMultiplier();
                if (track.laser) {
                    sample[7] = (float) member.beamLength();
                    sample[8] = (float) member.direction().x();
                    sample[9] = (float) member.direction().y();
                    sample[10] = (float) member.direction().z();
                }
                track.samples.add(sample);
            }
            for (ProgramRenderView.Warning warning : snapshot.warnings()) {
                int style = style(warning.style());
                boolean laser = warning.collisionProfile() instanceof CollisionProfile.Laser;
                double beam = laser ? ((CollisionProfile.Laser) warning.collisionProfile()).maxLength() : 0;
                var entry = new Warning(warning.group(), warning.batch(), warning.source(), warning.direction(),
                        tick, warning.birthTick(), style, laser, beam);
                warnings.putIfAbsent(entry.key(), entry);
            }
            for (ProgramRenderView.Billboard billboard : snapshot.billboards()) {
                var entry = new Billboard(tick, billboard.position(),
                        billboard.presentation().texture(),
                        billboard.presentation().size(),
                        billboard.presentation().tint(),
                        billboard.presentation().visualAngleRadians().at(billboard.age()),
                        billboard.age());
                billboards.putIfAbsent(entry.key(), entry);
            }
            for (ProgramRenderView.Guide guide : snapshot.guides()) {
                double[][] points = new double[guide.points().size()][];
                for (int i = 0; i < points.length; i++) {
                    Vec3 point = guide.points().get(i);
                    points[i] = new double[]{point.x(), point.y(), point.z()};
                }
                guides.put(tick + ":" + fixedKey(points), new Guide(tick, points));
            }
        }

        void acceptEvents(List<DanmakuWorldRuntime.Event> drained) {
            for (DanmakuWorldRuntime.Event event : drained) {
                // The timeline only needs refusals; commits/births are already in the member list.
                if (event.kind() != DanmakuWorldRuntime.Kind.REJECT) continue;
                events.add(new Event(event.tick(), "REJECT", event.reason() == null ? "" : event.reason(), event.group()));
            }
        }

        private int style(Pattern.Style style) {
            Style entry = new Style(style);
            Integer existing = styleIndex.get(entry.key());
            if (existing != null) return existing;
            int next = styles.size();
            styles.add(entry);
            styleIndex.put(entry.key(), next);
            return next;
        }

        Baked finish(BossProgramCards.Deck deck, BossProgramCards.Card card, long ticks, long replicaFailures) {
            if (replicaFailures != 0) throw new IllegalStateException(
                    "replica reported " + replicaFailures + " failure(s) while exporting " + card.nameKey());
            StringBuilder sb = new StringBuilder(1 << 20);
            sb.append("{\n");
            sb.append("  \"schema\": 1,\n");
            sb.append("  \"owner\": \"").append(deck.ownerId()).append("\",\n");
            sb.append("  \"index\": ").append(card.index()).append(",\n");
            sb.append("  \"card\": \"").append(card.nameKey()).append("\",\n");
            sb.append("  \"cycleTicks\": ").append(card.cycleTicks()).append(",\n");
            sb.append("  \"ticks\": ").append(ticks).append(",\n");
            sb.append("  \"stride\": ").append(stride).append(",\n");
            sb.append("  \"scene\": {");
            sb.append("\"dimension\": \"").append(DIMENSION).append("\", ");
            sb.append("\"boss\": ").append(vec(BOSS_EYE)).append(", ");
            sb.append("\"stub\": ").append(vec(STUB_FEET)).append(", ");
            sb.append("\"stubEyes\": ").append(vec(STUB_FEET.add(Vec3.Y.multiply(STUB_EYE_HEIGHT)))).append(", ");
            sb.append("\"groundTop\": "); fixed(sb, GROUND_TOP); sb.append(", ");
            sb.append("\"fov\": 70, \"ticksPerSecond\": 20}").append(",\n");
            sb.append("  \"renderer\": {");
            sb.append("\"bullet\": \"").append(DanmakuBulletTextures.currentLocation()).append("\", ");
            sb.append("\"warning\": \"").append(DanmakuBulletTextures.Variant.LOD.location()).append("\", ");
            sb.append("\"laser\": \"").append(LASER_TEXTURE).append("\", ");
            sb.append("\"cap\": \"").append(LASER_CAP_TEXTURE).append("\", ");
            sb.append("\"circle\": \"").append(LASER_CIRCLE_TEXTURE).append("\"},\n");
            sb.append("  \"styles\": [\n");
            // Deterministic style order: first appearance follows the projection's map iteration order,
            // which is not stable across JVMs. Sort by content key and remap member references.
            int[] remap = new int[styles.size()];
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < styles.size(); i++) order.add(i);
            order.sort(Comparator.comparing(i -> styles.get(i).key()));
            for (int newIndex = 0; newIndex < order.size(); newIndex++) remap[order.get(newIndex)] = newIndex;
            for (int i = 0; i < order.size(); i++) {
                Style style = styles.get(order.get(i));
                sb.append("    {\"mesh\": \"").append(style.mesh).append("\", ");
                sb.append("\"tint\": \"").append(style.tint).append("\", ");
                sb.append("\"radius\": "); fixed(sb, style.radius); sb.append(", ");
                sb.append("\"scale\": "); fixed(sb, style.scale); sb.append(", ");
                sb.append("\"texture\": \"").append(style.texture).append("\", ");
                sb.append("\"tumbleAmp\": "); fixed(sb, style.tumbleAmp); sb.append(", ");
                sb.append("\"tumblePeriod\": ").append(style.tumblePeriod);
                if (style.outline.length > 0) {
                    sb.append(", \"outline\": [");
                    for (int p = 0; p < style.outline.length; p += 2) {
                        if (p > 0) sb.append(',');
                        sb.append('['); fixed(sb, style.outline[p]); sb.append(','); fixed(sb, style.outline[p + 1]); sb.append(']');
                    }
                    sb.append("], \"half\": "); fixed(sb, 0.25);
                }
                sb.append('}');
                if (i + 1 < order.size()) sb.append(',');
                sb.append('\n');
            }
            sb.append("  ],\n");
            sb.append("  \"members\": [\n");
            int written = 0;
            for (Track track : tracks.values()) {
                if (track.samples.isEmpty()) continue;
                List<float[]> path = simplify(track.samples);
                verify(track.samples, path);
                samples += path.size();
                if (written++ > 0) sb.append(",\n");
                sb.append("    {\"g\": ").append(track.key.group()).append(", ");
                sb.append("\"b\": ").append(track.key.batch()).append(", ");
                sb.append("\"i\": ").append(track.key.index()).append(", ");
                sb.append("\"born\": ").append(track.born).append(", ");
                sb.append("\"life\": ").append(track.life).append(", ");
                sb.append("\"style\": ").append(remap[track.style]).append(", ");
                sb.append("\"laser\": ").append(track.laser);
                sb.append(", \"s\": [");
                for (int i = 0; i < path.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append('[');
                    float[] sample = path.get(i);
                    for (int n = 0; n < sample.length; n++) {
                        if (n > 0) sb.append(',');
                        if (n == 0) sb.append((long) sample[0]); else fixed(sb, sample[n]);
                    }
                    sb.append(']');
                }
                sb.append("]}");
            }
            sb.append("\n  ],\n");
            sb.append("  \"warnings\": [");
            boolean first = true;
            for (Warning warning : sortedWarnings()) {
                if (!first) sb.append(',');
                first = false;
                sb.append("\n    {\"g\": ").append(warning.group).append(", \"b\": ").append(warning.batch);
                sb.append(", \"src\": ").append(vec(warning.from)).append(", \"dir\": ").append(vec(warning.dir));
                sb.append(", \"from\": ").append(warning.since);
                sb.append(", \"until\": ").append(warning.until).append(", \"style\": ").append(warning.style);
                sb.append(", \"laser\": ").append(warning.laser);
                if (warning.laser) { sb.append(", \"beam\": "); fixed(sb, warning.beam); }
                sb.append('}');
            }
            sb.append(first ? "],\n" : "\n  ],\n");
            sb.append("  \"billboards\": [");
            first = true;
            for (Billboard billboard : sortedBillboards()) {
                if (!first) sb.append(',');
                first = false;
                sb.append("\n    {\"t\": ").append(billboard.tick).append(", \"pos\": ").append(vec(billboard.position)).append(", \"texture\": \"").append(billboard.texture).append('"');
                sb.append(", \"size\": "); fixed(sb, billboard.size);
                sb.append(", \"tint\": ").append(String.format(Locale.ROOT, "#%06X", billboard.tint & 0xFFFFFF));
                sb.append(", \"angle\": "); fixed(sb, billboard.angle);
                sb.append(", \"age\": "); fixed(sb, billboard.age);
                sb.append('}');
            }
            sb.append(first ? "],\n" : "\n  ],\n");
            sb.append("  \"guides\": [");
            first = true;
            for (Guide guide : sortedGuides()) {
                if (!first) sb.append(',');
                first = false;
                sb.append("\n    {\"t\": ").append(guide.tick).append(", \"p\": [");
                double[][] points = guide.points;
                for (int i = 0; i < points.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append('['); fixed(sb, points[i][0]); sb.append(','); fixed(sb, points[i][1]); sb.append(','); fixed(sb, points[i][2]); sb.append(']');
                }
                sb.append("]}");
            }
            sb.append(first ? "],\n" : "\n  ],\n");
            sb.append("  \"events\": [");
            first = true;
            for (Event event : events) {
                if (!first) sb.append(',');
                first = false;
                sb.append("\n    {\"t\": ").append(event.tick).append(", \"kind\": \"").append(event.kind)
                        .append("\", \"reason\": \"").append(event.reason).append("\", \"g\": ").append(event.group).append('}');
            }
            sb.append(first ? "]\n" : "\n  ]\n");
            sb.append("}\n");
            return new Baked(sb.toString(), ticks, written, samples);
        }
    }

    private record Warning(long group, long batch, Vec3 from, Vec3 dir, long since, long until,
                           int style, boolean laser, double beam) {
        String key() { return group + ":" + batch + ":" + until; }
    }

    private record Billboard(long tick, Vec3 position, String texture, double size, int tint, double angle, double age) {
        String key() {
            return tick + ":" + texture + ":" + Math.round(position.x() * 100) + ":" + Math.round(position.y() * 100)
                    + ":" + Math.round(position.z() * 100) + ":" + Math.round(age * 100);
        }
    }

    private record Guide(long tick, double[][] points) {}

    private record Event(long tick, String kind, String reason, long group) {}

    // ---- polyline simplification (design D8) --------------------------------------------------
    /**
     * Greedy Douglas-Peucker: extend the kept segment while every skipped sample stays inside the
     * tolerances. Most program bullets travel a straight line, so they collapse to their endpoints;
     * curved or breathing paths keep the samples they need.
     */
    private static List<float[]> simplify(List<float[]> in) {
        int n = in.size();
        if (n <= 2) return in;
        List<float[]> out = new ArrayList<>();
        out.add(in.get(0));
        int anchor = 0;
        for (int candidate = 2; candidate < n; candidate++) {
            boolean fits = in.get(candidate)[0] - in.get(anchor)[0] <= MAX_SPAN_TICKS;
            if (fits) {
                float[] a = in.get(anchor);
                float[] b = in.get(candidate);
                for (int k = anchor + 1; k < candidate; k++) {
                    if (!within(in.get(k), a, b)) { fits = false; break; }
                }
            }
            if (!fits) {
                out.add(in.get(candidate - 1));
                anchor = candidate - 1;
            }
        }
        if (out.get(out.size() - 1) != in.get(n - 1)) out.add(in.get(n - 1));
        return out;
    }

    private static boolean within(float[] point, float[] a, float[] b) {
        double span = b[0] - a[0];
        double w = span <= 0 ? 0 : (point[0] - a[0]) / span;
        for (int i = 1; i <= 3; i++) {
            if (Math.abs(point[i] - (a[i] + (b[i] - a[i]) * w)) > EPS_POSITION) return false;
        }
        if (Math.abs(point[4] - (a[4] + (b[4] - a[4]) * w)) > EPS_ALPHA) return false;
        if (Math.abs(point[5] - (a[5] + (b[5] - a[5]) * w)) > EPS_BRIGHTNESS) return false;
        if (Math.abs(point[6] - (a[6] + (b[6] - a[6]) * w)) > EPS_SIZE) return false;
        if (point.length <= 7) return true;
        if (Math.abs(point[7] - (a[7] + (b[7] - a[7]) * w)) > EPS_BEAM) return false;
        // Laser axis: a rotating beam must keep enough samples to bend.
        for (int i = 8; i <= 10; i++) {
            if (Math.abs(point[i] - (a[i] + (b[i] - a[i]) * w)) > EPS_DIRECTION) return false;
        }
        return true;
    }

    /** The exported polyline must reproduce every recorded sample inside the tolerances. */
    private static void verify(List<float[]> all, List<float[]> kept) {
        int k = 0;
        for (float[] sample : all) {
            while (k + 1 < kept.size() && kept.get(k + 1)[0] <= sample[0]) k++;
            float[] a = kept.get(k);
            float[] b = k + 1 < kept.size() ? kept.get(k + 1) : a;
            if (!within(sample, a, b)) {
                throw new IllegalStateException("simplified path lost tick " + (long) sample[0]
                        + " beyond tolerance (kept " + kept.size() + " of " + all.size() + ")");
            }
        }
    }

    // ---- index -------------------------------------------------------------------------------
    private static String index(List<Summary> summaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"cards\": [\n");
        for (int i = 0; i < summaries.size(); i++) {
            Summary summary = summaries.get(i);
            sb.append("    {\"owner\": \"").append(summary.owner).append("\", \"index\": ").append(summary.index)
                    .append(", \"card\": \"").append(summary.nameKey).append("\", \"ticks\": ").append(summary.ticks)
                    .append(", \"members\": ").append(summary.members)
                    .append(", \"samples\": ").append(summary.samples)
                    .append(", \"bytes\": ").append(summary.bytes)
                    .append(", \"file\": \"").append(summary.owner).append('_').append(summary.index).append(".json\"}");
            if (i + 1 < summaries.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    // ---- deterministic number formatting ------------------------------------------------------
    /** Fixed 3 decimals, locale-free, negative zero normalised to 0.000. */
    static void fixed(StringBuilder sb, double value) {
        if (!Double.isFinite(value)) value = 0;
        long scaled = Math.round(value * 1000.0);
        boolean negative = scaled < 0;
        if (negative) scaled = -scaled;
        long whole = scaled / 1000;
        long fraction = scaled % 1000;
        if (negative && (whole != 0 || fraction != 0)) sb.append('-');
        sb.append(whole).append('.');
        if (fraction < 10) sb.append("00"); else if (fraction < 100) sb.append('0');
        sb.append(fraction);
    }

    static String vec(Vec3 value) {
        StringBuilder sb = new StringBuilder(32);
        sb.append('[');
        fixed(sb, value.x()); sb.append(',');
        fixed(sb, value.y()); sb.append(',');
        fixed(sb, value.z());
        sb.append(']');
        return sb.toString();
    }

    private static String fixedKey(double[][] points) {
        StringBuilder sb = new StringBuilder();
        for (double[] point : points) {
            fixed(sb, point[0]); sb.append(',');
            fixed(sb, point[1]); sb.append(',');
            fixed(sb, point[2]); sb.append(';');
        }
        return sb.toString();
    }

    // ---- console -----------------------------------------------------------------------------
    private static void print(List<Summary> summaries) {
        long total = 0;
        long totalSamples = 0;
        System.out.printf(Locale.ROOT, "%-12s %2s  %-40s %6s %6s %9s %9s %7s%n",
                "owner", "#", "card", "ticks", "mbrs", "samples", "KB", "ms");
        for (Summary summary : summaries) {
            total += summary.bytes;
            totalSamples += summary.samples;
            System.out.printf(Locale.ROOT, "%-12s %2d  %-40s %6d %6d %9d %9.1f %7d%n",
                    summary.owner, summary.index, summary.nameKey, summary.ticks, summary.members,
                    summary.samples, summary.bytes / 1024.0, summary.nanos / 1_000_000);
        }
        System.out.printf(Locale.ROOT, "%nTOTAL %d card(s), %,d samples, %.2f MB text%n",
                summaries.size(), totalSamples, total / 1048576.0);
    }
}
