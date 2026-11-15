package com.example.async;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Spring cron has SIX fields: second minute hour day-of-month month day-of-week.
// CronExpression.next() tells you exactly when a schedule fires - test your crons instead of guessing.
class CronExpressionTest {

    private final LocalDateTime saturdayNoon = LocalDateTime.of(2026, 11, 14, 12, 0);

    @Test
    void weekdaysAtTwoAm_skipsTheWeekend() {
        CronExpression cron = CronExpression.parse("0 0 2 * * MON-FRI");

        assertThat(cron.next(saturdayNoon)).isEqualTo(LocalDateTime.of(2026, 11, 16, 2, 0));   // Monday
    }

    @Test
    void unixStyleFiveFieldCron_isRejected() {
        // Copied from a Unix crontab, where it means "at 2:00 every day". Spring cron starts with a
        // SECONDS field and needs six fields, so this fails at startup. The Spring version: "0 0 2 * * *".
        assertThatThrownBy(() -> CronExpression.parse("0 2 * * *"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyFifteenMinutes() {
        CronExpression cron = CronExpression.parse("0 */15 * * * *");

        assertThat(cron.next(LocalDateTime.of(2026, 11, 16, 9, 7))).isEqualTo(LocalDateTime.of(2026, 11, 16, 9, 15));
    }
}
