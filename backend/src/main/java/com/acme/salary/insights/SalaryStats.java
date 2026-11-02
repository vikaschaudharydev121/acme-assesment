package com.acme.salary.insights;

import java.util.Arrays;

/**
 * Descriptive statistics for a set of annual salaries, rounded to whole currency units.
 * Percentiles use linear interpolation between closest ranks — identical to Excel's
 * PERCENTILE.INC / MEDIAN — so HR can reconcile figures against their existing spreadsheets.
 */
public record SalaryStats(int count, long total, long min, long p25, long median, long p75, long max, long mean) {

    public static final SalaryStats EMPTY = new SalaryStats(0, 0, 0, 0, 0, 0, 0, 0);

    public static SalaryStats of(long[] values) {
        if (values.length == 0) {
            return EMPTY;
        }
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        long total = 0;
        for (long v : sorted) {
            total = Math.addExact(total, v);
        }
        return new SalaryStats(
                sorted.length,
                total,
                sorted[0],
                Math.round(percentile(sorted, 0.25)),
                Math.round(percentile(sorted, 0.50)),
                Math.round(percentile(sorted, 0.75)),
                sorted[sorted.length - 1],
                Math.round((double) total / sorted.length));
    }

    /** @param sorted ascending, non-empty; @param p in [0, 1] */
    static double percentile(long[] sorted, double p) {
        double rank = p * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        double fraction = rank - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }
}
