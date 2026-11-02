package com.bitsson.gensokyou.balance;

/** Compatibility adapter for calibration reports; numeric truth is production BuildNumbers. */
final class BuildBudgetCandidate {
    static double power(int t) { return BuildNumbers.power(t); }
    static double pool(int t) { return BuildNumbers.pool(t); }
    static double health(int t) { return BuildNumbers.health(t); }
    static double ward(int t) { return BuildNumbers.ward(t); }
    static double window(int t) { return BuildNumbers.window(t); }
    static double sphereCost(int t) { return BuildNumbers.sphereCost(t); }
    static double bulletCost(String k, int t) { return BuildNumbers.bulletCost(k, t); }
    static double cap(String k, int t) { return BuildNumbers.cap(k, t); }
    static double innateMaximum(String k, int t) { return BuildNumbers.innateMaximum(k, t); }
    static double innateMean(String k, int t) { return BuildNumbers.innateMean(k, t); }
    static double coreMaximum(String k, int t) { return BuildNumbers.coreMaximum(k, t); }
    static String[] keys() { return BuildNumbers.keys(); }
    static double coreScore(java.util.Map<String, Double> v, int t) { return BuildNumbers.coreScore(v, t); }
    static double coreBudget(int t) { return BuildNumbers.coreBudget(t); }
    static boolean legalCore(java.util.Map<String, Double> v, int t) { return BuildNumbers.legalCore(v, t); }
    static int coreTier(int t) { return BuildNumbers.coreTier(t); }
    static double spellBaseCost(String k, int t) { return BuildNumbers.spellBaseCost(k, t); }
    static double foxDamagePerSecond(int t) { return BuildNumbers.foxDamagePerSecond(t); }
    static double foxDuration(int t) { return BuildNumbers.foxDuration(t); }
}
