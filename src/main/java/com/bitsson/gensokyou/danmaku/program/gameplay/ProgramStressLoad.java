package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.TimeDomain;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Explicit pressure fixture; numbers are test inputs, not BOSS balance. */
public final class ProgramStressLoad {
    public static final int PERIOD=200;
    private ProgramStressLoad() {}
    public static List<Actions.ChildProgram> batches(int count) {
        if (count!=800 && count!=2000) throw new IllegalArgumentException("stress count must be 800 or 2000");
        var result=new ArrayList<Actions.ChildProgram>();
        var child=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.Radial(4,0,0),Set.of("derived"),
                new Pattern.Style("sphere","gold",.1,1,.2)),new Paths.Linear(Vec3.Z,.08),80,0,0,List.of(),
                damage(80),new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
        // Forty parents emit four children at age60. Together with count-160
        // parents this reaches exactly count, without replacing or shortening them.
        for (int offset=0;offset<count-160;offset+=40) {
            int n=Math.min(40,count-160-offset);
            var action=new Actions.ChildAction(Actions.ChildActionKind.EMIT,new Selection.IndexRange(0,n),new Actions.AtAge(60),1,1,child,
                    Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y);
            var pattern=new Patterns.Tagged(new Patterns.ParallelGrid(1,n,.4,0,Set.of(),new Vec3(0,(offset/40)*.35,0)),Set.of("parent"),
                    new Pattern.Style("sphere","blue",.1,1,.2));
            var transform=new Transforms.Compose(List.of(new Transforms.Breathe(1,.15,100,0),
                    new Transforms.Rotate(Vec3.Z,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(200,.8))),Vec3.ZERO)));
            result.add(new Actions.ChildProgram(pattern,new Paths.Linear(Vec3.Z,.02),PERIOD,offset==0?1:0,offset==0?160:0,
                    offset==0?List.of(action):List.of(),damage(PERIOD),transform));
        }
        var rotate=new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,-.4),new ScalarCurve.Key(200,.4))),Vec3.ZERO);
        result.add(new Actions.ChildProgram(new Patterns.Tagged(new Patterns.ParallelGrid(1,8,1.5,0,Set.of(),new Vec3(0,3,0)),Set.of("laser"),
                new Pattern.Style("laser","red",.12,1,.12)),new Paths.Hold(),PERIOD,0,0,List.of(),damage(PERIOD),rotate,
                Actions.DirectionRule.FIXED,0,new CollisionProfile.Laser(20,false,10)));
        return List.copyOf(result);
    }
    private static Presentation.Profile damage(int lifetime) {
        return new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,lifetime)),null,null,List.of());
    }
}
