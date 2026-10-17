package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Shared low-level input, deliberately independent of either backend. */
public final class ProgramBenchmarkLoad {
    public static final int PERIOD=100, LIFETIME=100;
    public static final double SPEED=.08, RADIUS=.2;
    public static final long SEED=20261017L;
    private ProgramBenchmarkLoad() {}
    public static List<Actions.ChildProgram> batches(int count) {
        if (!Set.of(48,480,800,2000).contains(count)) throw new IllegalArgumentException("supported counts: 48/480/800/2000");
        var result=new ArrayList<Actions.ChildProgram>();
        for (int offset=0;offset<count;offset+=48) {
            int n=Math.min(48,count-offset);
            var pattern=new Patterns.Tagged(new Patterns.ParallelGrid(1,n,.45,0,Set.of(),new Vec3(0,(offset/48)*.45,0)),Set.of(),
                    new Pattern.Style("sphere","blue",RADIUS,1,RADIUS));
            result.add(new Actions.ChildProgram(pattern,new Paths.Linear(Vec3.Z,SPEED),LIFETIME,0,0,List.of(),
                    new Presentation.Profile(List.of(new Presentation.Window(com.bitsson.gensokyou.danmaku.program.TimeDomain.MEMBER_AGE,0,LIFETIME)),null,null,List.of()),new Transforms.Compose(List.of())));
        }
        return List.copyOf(result);
    }
}
