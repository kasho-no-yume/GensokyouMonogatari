package com.bitsson.gensokyou.danmaku.program.author;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Development content only. No existing BOSS card is replaced or enabled. */
public final class AuthoringTemplates {
    private AuthoringTemplates() {}
    /** Twenty-second side-by-side probe: two seconds active, two seconds hidden. */
    public static CardProgram.Compiled hiddenState() {
        var card=new CardProgram("gensokyou:author_hidden").clearance(0);
        var targets=card.targets("players",64,1);
        var frame=card.aimed("frame",targets,0,new Vec3(0,1,0));
        var balls=card.offset("balls",frame,new Vec3(-3,0,5));
        var beam=card.offset("beam",frame,new Vec3(3,0,0));
        var model=card.offset("model",frame,new Vec3(0,2,5));
        var spheres=MemberProgram.attack("hidden spheres",MemberProgram.styled(
                new Patterns.ParallelGrid(1,3,1.5,1,Set.of(),Vec3.ZERO),"sphere","cyan",.4,1,.55),
                new Paths.Hold(),400).fixedDirection()
                .hiddenCycle(com.bitsson.gensokyou.danmaku.program.TimeDomain.MEMBER_AGE,80,40,0).build();
        var laser=MemberProgram.attack("hidden laser",MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),
                "laser","red",.18,1,.25),new Paths.Hold(),400).fixedDirection().laser(18,false,10)
                .hiddenCycle(com.bitsson.gensokyou.danmaku.program.TimeDomain.MEMBER_AGE,80,40,0).build();
        card.fire(balls,spheres,40,71); card.fire(beam,laser,40,73);
        var star=MemberProgram.attack("hidden model",MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),
                "star_prism","gold",.3,1,.65),new Paths.Hold(),400).fixedDirection()
                .hiddenCycle(com.bitsson.gensokyou.danmaku.program.TimeDomain.MEMBER_AGE,80,40,0).build();
        card.fire(model,star,40,79);
        card.at(441,CardProgram::endCard);
        return card.once();
    }
    public static CardProgram.Compiled volley() {
        var card=new CardProgram("gensokyou:author_volley");
        var targets=card.targets("players",64,5);
        var gun=card.aimed("volley",targets,0,new Vec3(0,1,0));
        var bullets=MemberProgram.attack("fan",MemberProgram.styled(new Patterns.Fan(9,Math.toRadians(60),0),"sphere","#62D6B4",.12,1,.22,"fan"),
                new Paths.Linear(Vec3.Z,.18),100).templateDirections().build();
        card.every(0,30,3,c->c.fire(gun,bullets,20,31));
        card.at(181,CardProgram::endCard);
        return card.once();
    }
    public static CardProgram.Compiled flower() {
        var card=new CardProgram("gensokyou:author_flower");
        var targets=card.targets("players",64,5);
        var gun=card.aimed("flower",targets,0,new Vec3(0,1,0));
        var pattern=new Patterns.Union(List.of(
                MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),"star_prism","gold",.18,2,.35,"core"),
                MemberProgram.styled(new Patterns.Rose(5,5,2,1,0),"sphere","#B080FF",.12,1,.22,"petals")));
        var motion=new Transforms.Compose(List.of(new Transforms.Breathe(1,.3,60,0),
                new Transforms.Rotate(Vec3.Z,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(180,Math.PI*2))),Vec3.ZERO),
                new Transforms.Translate(new Paths.Linear(Vec3.Z,.04))));
        var members=MemberProgram.attack("breathing flower",pattern,new Paths.Hold(),180).fixedDirection().transform(motion).build();
        card.fire(gun,members,20,47);
        card.at(100,c->c.release(gun,new Selection.Tag("core"),new Selection.Tag("petals"),.16));
        card.at(201,CardProgram::endCard);
        return card.once();
    }
    public static CardProgram.Compiled carriers() {
        var card=new CardProgram("gensokyou:author_carriers");
        var targets=card.targets("players",64,5);
        var gun=card.aimed("carriers",targets,0,new Vec3(0,1,0));
        var fragments=MemberProgram.attack("fragments",MemberProgram.styled(new Patterns.SphereDirections(4,Patterns.SphereDistribution.SEEDED,7),
                "sphere","cyan",.1,1,.18,"fragments"),new Paths.Linear(Vec3.Z,.12),70).templateDirections().build();
        var split=MemberProgram.attack("splitting children",MemberProgram.styled(new Patterns.Fan(3,.5,0),"star_prism","pink",.12,1,.22,"children"),
                new Paths.Linear(Vec3.Z,.10),100).templateDirections()
                .replace(new Selection.Tag("children"),new Actions.AtAge(40),fragments,Actions.OnReject.KEEP_PARENT).build();
        var mothers=MemberProgram.attack("moving mothers",MemberProgram.styled(new Patterns.Fan(3,.6,0),"sphere","blue",.18,1,.3,"mothers"),
                new Paths.Arc(Vec3.Z,Vec3.Y,.007,.12),100).templateDirections()
                .emit(new Selection.Tag("mothers"),20,20,3,split).build();
        card.fire(gun,mothers,20,59);
        card.at(191,CardProgram::endCard);
        return card.once();
    }
}
