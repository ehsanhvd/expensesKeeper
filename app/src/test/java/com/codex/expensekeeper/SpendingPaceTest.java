package com.codex.expensekeeper;

import java.util.Arrays;

/** Standalone regression checks: run with javac/java, no Android runtime needed. */
public final class SpendingPaceTest {
    public static void main(String[] args) {
        long[] days = new long[63];
        check(new SpendingPace(days, 63).state == SpendingPace.LEARNING, "empty history");
        Arrays.fill(days, 100);
        check(new SpendingPace(days, 34).state == SpendingPace.LEARNING, "short history");
        SpendingPace steady = new SpendingPace(days, 35);
        check(steady.state == SpendingPace.STEADY && steady.baselineWeeks == 4, "first baseline");
        check(steady.dailyAverage == 100 && steady.usualDaily == 100, "daily averages");
        check(new SpendingPace(days, 100).baselineWeeks == 8, "history cap");
        Arrays.fill(days, 0, 7, 95);
        SpendingPace green = new SpendingPace(days, 63);
        check(green.state == SpendingPace.GREEN && green.percentChange() == 5, "reduction goal");
        check(green.needle <= 1f / 3f, "green gauge alignment");
        Arrays.fill(days, 0, 7, 115);
        SpendingPace high = new SpendingPace(days, 63);
        check(high.state == SpendingPace.HIGH && high.needle >= 2f / 3f, "high threshold");
        Arrays.fill(days, 0, 7, 1000);
        check(new SpendingPace(days, 63).needle == 1, "large purchase cap");
        Arrays.fill(days, 0, 7, 0);
        check(new SpendingPace(days, 63).needle == 0, "zero recent spending");
        Arrays.fill(days, 0);
        days[0] = 700;
        check(new SpendingPace(days, 63).state == SpendingPace.LEARNING, "zero baseline");
        Arrays.fill(days, 100);
        days[0] = 800;
        check(new SpendingPace(days, 63).usualDaily == 100, "recent purchases excluded from baseline");
        System.out.println("SpendingPace checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
