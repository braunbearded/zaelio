package com.zaelio.app;

import static org.junit.Assert.*;

import androidx.test.core.app.ApplicationProvider;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OverviewOptionsTest {
    private OverviewOptions options;
    private TimeZone oldZone;

    @Before
    public void setUp() {
        ApplicationProvider.getApplicationContext().getSharedPreferences("overview_options", 0).edit().clear().commit();
        options = new OverviewOptions(ApplicationProvider.getApplicationContext(), "sessions");
        oldZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(oldZone);
    }

    @Test
    @Config(sdk = {23, 34})
    public void trackerMultiSelectionAndEverySortPreserveSourceOrder() {
        Tracker a = tracker(1, "Alpha", 100);
        Tracker z = tracker(2, "Zulu", 200);
        Map<Long, Tracker> trackers = new HashMap<>();
        trackers.put(a.id, a);
        trackers.put(z.id, z);
        Session first = session(10, a.id, 10);
        Session second = session(20, z.id, 20);
        Session last = session(30, a.id, 30);
        List<Session> manual = Arrays.asList(second, last, first);
        assertEquals(manual, options.sessions(manual, trackers, 0, Locale.GERMAN));
        options.sort = 1;
        assertEquals(Arrays.asList(last, second, first), options.sessions(manual, trackers, 0, Locale.GERMAN));
        options.sort = 2;
        assertEquals(Arrays.asList(first, second, last), options.sessions(manual, trackers, 0, Locale.GERMAN));
        options.sort = 3;
        assertEquals(Arrays.asList(last, first, second), options.sessions(manual, trackers, 0, Locale.GERMAN));
        options.trackerIds.add(a.id);
        assertEquals(Arrays.asList(last, first), options.sessions(manual, trackers, 0, Locale.GERMAN));
        options.trackerIds.add(z.id);
        assertEquals(3, options.sessions(manual, trackers, 0, Locale.GERMAN).size());
        options.trackerIds.clear();
        options.sort = 0;
        assertTrue(options.canReorder());
        assertEquals(Arrays.asList(second, last, first), manual);
        List<Tracker> trackerManual = Arrays.asList(z, a);
        assertEquals(trackerManual, options.trackers(trackerManual, manual, 0, Locale.GERMAN));
        options.sort = 1;
        assertEquals(Arrays.asList(z, a), options.trackers(trackerManual, manual, 0, Locale.GERMAN));
        options.sort = 2;
        assertEquals(Arrays.asList(a, z), options.trackers(trackerManual, manual, 0, Locale.GERMAN));
        options.sort = 3;
        assertEquals(Arrays.asList(a, z), options.trackers(trackerManual, manual, 0, Locale.GERMAN));
    }

    @Test
    public void customDatesIncludeWholeLocalDaysEvenAcrossDstAndTimeZones() {
        for (String zone : new String[]{"Europe/Berlin", "America/Los_Angeles", "UTC"}) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            options.period = OverviewOptions.CUSTOM;
            options.fromDay = options.toDay = OverviewOptions.utcDay(2026, Calendar.MARCH, 29);
            long start = local(2026, Calendar.MARCH, 29, 0);
            long nextDay = local(2026, Calendar.MARCH, 30, 0);
            assertFalse(options.matchesDate(start - 1, start));
            assertTrue(options.matchesDate(start, start));
            assertTrue(options.matchesDate(nextDay - 1, start));
            assertFalse(options.matchesDate(nextDay, start));
            assertEquals("29.03.2026", OverviewOptions.displayDay(options.fromDay));
        }
    }

    @Test
    public void recentPresetsIncludeTodayAndDoNotIncludeFutureOrExtraDay() {
        long now = local(2026, Calendar.OCTOBER, 2, 12);
        for (int period : new int[]{1, 2}) {
            options.period = period;
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(local(2026, Calendar.OCTOBER, 2, 0));
            start.add(Calendar.DATE, period == 1 ? -6 : -29);
            assertTrue(options.matchesDate(start.getTimeInMillis(), now));
            assertFalse(options.matchesDate(start.getTimeInMillis() - 1, now));
            assertTrue(options.matchesDate(local(2026, Calendar.OCTOBER, 3, 0) - 1, now));
            assertFalse(options.matchesDate(local(2026, Calendar.OCTOBER, 3, 0), now));
        }
    }

    @Test
    public void trackerPeriodAndInitialCustomRangeUseSessionsNotTrackerCreationDates() {
        Tracker old = tracker(1, "Old tracker", 0);
        Tracker newer = tracker(2, "New tracker", local(2026, Calendar.SEPTEMBER, 1, 0));
        Session first = session(1, old.id, local(2026, Calendar.SEPTEMBER, 1, 12));
        Session last = session(2, old.id, local(2026, Calendar.SEPTEMBER, 30, 23));
        options.useSessionRange(Arrays.asList(first, last));
        assertEquals("01.09.2026", OverviewOptions.displayDay(options.fromDay));
        assertEquals("30.09.2026", OverviewOptions.displayDay(options.toDay));
        options.period = OverviewOptions.CUSTOM;
        assertEquals(Collections.singletonList(old), options.trackers(Arrays.asList(newer, old),
                Arrays.asList(first, last), last.createdAt, Locale.GERMAN));
        assertEquals(1, options.filterCount());
        assertFalse(options.canReorder());
    }

    @Test
    public void draftsPersistPerTabOnlyOnSaveAndInvalidDatesPreventApply() {
        OverviewOptions draft = options.copy();
        draft.trackerIds.add(7L);
        draft.sort = 1;
        draft.period = OverviewOptions.CUSTOM;
        draft.fromDay = OverviewOptions.utcDay(2026, Calendar.SEPTEMBER, 1);
        draft.toDay = OverviewOptions.utcDay(2026, Calendar.SEPTEMBER, 30);
        assertEquals(0, options.filterCount());
        assertEquals(0, new OverviewOptions(ApplicationProvider.getApplicationContext(), "sessions").filterCount());
        options.copyFrom(draft);
        options.save();
        OverviewOptions restored = new OverviewOptions(ApplicationProvider.getApplicationContext(), "sessions");
        assertEquals(draft.trackerIds, restored.trackerIds);
        assertEquals(draft.fromDay, restored.fromDay);
        assertEquals(draft.toDay, restored.toDay);
        assertEquals(1, restored.sort);
        assertEquals(2, restored.filterCount());
        assertEquals(0, new OverviewOptions(ApplicationProvider.getApplicationContext(), "trackers").filterCount());
        draft.fromDay = draft.toDay + 1;
        assertFalse(draft.validDates());
        draft.reset();
        assertTrue(draft.validDates());
        assertTrue(draft.canReorder());
        assertEquals(0, draft.filterCount());
    }

    @Test
    public void reconciliationRemovesSessionlessTrackerChoicesAndStalePeriodsWithoutChangingSort() {
        long now = local(2026, Calendar.OCTOBER, 2, 12);
        Tracker training = tracker(1, "Training", 0);
        Tracker reading = tracker(2, "Lesen", 0);
        Tracker unused = tracker(3, "Ohne Sessions", 0);
        Session a = session(1, training.id, local(2026, Calendar.SEPTEMBER, 1, 12));
        Session b = session(2, reading.id, local(2026, Calendar.SEPTEMBER, 10, 12));
        List<Tracker> trackers = Arrays.asList(training, reading, unused);
        assertEquals(Arrays.asList(training, reading), OverviewOptions.availableTrackers(trackers, Arrays.asList(a, b), true));
        assertEquals(trackers, OverviewOptions.availableTrackers(trackers, Arrays.asList(a, b), false));
        options.trackerIds.add(reading.id);
        options.period = OverviewOptions.CUSTOM;
        options.fromDay = options.toDay = OverviewOptions.utcDay(b.createdAt);
        options.sort = 3;
        List<Tracker> remainingChoices = OverviewOptions.availableTrackers(trackers, Collections.singletonList(a), true);
        assertTrue(options.reconcile(remainingChoices, Collections.singletonList(a), now));
        assertTrue(options.trackerIds.isEmpty());
        assertEquals(OverviewOptions.ALL, options.period);
        assertEquals(3, options.sort);
        assertArrayEquals(new long[]{OverviewOptions.utcDay(a.createdAt), OverviewOptions.utcDay(a.createdAt)},
                options.sessionRange(Collections.singletonList(a)));
        options.save();
        OverviewOptions restored = new OverviewOptions(ApplicationProvider.getApplicationContext(), "sessions");
        assertEquals(0, restored.filterCount());
        assertEquals(3, restored.sort);
        assertFalse(options.reconcile(remainingChoices, Collections.singletonList(a), now));
    }

    @Test
    public void selectedTrackersDetermineAvailablePresetsAndClampedCustomDateBounds() {
        long now = local(2026, Calendar.OCTOBER, 2, 12);
        Tracker old = tracker(1, "Old", 0);
        Tracker recent = tracker(2, "Recent", 0);
        Session oldSession = session(1, old.id, local(2026, Calendar.AUGUST, 1, 12));
        Session recentSession = session(2, recent.id, local(2026, Calendar.OCTOBER, 2, 10));
        List<Session> sessions = Arrays.asList(oldSession, recentSession);
        List<Tracker> trackers = Arrays.asList(old, recent);
        assertTrue(options.periodAvailable(1, sessions, now));
        assertTrue(options.periodAvailable(2, sessions, now));
        options.trackerIds.add(old.id);
        assertFalse(options.periodAvailable(1, sessions, now));
        assertFalse(options.periodAvailable(2, sessions, now));
        assertTrue(options.periodAvailable(OverviewOptions.CUSTOM, sessions, now));
        options.period = OverviewOptions.CUSTOM;
        options.fromDay = OverviewOptions.utcDay(2026, Calendar.JANUARY, 1);
        options.toDay = OverviewOptions.utcDay(2026, Calendar.DECEMBER, 31);
        assertTrue(options.reconcile(trackers, sessions, now));
        assertEquals(OverviewOptions.utcDay(oldSession.createdAt), options.fromDay);
        assertEquals(options.fromDay, options.toDay);
        options.period = 1;
        assertTrue(options.reconcile(trackers, sessions, now));
        assertEquals(OverviewOptions.ALL, options.period);
        assertTrue(options.reconcile(Collections.emptyList(), Collections.emptyList(), now));
        assertFalse(options.periodAvailable(OverviewOptions.CUSTOM, Collections.emptyList(), now));
        assertTrue(options.periodAvailable(OverviewOptions.ALL, Collections.emptyList(), now));
        assertEquals(0, options.filterCount());
    }

    private Tracker tracker(long id, String name, long createdAt) {
        Tracker tracker = new Tracker();
        tracker.id = id;
        tracker.name = name;
        tracker.createdAt = createdAt;
        return tracker;
    }

    private Session session(long id, long trackerId, long createdAt) {
        Session session = new Session();
        session.id = id;
        session.trackerId = trackerId;
        session.createdAt = createdAt;
        return session;
    }

    private long local(int year, int month, int day, int hour) {
        Calendar date = Calendar.getInstance();
        date.clear();
        date.set(year, month, day, hour, 0);
        return date.getTimeInMillis();
    }
}
