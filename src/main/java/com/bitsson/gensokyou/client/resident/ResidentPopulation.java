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

    public void tick(List<ResidentScene.Slot> candidates, ResidentRoute.Point viewer, ResidentWalker.Surface surface) {
        tick(candidates,viewer,surface,walker->true);
    }

    public void tick(List<ResidentScene.Slot> candidates, ResidentRoute.Point viewer, ResidentWalker.Surface surface,
                     Predicate<ResidentWalker> mayAppear) {
        tick++;
        retryAfter.entrySet().removeIf(e->e.getValue()<=tick);
        var renderable=new ArrayList<ResidentWalker>();
        for(Iterator<ResidentWalker> it=active.values().iterator();it.hasNext();) {
            var walker=it.next();
            var old=walker.frame(1);
            if(old==null || old.position().distance(viewer)>ResidentScene.VIEW_DISTANCE) {it.remove();continue;}
            walker.tick(surface);
            var frame=walker.frame(1);
            if(frame==null || frame.position().distance(viewer)>ResidentScene.VIEW_DISTANCE) {
                it.remove(); retryAfter.put(walker.slot().identity(),tick+20); continue;
            }
            renderable.add(walker);
        }
        int attempts=0;
        for(var slot:ResidentScene.candidates(candidates,viewer)) {
            if(active.size()>=ResidentScene.ACTIVE_LIMIT || attempts>=ADMISSIONS_PER_TICK) break;
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
            active.put(slot.identity(),walker); renderable.add(walker);
        }
        visible=List.copyOf(renderable);
    }

    public List<ResidentWalker> visible() { return visible; }
    public int size() { return active.size(); }
    public void clear() { active.clear(); retryAfter.clear(); visible=List.of(); tick=0; }
}
