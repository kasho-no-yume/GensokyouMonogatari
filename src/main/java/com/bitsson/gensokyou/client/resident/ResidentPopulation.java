package com.bitsson.gensokyou.client.resident;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Only visible, supported residents consume slots. Existing walkers survive candidate reordering. */
public final class ResidentPopulation {
    public static final int ADMISSIONS_PER_TICK = 12;
    // Long enough to visit all 512 spare candidates before visible foreground points retry.
    public static final int VIEW_RETRY_TICKS = 60;
    private final Map<String,ResidentWalker> active = new LinkedHashMap<>();
    private final Map<String,Long> retryAfter = new HashMap<>();
    private List<ResidentWalker> visible = List.of();
    private long tick;
    private final Map<ResidentWalker, Long> born = new HashMap<>();
    private ResidentRoute.Point initialViewer, lastViewer;
    private int initialTicks;

    public void tickAoi(List<ResidentScene.Slot> candidates, ResidentRoute.Point viewer, ResidentWalker.Surface surface) {
        if (lastViewer == null || lastViewer.distance(viewer) > ResidentSpawnVisibility.DESPAWN_RADIUS) {
            initialViewer = viewer; initialTicks = 0;
        }
        lastViewer = viewer;
        boolean initial = initialTicks++ < 40 && initialViewer.distance(viewer) < 8;
        update(candidates, viewer, surface, walker -> ResidentSpawnVisibility.mayAppear(walker.frame(1).position(), viewer,
                initial || VillageResidentAnchors.isIndoor(walker.slot())), true);
    }

    public void tick(List<ResidentScene.Slot> candidates, ResidentRoute.Point viewer, ResidentWalker.Surface surface) {
        tick(candidates,viewer,surface,walker->true);
    }

    public void tick(List<ResidentScene.Slot> candidates, ResidentRoute.Point viewer, ResidentWalker.Surface surface,
                      Predicate<ResidentWalker> mayAppear) {
        update(candidates, viewer, surface, mayAppear, false);
    }

    private void update(List<ResidentScene.Slot> candidates, ResidentRoute.Point viewer, ResidentWalker.Surface surface,
                        Predicate<ResidentWalker> mayAppear, boolean aoi) {
        tick++;
        double retirement = aoi ? ResidentSpawnVisibility.DESPAWN_RADIUS : ResidentScene.VIEW_DISTANCE;
        retryAfter.entrySet().removeIf(e->e.getValue()<=tick);
        var renderable=new ArrayList<ResidentWalker>();
        for(Iterator<ResidentWalker> it=active.values().iterator();it.hasNext();) {
            var walker=it.next();
            var old=walker.frame(1);
            if(old==null || old.position().distance(viewer)>retirement) {it.remove();born.remove(walker);continue;}
            walker.tick(surface);
            var frame=walker.frame(1);
            if(frame==null || frame.position().distance(viewer)>retirement) {
                it.remove(); born.remove(walker); retryAfter.put(walker.slot().identity(),tick+20); continue;
            }
            renderable.add(walker);
        }
        int attempts=0;
        for(var slot:ResidentScene.candidates(candidates,viewer)) {
            if((!aoi && active.size()>=ResidentScene.ACTIVE_LIMIT) || attempts>=ADMISSIONS_PER_TICK) break;
            if(active.containsKey(slot.identity()) || retryAfter.containsKey(slot.identity())) continue;
            attempts++;
            var walker=new ResidentWalker(slot);
            walker.tick(surface);
            var frame=walker.frame(1);
            if(frame==null || frame.position().distance(viewer)>ResidentScene.VIEW_DISTANCE) {
                retryAfter.put(slot.identity(),tick+40); continue;
            }
            if(slot.identity().startsWith("gensokyou:street/") && (frame.position().distance(viewer)<8
                    || renderable.stream().anyMatch(other->other.frame(1).position().distance(frame.position())<1.2))) {
                retryAfter.put(slot.identity(),tick+20); continue;
            }
            // Inspect the actual resolved initial frame, including the route's randomized phase.
            // Existing walkers above never run this gate when they walk into view.
            if(!mayAppear.test(walker)) {
                retryAfter.put(slot.identity(),tick+VIEW_RETRY_TICKS); continue;
            }
            if (aoi && inCell(renderable, frame.position()) >= 8) {
                retryAfter.put(slot.identity(), tick + 20); continue;
            }
            if (active.size() >= ResidentScene.ACTIVE_LIMIT) {
                var retire = renderable.stream().filter(w -> w.frame(1).position().distance(viewer) > 40)
                        .filter(w -> inCell(renderable, w.frame(1).position()) > inCell(renderable, frame.position()))
                        .max(java.util.Comparator.comparingDouble(w -> w.frame(1).position().distance(viewer))).orElse(null);
                if (retire == null) continue;
                active.remove(retire.slot().identity()); renderable.remove(retire); born.remove(retire);
                retryAfter.put(retire.slot().identity(), tick + 60);
            }
            active.put(slot.identity(),walker); renderable.add(walker); born.put(walker, tick);
        }
        visible=List.copyOf(renderable);
    }

    public List<ResidentWalker> visible() { return visible; }
    public int size() { return active.size(); }
    public float opacity(ResidentWalker walker, float partial) {
        return walker.slot().identity().startsWith("gensokyou:preview/") ? 1F
                : Math.clamp((tick - born.getOrDefault(walker, tick) + partial) / 10F, 0F, 1F);
    }
    private static long inCell(List<ResidentWalker> residents, ResidentRoute.Point point) {
        int x = (int) Math.floor(point.x() / 16), z = (int) Math.floor(point.z() / 16);
        return residents.stream().filter(w -> (int) Math.floor(w.frame(1).position().x() / 16) == x
                && (int) Math.floor(w.frame(1).position().z() / 16) == z).count();
    }
    public void clear() {
        active.clear(); retryAfter.clear(); born.clear(); visible=List.of(); tick=0;
        initialViewer=null; lastViewer=null; initialTicks=0;
    }
}
