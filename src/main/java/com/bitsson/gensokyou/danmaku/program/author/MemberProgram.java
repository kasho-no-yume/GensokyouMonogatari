package com.bitsson.gensokyou.danmaku.program.author;

import com.bitsson.gensokyou.danmaku.program.TimeDomain;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramVisuals;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Content builder. Harm and direction are intentional; derivation bounds are computed. */
public final class MemberProgram {
    private final String name;
    private final Pattern pattern;
    private final Path path;
    private final long life;
    private final boolean attack;
    private Actions.DirectionRule direction;
    private long directionChannel;
    private Transforms.Transform transform=new Transforms.Compose(List.of());
    private Presentation.Profile presentation;
    private CollisionProfile collision=CollisionProfile.projectile();
    private final List<Actions.ChildAction> actions=new ArrayList<>();
    private MemberProgram(String name,Pattern pattern,Path path,long life,boolean attack) {
        this.name=Objects.requireNonNull(name); this.pattern=Objects.requireNonNull(pattern); this.path=Objects.requireNonNull(path);
        if (name.isBlank() || life<1) throw new IllegalArgumentException("member name and positive lifetime required");
        this.life=life; this.attack=attack;
        presentation=new Presentation.Profile(attack?List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,life)):List.of(),null,null,List.of());
    }
    public static MemberProgram attack(String name,Pattern pattern,Path path,long lifetime) { return new MemberProgram(name,pattern,path,lifetime,true); }
    public static MemberProgram decoration(String name,Pattern pattern,Path path,long lifetime) { return new MemberProgram(name,pattern,path,lifetime,false); }
    public static Pattern styled(Pattern pattern,String projectile,String color,double collisionRadius,double damageScale,double visualRadius,String... tags) {
        var style=new Pattern.Style(projectile,color,collisionRadius,damageScale,visualRadius);
        ProgramVisuals.validate(style);
        return new Patterns.Tagged(pattern,Set.of(tags),style);
    }
    public MemberProgram fixedDirection() { direction=Actions.DirectionRule.FIXED; return this; }
    public MemberProgram templateDirections() { direction=Actions.DirectionRule.TEMPLATE; return this; }
    public MemberProgram randomDirections(long channel) { direction=Actions.DirectionRule.SEEDED_SPHERE; directionChannel=channel; return this; }
    /**
     * 每发弹朝「本拍采样到的目标点」飞（真实的源→目标向量，含真实距离与倾斜）。
     * 采样点在运行期由服务端解析并随批次下发，客户端据此重建同一条方向。
     */
    public MemberProgram aimAtTarget() { direction=Actions.DirectionRule.AIM_LOCAL; return this; }
    public MemberProgram transform(Transforms.Transform value) { transform=Objects.requireNonNull(value); return this; }
    /** Normal phase first, then translucent/no-collision phase; repeats on the chosen clock. */
    public MemberProgram hiddenCycle(TimeDomain clock,double period,double activeTicks,double phase) {
        presentation=new Presentation.Profile(presentation.damageWindows(),new Presentation.VisibilityCycle(clock,period,activeTicks,phase),
                presentation.fade(),presentation.flashes(),presentation.soundCues());
        return this;
    }
    public MemberProgram presentation(Presentation.Profile value) {
        if (!attack && !value.damageWindows().isEmpty()) throw new IllegalArgumentException(name+": decoration cannot have damage windows");
        presentation=value; return this;
    }
    public MemberProgram laser(double length,boolean clipBlocks,int hitInterval) { collision=new CollisionProfile.Laser(length,clipBlocks,hitInterval); return this; }
    /** 球弹穿透方块（蛛丝等）：只判实体命中，方块不再截断。 */
    public MemberProgram piercing() { collision=new CollisionProfile.Projectile(true); return this; }
    public MemberProgram child(Actions.ChildAction action) { actions.add(Objects.requireNonNull(action)); return this; }
    public MemberProgram emit(Selection parents,long firstAge,long period,int count,Actions.ChildProgram child) {
        return child(new Actions.ChildAction(Actions.ChildActionKind.EMIT,parents,new Actions.AtAge(firstAge),period,count,child,
                Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y));
    }
    public MemberProgram replace(Selection parents,Actions.Trigger trigger,Actions.ChildProgram child,Actions.OnReject rejection) {
        return child(new Actions.ChildAction(Actions.ChildActionKind.REPLACE,parents,trigger,1,1,child,
                Actions.Binding.snapshot(),Actions.AtomicScope.ALL_SELECTED_PARENTS,rejection,ScalarCurve.constant(0),Vec3.Y));
    }
    public Actions.ChildProgram build() {
        try {
            if (direction==null) throw new IllegalArgumentException("choose fixedDirection(), templateDirections(), aimAtTarget() or randomDirections()");
            if (attack && presentation.damageWindows().isEmpty()) throw new IllegalArgumentException("attack requires a damage window; use decoration() for harmless members");
            validateVisuals(pattern);
            for (var member:pattern.generate(new Deterministic.Seed(0,List.of()))) {
                boolean laser=collision instanceof CollisionProfile.Laser;
                if (member.style().projectile().equals("laser")!=laser)
                    throw new IllegalArgumentException("laser style and laser collision must be selected together");
            }
            long descendants=0; int generations=0;
            for (var action:actions) {
                descendants=Math.addExact(descendants,Math.multiplyExact(Math.multiplyExact((long)pattern.countBound(),action.count()),action.child().totalMembersBound()));
                generations=Math.max(generations,1+action.child().maxGenerations());
            }
            return new Actions.ChildProgram(pattern,path,life,generations,descendants,actions,presentation,transform,direction,directionChannel,collision);
        } catch (IllegalArgumentException | ArithmeticException ex) { throw new IllegalArgumentException(name+": "+ex.getMessage(),ex); }
    }
    private static void validateVisuals(Pattern pattern) {
        for (var member:pattern.generate(new Deterministic.Seed(0,List.of()))) ProgramVisuals.validate(member.style());
    }
}
