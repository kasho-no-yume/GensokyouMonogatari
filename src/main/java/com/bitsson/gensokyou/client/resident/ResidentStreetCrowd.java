package com.bitsson.gensokyou.client.resident;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Incremental local street performances; no world seed, global navigation grid or distant simulation. */
public final class ResidentStreetCrowd {
    public static final int PROBES_PER_TICK=48, MAX_CACHED=384;
    public static final double SPAWN_RADIUS=72;
    private final Map<String,ResidentScene.Slot> slots=new LinkedHashMap<>();
    private final ArrayDeque<Seed> pending=new ArrayDeque<>();
    private ResidentRoute.Point lastViewer;
    private long tick,lastRefresh;
    private Work work;

    public record Seed(String identity,double x,double z,int axis) {
        public static Seed at(double x,double z,int axis) {
            int gx=(int)Math.floor(x/3),gz=(int)Math.floor(z/3);
            String id="gensokyou:street/"+gx+"/"+gz;
            long h=ResidentAppearance.hash(id);
            return new Seed(id,gx*3+1.5+((h>>>3)&3)*.08,gz*3+1.5+((h>>>7)&3)*.08,axis&1);
        }
        double distanceSquared(ResidentRoute.Point p) { double dx=x-p.x(),dz=z-p.z();return dx*dx+dz*dz; }
    }
    public interface Streets {
        /** Return only dry, loaded public ground, never a roof or building interior. */
        ResidentRoute.Point groundAt(double x,double z);
        boolean clear(ResidentRoute.Point from,ResidentRoute.Point to);
    }

    public void update(ResidentRoute.Point viewer,Supplier<List<Seed>> interests,Streets surface) {
        tick++;
        slots.values().removeIf(s->s.route().distanceSquared(viewer)>ResidentScene.VIEW_DISTANCE*ResidentScene.VIEW_DISTANCE);
        if(lastViewer==null || horizontalDistanceSquared(lastViewer,viewer)>=8*8 || tick-lastRefresh>=40) {
            var unique=new LinkedHashMap<String,Seed>();
            for(var seed:interests.get()) if(seed.distanceSquared(viewer)<=SPAWN_RADIUS*SPAWN_RADIUS)
                unique.putIfAbsent(seed.identity(),seed);
            pending.clear();
            unique.values().stream().filter(s->!slots.containsKey(s.identity()))
                    .sorted(Comparator.comparingDouble((Seed s)->s.distanceSquared(viewer)).thenComparing(Seed::identity))
                    .limit(ResidentScene.MAX_RESIDENTS).forEach(pending::add);
            lastViewer=viewer; lastRefresh=tick;
        }
        for(int probes=0;probes<PROBES_PER_TICK && slots.size()<MAX_CACHED;probes++) {
            if(work==null) {
                if(pending.isEmpty()) break;
                work=new Work(pending.remove());
            }
            if(work.seed.distanceSquared(viewer)>ResidentScene.VIEW_DISTANCE*ResidentScene.VIEW_DISTANCE) {work=null;continue;}
            if(work.step(surface)) {
                var slot=work.result();
                if(slot!=null) slots.put(slot.identity(),slot);
                work=null;
            }
        }
    }

    public List<ResidentScene.Slot> near(ResidentRoute.Point viewer,int budget) {
        return slots.values().stream().filter(s->s.route().distanceSquared(viewer)<=ResidentScene.VIEW_DISTANCE*ResidentScene.VIEW_DISTANCE)
                .sorted(Comparator.comparingDouble((ResidentScene.Slot s)->s.route().distanceSquared(viewer)).thenComparing(ResidentScene.Slot::identity))
                .limit(Math.max(0,budget)).toList();
    }
    public void clear() {slots.clear();pending.clear();work=null;lastViewer=null;tick=0;lastRefresh=0;}
    public int size() {return slots.size();}
    private static double horizontalDistanceSquared(ResidentRoute.Point a,ResidentRoute.Point b) {
        double dx=a.x()-b.x(),dz=a.z()-b.z();return dx*dx+dz*dz;
    }

    private static final class Work {
        final Seed seed;
        final long hash;
        final boolean walking;
        final int targetSteps;
        final List<ResidentRoute.Point> path=new ArrayList<>();
        int direction,steps;
        boolean reversed,valid;
        Work(Seed seed) {
            this.seed=seed;hash=ResidentAppearance.hash(seed.identity());
            walking=Math.floorMod(hash,5)!=0;
            targetSteps=16+(int)((hash>>>4)&15); // Eight to sixteen blocks, sampled every half block.
            direction=seed.axis()+(((hash>>>2)&1)==0?0:2);
        }
        boolean step(Streets surface) {
            if(path.isEmpty()) {
                var origin=surface.groundAt(seed.x(),seed.z());
                if(origin==null) return true;
                path.add(origin);valid=true;
                return !walking;
            }
            var previous=path.getLast();
            double dx=direction==0?.5:direction==2?-.5:0,dz=direction==1?.5:direction==3?-.5:0;
            var next=surface.groundAt(previous.x()+dx,previous.z()+dz);
            if(next==null || Math.abs(next.y()-previous.y())>.6 || !surface.clear(previous,next)) return stop();
            append(next);steps++;
            if(path.size()>ResidentRoute.MAX_POINTS) {path.removeLast();return stop();}
            if(steps==16 && ((hash>>>9)&3)==0) direction=(direction+(((hash>>>11)&1)==0?1:3))&3;
            return steps>=targetSteps;
        }
        boolean stop() {
            if(steps<8 && !reversed) {
                reversed=true;direction=(direction+2)&3;steps=0;
                var origin=path.getFirst();path.clear();path.add(origin);
                return false;
            }
            valid=steps>=8;
            return true;
        }
        void append(ResidentRoute.Point p) {
            if(path.size()>=2) {
                var a=path.get(path.size()-2);var b=path.getLast();
                double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z();
                double vx=p.x()-b.x(),vy=p.y()-b.y(),vz=p.z()-b.z();
                if(Math.abs(ux*vy-uy*vx)<1e-8 && Math.abs(ux*vz-uz*vx)<1e-8 && Math.abs(uy*vz-uz*vy)<1e-8
                        && ux*vx+uy*vy+uz*vz>0) {path.set(path.size()-1,p);return;}
            }
            path.add(p);
        }
        ResidentScene.Slot result() {
            if(!valid || path.isEmpty() || (walking && steps<8)) return null;
            return new ResidentScene.Slot(seed.identity(),new ResidentRoute(List.copyOf(path)),direction*90f,true);
        }
    }
}
