package com.bitsson.gensokyou.danmaku.program.math;

import java.util.List;

/** Piecewise linear scalar function with closed-form integral and held endpoint values. */
public record ScalarCurve(List<Key> keys) {
    public record Key(double tick, double value) {
        public Key { Deterministic.nonnegative(tick); Deterministic.finite(value); }
    }
    public ScalarCurve {
        keys = List.copyOf(keys);
        if (keys.isEmpty() || keys.size() > 64 || keys.getFirst().tick() != 0) throw new IllegalArgumentException("1..64 keys starting at zero required");
        for (int i=1; i<keys.size(); i++) if (keys.get(i).tick() <= keys.get(i-1).tick()) throw new IllegalArgumentException("ordered distinct ticks required");
    }
    public static ScalarCurve constant(double value) { return new ScalarCurve(List.of(new Key(0,value))); }
    public double at(double tick) {
        Deterministic.nonnegative(tick);
        for (int i=1; i<keys.size(); i++) {
            Key a=keys.get(i-1), b=keys.get(i);
            if (tick < b.tick()) return a.value()+(b.value()-a.value())*(tick-a.tick())/(b.tick()-a.tick());
        }
        return keys.getLast().value();
    }
    public double integral(double tick) {
        Deterministic.nonnegative(tick);
        double area=0;
        for (int i=1; i<keys.size(); i++) {
            Key a=keys.get(i-1), b=keys.get(i);
            double dt=StrictMath.min(tick,b.tick())-a.tick();
            if (dt <= 0) return area;
            area += a.value()*dt+(b.value()-a.value())*dt*dt/(2*(b.tick()-a.tick()));
            if (tick <= b.tick()) return area;
        }
        return area+keys.getLast().value()*StrictMath.max(0,tick-keys.getLast().tick());
    }
    public double maxAbs() { return keys.stream().mapToDouble(k -> StrictMath.abs(k.value())).max().orElseThrow(); }
}
