package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.math.*;
import java.util.*;

/** Local XY planes face +Z. Phase rotates both the figure and its gap. Angles are radians. */
public final class Patterns {
    private Patterns() {}
    private static void count(int count) { if (count<1 || count>512) throw new IllegalArgumentException("count in 1..512 required"); }
    private static Pattern.Member member(int index,Vec3 p,Vec3 d) { return new Pattern.Member(index,p,d,Pattern.Style.DEFAULT,Set.of(),-1); }
    private static Vec3 circle(double angle) { return new Vec3(StrictMath.cos(angle),StrictMath.sin(angle),0); }
    private static List<Pattern.Member> generated(int n, java.util.function.IntFunction<Pattern.Member> generator) {
        var result=new ArrayList<Pattern.Member>(n); for (int i=0;i<n;i++) result.add(generator.apply(i)); return List.copyOf(result);
    }
    public record Point(Vec3 position,Vec3 direction) implements Pattern {
        public Point { java.util.Objects.requireNonNull(position); direction=direction.unit(); }
        public List<Member> generate(Deterministic.Seed seed) { return List.of(member(0,position,direction)); }
        public int countBound() { return 1; }
        public double radiusBound() { return position.length(); }
    }
    public record Tagged(Pattern pattern,Set<String> tags,Pattern.Style style) implements Pattern {
        public Tagged { java.util.Objects.requireNonNull(pattern); tags=Set.copyOf(tags); java.util.Objects.requireNonNull(style); }
        public List<Member> generate(Deterministic.Seed seed) {
            return pattern.generate(seed).stream().map(m->{ var merged=new HashSet<>(m.tags()); merged.addAll(tags); return new Member(m.index(),m.position(),m.direction(),style,merged,m.targetSlot()); }).toList();
        }
        public int countBound() { return pattern.countBound(); }
        public double radiusBound() { return pattern.radiusBound(); }
    }
    public record Union(List<Pattern> children) implements Pattern {
        public Union { children=List.copyOf(children); count(children.stream().mapToInt(Pattern::countBound).reduce(0,Math::addExact)); }
        public List<Member> generate(Deterministic.Seed seed) {
            var result=new ArrayList<Member>();
            for (int c=0;c<children.size();c++) for (Member m:children.get(c).generate(seed.child(c))) result.add(new Member(result.size(),m.position(),m.direction(),m.style(),m.tags(),m.targetSlot()));
            return List.copyOf(result);
        }
        public int countBound() { return children.stream().mapToInt(Pattern::countBound).sum(); }
        public double radiusBound() { return children.stream().mapToDouble(Pattern::radiusBound).max().orElseThrow(); }
    }
    /** Exactly count members on the retained arc; gap is centered on phase. */
    public record Ring(int count,double radius,double gapRadians,double phaseRadians) implements Pattern {
        public Ring { Patterns.count(count); Deterministic.nonnegative(radius); Deterministic.finite(gapRadians,phaseRadians); if (gapRadians<0 || gapRadians>=Deterministic.TAU) throw new IllegalArgumentException("gap in [0,2pi)"); }
        public List<Member> generate(Deterministic.Seed seed) {
            return generated(count,i->{ double a=phaseRadians+(gapRadians==0 ? Deterministic.TAU*i/count : gapRadians/2+(Deterministic.TAU-gapRadians)*(i+.5)/count); return member(i,circle(a).multiply(radius),Vec3.Z); });
        }
        public int countBound() { return count; }
        public double radiusBound() { return radius; }
    }
    public record Fan(int count,double spreadRadians,double phaseRadians) implements Pattern {
        public Fan { Patterns.count(count); Deterministic.finite(spreadRadians,phaseRadians); if (spreadRadians<0 || spreadRadians>Deterministic.TAU) throw new IllegalArgumentException("spread in [0,2pi]"); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(count,i->member(i,Vec3.ZERO,Vec3.Z.rotate(Vec3.Y,phaseRadians+(count==1?0:spreadRadians*(i/(double)(count-1)-.5))))); }
        public int countBound() { return count; }
        public double radiusBound() { return 0; }
    }
    public record AngularGrid(int rows,int columns,double yawSpacing,double pitchSpacing) implements Pattern {
        public AngularGrid { if (rows<1 || columns<1) throw new IllegalArgumentException("positive dimensions required"); count(Math.multiplyExact(rows,columns)); Deterministic.finite(yawSpacing,pitchSpacing); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(countBound(),i->member(i,Vec3.ZERO,Vec3.Z.rotate(Vec3.X,((rows-1)/2.0-i/columns)*pitchSpacing).rotate(Vec3.Y,(i%columns-(columns-1)/2.0)*yawSpacing))); }
        public int countBound() { return rows*columns; }
        public double radiusBound() { return 0; }
    }
    public record ParallelGrid(int rows,int columns,double spacingX,double spacingY,Set<Integer> omittedColumns,Vec3 offset) implements Pattern {
        public ParallelGrid {
            if (rows<1 || columns<1) throw new IllegalArgumentException("positive dimensions required"); count(Math.multiplyExact(rows,columns));
            Deterministic.nonnegative(spacingX); Deterministic.nonnegative(spacingY); omittedColumns=Set.copyOf(omittedColumns); java.util.Objects.requireNonNull(offset);
            for (int column:omittedColumns) if (column<0 || column>=columns) throw new IllegalArgumentException("column out of range");
        }
        public List<Member> generate(Deterministic.Seed seed) {
            var result=new ArrayList<Member>();
            for (int row=0;row<rows;row++) for (int column=0;column<columns;column++) if (!omittedColumns.contains(column)) {
                // Identity is the unmasked lattice index, so removing a column does not renumber survivors.
                result.add(member(row*columns+column,offset.add(new Vec3((column-(columns-1)/2.0)*spacingX,((rows-1)/2.0-row)*spacingY,0)),Vec3.Z));
            }
            return List.copyOf(result);
        }
        public int countBound() { return rows*(columns-omittedColumns.size()); }
        public double radiusBound() { return offset.length()+StrictMath.hypot((columns-1)*spacingX/2,(rows-1)*spacingY/2); }
    }
    public record AxisNodes(Vec3 axis,List<Double> distances,List<Vec3> directions,double inset) implements Pattern {
        public AxisNodes {
            axis=axis.unit(); distances=List.copyOf(distances); directions=directions.stream().map(Vec3::unit).toList();
            count(Math.multiplyExact(distances.size(),directions.size())); Deterministic.nonnegative(inset);
            distances.forEach(d->Deterministic.finite(d));
        }
        public List<Member> generate(Deterministic.Seed seed) { return generated(countBound(),i->{ Vec3 d=directions.get(i%directions.size()); return member(i,axis.multiply(distances.get(i/directions.size())).add(d.multiply(inset)),d); }); }
        public int countBound() { return distances.size()*directions.size(); }
        public double radiusBound() { return distances.stream().mapToDouble(StrictMath::abs).max().orElseThrow()+inset; }
    }
    public record SurfaceRays(List<Vec3> resolvedPoints,List<Vec3> directions) implements Pattern {
        public SurfaceRays { resolvedPoints=List.copyOf(resolvedPoints); directions=directions.stream().map(Vec3::unit).toList(); count(Math.multiplyExact(resolvedPoints.size(),directions.size())); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(countBound(),i->member(i,resolvedPoints.get(i/directions.size()),directions.get(i%directions.size()))); }
        public int countBound() { return resolvedPoints.size()*directions.size(); }
        public double radiusBound() { return resolvedPoints.stream().mapToDouble(Vec3::length).max().orElseThrow(); }
    }
    public record Rose(int petals,int samplesPerPetal,double baseRadius,double amplitude,double phaseRadians) implements Pattern {
        public Rose { if (petals<2 || samplesPerPetal<1) throw new IllegalArgumentException("petals >=2 and samples >=1 required"); count(Math.multiplyExact(petals,samplesPerPetal)); Deterministic.nonnegative(baseRadius); Deterministic.nonnegative(amplitude); Deterministic.finite(phaseRadians); if (amplitude>baseRadius) throw new IllegalArgumentException("amplitude exceeds base"); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(countBound(),i->{ double a=Deterministic.TAU*i/countBound(),r=baseRadius+amplitude*StrictMath.cos(petals*a); return member(i,circle(a+phaseRadians).multiply(r),Vec3.Z); }); }
        public int countBound() { return petals*samplesPerPetal; }
        public double radiusBound() { return baseRadius+amplitude; }
    }
    public record Spiral(int count,double turns,double minRadius,double maxRadius,int handedness,double phaseRadians) implements Pattern {
        public Spiral { Patterns.count(count); Deterministic.nonnegative(minRadius); Deterministic.nonnegative(maxRadius); Deterministic.finite(turns,phaseRadians); if (maxRadius<minRadius || StrictMath.abs(handedness)!=1) throw new IllegalArgumentException("ordered radii and handedness +/-1 required"); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(count,i->{ double u=count==1?0:i/(double)(count-1); return member(i,circle(phaseRadians+handedness*Deterministic.TAU*turns*u).multiply(minRadius+(maxRadius-minRadius)*u),Vec3.Z); }); }
        public int countBound() { return count; }
        public double radiusBound() { return maxRadius; }
    }
    public record ConeScatter(int count,double coneRadians,long channel) implements Pattern {
        public ConeScatter { Patterns.count(count); Deterministic.nonnegative(coneRadians); if (coneRadians>StrictMath.PI) throw new IllegalArgumentException("cone <= pi"); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(count,i->member(i,Vec3.ZERO,Deterministic.seedDirection(seed,channel,i,Deterministic.Distribution.CONE,Vec3.Z,coneRadians))); }
        public int countBound() { return count; }
        public double radiusBound() { return 0; }
    }
    public record VolumeScatter(int count,Vec3 halfExtents,long channel) implements Pattern {
        public VolumeScatter { Patterns.count(count); if (halfExtents.x()<0 || halfExtents.y()<0 || halfExtents.z()<0) throw new IllegalArgumentException("nonnegative extents required"); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(count,i->member(i,new Vec3((2*seed.unit(channel,3L*i)-1)*halfExtents.x(),(2*seed.unit(channel,3L*i+1)-1)*halfExtents.y(),(2*seed.unit(channel,3L*i+2)-1)*halfExtents.z()),Vec3.Z)); }
        public int countBound() { return count; }
        public double radiusBound() { return halfExtents.length(); }
    }
    public enum SphereDistribution { FIBONACCI, SEEDED }
    public enum Direction { OUTWARD, INWARD, FIXED }
    private static Vec3 sphere(int index,int count,SphereDistribution distribution,Deterministic.Seed seed,long channel) {
        if (distribution==SphereDistribution.SEEDED) return Deterministic.seedDirection(seed,channel,index,Deterministic.Distribution.UNIFORM_SPHERE,Vec3.Z,StrictMath.PI);
        double z=1-2*(index+.5)/count, a=index*StrictMath.PI*(3-StrictMath.sqrt(5)),r=StrictMath.sqrt(1-z*z);
        return new Vec3(r*StrictMath.cos(a),r*StrictMath.sin(a),z);
    }
    public record SphereSurface(int count,double radius,SphereDistribution distribution,Direction direction,Vec3 fixedDirection,long channel) implements Pattern {
        public SphereSurface { Patterns.count(count); Deterministic.nonnegative(radius); java.util.Objects.requireNonNull(distribution); java.util.Objects.requireNonNull(direction); fixedDirection=fixedDirection.unit(); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(count,i->{ Vec3 d=sphere(i,count,distribution,seed,channel); return member(i,d.multiply(radius),switch(direction){ case OUTWARD->d; case INWARD->d.multiply(-1); case FIXED->fixedDirection; }); }); }
        public int countBound() { return count; }
        public double radiusBound() { return radius; }
    }
    public record SphereDirections(int count,SphereDistribution distribution,long channel) implements Pattern {
        public SphereDirections { Patterns.count(count); java.util.Objects.requireNonNull(distribution); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(count,i->member(i,Vec3.ZERO,sphere(i,count,distribution,seed,channel))); }
        public int countBound() { return count; }
        public double radiusBound() { return 0; }
    }
    public record HelixColumn(double height,double turns,int arms,int samplesPerArm,ScalarCurve radiusByHeight,double phaseRadians) implements Pattern {
        public HelixColumn { Deterministic.positive(height); Deterministic.finite(turns,phaseRadians); if (arms<1 || samplesPerArm<1) throw new IllegalArgumentException("positive helix dimensions required"); count(Math.multiplyExact(arms,samplesPerArm)); java.util.Objects.requireNonNull(radiusByHeight); if (radiusByHeight.keys().stream().anyMatch(k->k.value()<0)) throw new IllegalArgumentException("nonnegative radii required"); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(countBound(),i->{ int arm=i/samplesPerArm,sample=i%samplesPerArm; double u=samplesPerArm==1?0:sample/(double)(samplesPerArm-1),y=height*u,a=phaseRadians+Deterministic.TAU*(turns*u+arm/(double)arms),r=radiusByHeight.at(u); return member(i,new Vec3(r*StrictMath.cos(a),y,r*StrictMath.sin(a)),Vec3.Y); }); }
        public int countBound() { return arms*samplesPerArm; }
        public double radiusBound() { return StrictMath.hypot(height,radiusByHeight.maxAbs()); }
    }
    public record Radial(int count,double gapRadians,double phaseRadians) implements Pattern {
        public Radial { new Ring(count,0,gapRadians,phaseRadians); }
        public List<Member> generate(Deterministic.Seed seed) { return new Ring(count,1,gapRadians,phaseRadians).generate(seed).stream().map(m->member(m.index(),Vec3.ZERO,m.position())).toList(); }
        public int countBound() { return count; }
        public double radiusBound() { return 0; }
    }
    /** Latitude bands with a common azimuthal gap; unlike SphereSurface this has a door. */
    public record Shell(int bands,int perBand,double radius,double gapRadians,double phaseRadians) implements Pattern {
        public Shell { if (bands<1 || perBand<1) throw new IllegalArgumentException("positive dimensions required"); count(Math.multiplyExact(bands,perBand)); new Ring(perBand,radius,gapRadians,phaseRadians); }
        public List<Member> generate(Deterministic.Seed seed) {
            List<Member> ring=new Ring(perBand,1,gapRadians,phaseRadians).generate(seed);
            return generated(countBound(),i->{ double z=1-2*(i/perBand+.5)/bands; Vec3 q=ring.get(i%perBand).position(); Vec3 p=new Vec3(q.x()*StrictMath.sqrt(1-z*z),q.y()*StrictMath.sqrt(1-z*z),z).multiply(radius); return member(i,p,Vec3.Z); });
        }
        public int countBound() { return bands*perBand; }
        public double radiusBound() { return radius; }
    }
    public record Cage(int levels,int perLevel,double radius,double height,double gapRadians,double phaseRadians) implements Pattern {
        public Cage { if (levels<1 || perLevel<1) throw new IllegalArgumentException("positive dimensions required"); count(Math.multiplyExact(levels,perLevel)); Deterministic.nonnegative(height); new Ring(perLevel,radius,gapRadians,phaseRadians); }
        public List<Member> generate(Deterministic.Seed seed) {
            var ring=new Ring(perLevel,radius,gapRadians,phaseRadians).generate(seed);
            return generated(countBound(),i->{ Vec3 q=ring.get(i%perLevel).position(); return member(i,new Vec3(q.x(),levels==1?0:height*(i/perLevel/(double)(levels-1)-.5),q.y()),Vec3.Z); });
        }
        public int countBound() { return levels*perLevel; }
        public double radiusBound() { return StrictMath.hypot(radius,height/2); }
    }
    public record AroundPoint(int count,Vec3 center,double minRadius,double maxRadius,double aimBias,double scatterRadians,long channel) implements Pattern {
        public AroundPoint { Patterns.count(count); java.util.Objects.requireNonNull(center); Deterministic.positive(minRadius); Deterministic.nonnegative(maxRadius); Deterministic.finite(aimBias,scatterRadians); if (maxRadius<minRadius || aimBias<0 || aimBias>1 || scatterRadians<0 || scatterRadians>StrictMath.PI) throw new IllegalArgumentException("invalid around-point envelope"); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(count,i->{
            Vec3 radial=sphere(i,count,SphereDistribution.FIBONACCI,seed,channel);
            double r=minRadius+(maxRadius-minRadius)*seed.unit(channel,4L*i);
            Vec3 p=center.add(radial.multiply(r)),aim=center.subtract(p).unit();
            Vec3 d=seed.unit(channel,4L*i+1)<aimBias?aim:Deterministic.seedDirection(seed,channel+1,i,Deterministic.Distribution.CONE,aim,scatterRadians);
            return member(i,p,d);
        }); }
        public int countBound() { return count; }
        public double radiusBound() { return center.length()+maxRadius; }
    }
    /** True volume randomness and an independent exact-aim branch. */
    public record Lattice(int count,Vec3 center,double minRadius,double maxRadius,double aimBias,double scatterRadians,long channel) implements Pattern {
        public Lattice { new AroundPoint(count,center,minRadius,maxRadius,aimBias,scatterRadians,channel); }
        public List<Member> generate(Deterministic.Seed seed) { return generated(count,i->{
            Vec3 radial=Deterministic.seedDirection(seed,channel,i,Deterministic.Distribution.UNIFORM_SPHERE,Vec3.Z,StrictMath.PI);
            double r=StrictMath.cbrt(minRadius*minRadius*minRadius+seed.unit(channel+1,i)*(maxRadius*maxRadius*maxRadius-minRadius*minRadius*minRadius));
            Vec3 p=center.add(radial.multiply(r)),aim=center.subtract(p).unit();
            Vec3 d=seed.unit(channel+2,i)<aimBias?aim:Deterministic.seedDirection(seed,channel+3,i,Deterministic.Distribution.CONE,aim,scatterRadians);
            return member(i,p,d);
        }); }
        public int countBound() { return count; }
        public double radiusBound() { return center.length()+maxRadius; }
    }
    public record Rain(int count,Vec3 halfExtents,double height,long channel) implements Pattern {
        public Rain { new VolumeScatter(count,halfExtents,channel); Deterministic.finite(height); }
        public List<Member> generate(Deterministic.Seed seed) { return new VolumeScatter(count,halfExtents,channel).generate(seed).stream().map(m->member(m.index(),m.position().add(Vec3.Y.multiply(height)),Vec3.Y.multiply(-1))).toList(); }
        public int countBound() { return count; }
        public double radiusBound() { return halfExtents.length()+StrictMath.abs(height); }
    }
    /**
     * A {@code rows×columns} lattice whose columns tilt about local up:
     * column sign ∈ [-1,1] rotates {@code forward} about local Y by {@code sign*columnTiltRadians}.
     * Used for {@code normalize(F ± kR)} panels. Encoded as scalars, not per-member vectors.
     */
    public record TiltedGrid(int rows,int columns,double spacingX,double spacingY,
                             Set<Integer> omittedColumns,Vec3 offset,Vec3 forward,double columnTiltRadians) implements Pattern {
        public TiltedGrid {
            if (rows<1 || columns<1) throw new IllegalArgumentException("positive dimensions required");
            Deterministic.nonnegative(spacingX); Deterministic.nonnegative(spacingY);
            omittedColumns=Set.copyOf(omittedColumns); java.util.Objects.requireNonNull(offset);
            forward=forward.unit(); Deterministic.finite(columnTiltRadians);
            for (int column:omittedColumns) if (column<0 || column>=columns) throw new IllegalArgumentException("column out of range");
            count(rows*(columns-omittedColumns.size()));
        }
        private Vec3 direction(int column) {
            if (columns<2 || columnTiltRadians==0) return forward;
            double sign=2.0*column/(columns-1)-1;
            return forward.rotate(Vec3.Y,sign*columnTiltRadians);
        }
        public List<Member> generate(Deterministic.Seed seed) {
            var result=new ArrayList<Member>();
            for (int row=0;row<rows;row++) for (int column=0;column<columns;column++) {
                if (omittedColumns.contains(column)) continue;
                result.add(member(row*columns+column,offset.add(new Vec3((column-(columns-1)/2.0)*spacingX,
                        ((rows-1)/2.0-row)*spacingY,0)),direction(column)));
            }
            return List.copyOf(result);
        }
        public int countBound() { return rows*(columns-omittedColumns.size()); }
        public double radiusBound() { return offset.length()+StrictMath.hypot((columns-1)*spacingX/2,(rows-1)*spacingY/2); }
    }
}
