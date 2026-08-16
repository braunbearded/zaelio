package com.zaelio.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatUtilTest {
    @Test
    public void formatMsUsesHoursMinutesSeconds() {
        assertEquals("01:02:03", FormatUtil.formatMs(3_723_000));
    }

    @Test
    public void numericParsingFallsBackSafely() {
        assertEquals(42L, FormatUtil.toLong("42"));
        assertEquals(0L, FormatUtil.toLong("nope"));
        assertEquals(2.5, FormatUtil.parseDouble("2.5", 1), 0.0001);
        assertEquals(1.0, FormatUtil.parseDouble("nope", 1), 0.0001);
    }
}
