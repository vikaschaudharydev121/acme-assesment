package com.acme.salary.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryStatsTest {

    @Test
    @DisplayName("empty input yields all-zero stats rather than failing")
    void emptyInput() {
        assertThat(SalaryStats.of(new long[0])).isEqualTo(SalaryStats.EMPTY);
    }

    @Test
    void singleValueIsEveryStatistic() {
        SalaryStats stats = SalaryStats.of(new long[]{50_000});

        assertThat(stats).isEqualTo(new SalaryStats(1, 50_000, 50_000, 50_000, 50_000, 50_000, 50_000, 50_000));
    }

    @Test
    void oddCountMedianIsMiddleValueRegardlessOfInputOrder() {
        SalaryStats stats = SalaryStats.of(new long[]{90, 10, 50, 30, 70});

        assertThat(stats.median()).isEqualTo(50);
        assertThat(stats.min()).isEqualTo(10);
        assertThat(stats.max()).isEqualTo(90);
        assertThat(stats.total()).isEqualTo(250);
        assertThat(stats.mean()).isEqualTo(50);
    }

    @Test
    void evenCountMedianAveragesTheTwoMiddleValues() {
        assertThat(SalaryStats.of(new long[]{100, 200, 300, 400}).median()).isEqualTo(250);
    }

    @Test
    @DisplayName("quartiles match Excel PERCENTILE.INC: {1,2,3,4} -> Q1 = 1.75, Q3 = 3.25")
    void quartilesMatchExcel() {
        long[] sorted = {1, 2, 3, 4};

        assertThat(SalaryStats.percentile(sorted, 0.25)).isEqualTo(1.75);
        assertThat(SalaryStats.percentile(sorted, 0.75)).isEqualTo(3.25);
        assertThat(SalaryStats.percentile(sorted, 0.0)).isEqualTo(1.0);
        assertThat(SalaryStats.percentile(sorted, 1.0)).isEqualTo(4.0);
    }

    @Test
    void medianIsRobustToAnOutlierWhileMeanIsNot() {
        SalaryStats stats = SalaryStats.of(new long[]{60_000, 62_000, 64_000, 66_000, 1_000_000});

        assertThat(stats.median()).isEqualTo(64_000);
        assertThat(stats.mean()).isEqualTo(250_400);
    }

    @Test
    void doesNotMutateCallerArray() {
        long[] input = {3, 1, 2};

        SalaryStats.of(input);

        assertThat(input).containsExactly(3, 1, 2);
    }
}
