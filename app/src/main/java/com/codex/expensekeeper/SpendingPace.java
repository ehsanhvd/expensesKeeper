package com.codex.expensekeeper;

/** Pure calculation; each bucket is a calendar day ending at the current local time. */
final class SpendingPace {
    static final int LEARNING = -1, GREEN = 0, STEADY = 1, HIGH = 2;
    final double dailyAverage, usualDaily;
    final int baselineWeeks, state;
    final float needle;

    SpendingPace(long[] days, int historyDays) {
        baselineWeeks = Math.max(0, Math.min(8, (historyDays - 7) / 7));
        double recent = 0, baseline = 0;
        for (int i = 0; i < 7; i++) recent += days[i];
        for (int i = 7; i < 7 + baselineWeeks * 7; i++) baseline += days[i];
        dailyAverage = recent / 7;
        usualDaily = baselineWeeks == 0 ? 0 : baseline / (baselineWeeks * 7);
        if (baselineWeeks < 4 || usualDaily <= 0) {
            state = LEARNING;
            needle = .5f;
        } else {
            double ratio = dailyAverage / usualDaily;
            // Green rewards a modest reduction; amber leaves room for normal variation.
            state = ratio <= .95 ? GREEN : ratio < 1.15 ? STEADY : HIGH;
            // Match the needle's color sector to the background's thresholds.
            double position = ratio <= .95 ? ratio / .95 / 3
                    : ratio < 1.15 ? 1d / 3 + (ratio - .95) / .20 / 3
                    : 2d / 3 + (ratio - 1.15) / .85 / 3;
            needle = (float) Math.max(0, Math.min(1, position));
        }
    }

    long percentChange() {
        return usualDaily <= 0 ? 0 : Math.round(Math.abs(dailyAverage / usualDaily - 1) * 100);
    }
}
