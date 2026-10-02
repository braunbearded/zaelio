package com.zaelio.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

final class OverviewOptions {
    static final String[] SORT_LABELS = {"Manuelle Reihenfolge", "Neueste zuerst", "Älteste zuerst", "Trackername A–Z"};
    static final String[] PERIOD_LABELS = {"Alle", "7 Tage", "30 Tage", "Eigener Zeitraum"};
    static final int MANUAL = 0;
    static final int ALL = 0;
    static final int CUSTOM = 3;

    final Set<Long> trackerIds = new HashSet<>();
    int sort;
    int period;
    // MaterialDatePicker represents calendar dates at midnight UTC, not session timestamps.
    long fromDay;
    long toDay;
    private final SharedPreferences prefs;
    private final String key;

    OverviewOptions(Context context, String key) {
        this.prefs = context.getSharedPreferences("overview_options", Context.MODE_PRIVATE);
        this.key = key;
        sort = validIndex(prefs.getInt(key + ".sort", 0), SORT_LABELS.length);
        period = validIndex(prefs.getInt(key + ".period", 0), PERIOD_LABELS.length);
        fromDay = prefs.getLong(key + ".from", utcDay(System.currentTimeMillis()));
        toDay = prefs.getLong(key + ".to", fromDay);
        for (String id : prefs.getStringSet(key + ".trackers", new HashSet<>())) {
            try {
                trackerIds.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
            }
        }
        if (fromDay > toDay) {
            period = ALL;
        }
    }

    private OverviewOptions(OverviewOptions source) {
        prefs = source.prefs;
        key = source.key;
        copyFrom(source);
    }

    OverviewOptions copy() {
        return new OverviewOptions(this);
    }

    void copyFrom(OverviewOptions source) {
        trackerIds.clear();
        trackerIds.addAll(source.trackerIds);
        sort = source.sort;
        period = source.period;
        fromDay = source.fromDay;
        toDay = source.toDay;
    }

    void save() {
        Set<String> ids = new HashSet<>();
        for (long id : trackerIds) {
            ids.add(String.valueOf(id));
        }
        prefs.edit().putStringSet(key + ".trackers", ids).putInt(key + ".sort", sort)
                .putInt(key + ".period", period).putLong(key + ".from", fromDay)
                .putLong(key + ".to", toDay).apply();
    }

    void reset() {
        trackerIds.clear();
        period = ALL;
        sort = MANUAL;
    }

    int filterCount() {
        return trackerIds.size() + (period == ALL ? 0 : 1);
    }

    boolean canReorder() {
        return sort == MANUAL && filterCount() == 0;
    }

    boolean validDates() {
        return period != CUSTOM || fromDay <= toDay;
    }

    boolean matches(Session session, long now) {
        return (trackerIds.isEmpty() || trackerIds.contains(session.trackerId)) && matchesDate(session.createdAt, now);
    }

    boolean matchesDate(long timestamp, long now) {
        return matchesDate(timestamp, now, period);
    }

    private boolean matchesDate(long timestamp, long now, int period) {
        if (period == ALL) {
            return true;
        }
        Calendar start = localMidnight(period == CUSTOM ? fromDay : utcDay(now));
        Calendar end = localMidnight(period == CUSTOM ? toDay : utcDay(now));
        if (period != CUSTOM) {
            start.add(Calendar.DATE, period == 1 ? -6 : -29);
        }
        end.add(Calendar.DATE, 1);
        return timestamp >= start.getTimeInMillis() && timestamp < end.getTimeInMillis();
    }

    List<Session> sessions(List<Session> source, Map<Long, Tracker> trackers, long now, Locale locale) {
        List<Session> result = new ArrayList<>();
        for (Session session : source) {
            if (trackers.containsKey(session.trackerId) && matches(session, now)) {
                result.add(session);
            }
        }
        if (sort == 1 || sort == 2) {
            Collections.sort(result, (a, b) -> compareDates(a.createdAt, a.id, b.createdAt, b.id));
        } else if (sort == 3) {
            Collator collator = Collator.getInstance(locale);
            Collections.sort(result, (a, b) -> collator.compare(name(trackers.get(a.trackerId)), name(trackers.get(b.trackerId))));
        }
        return result;
    }

    List<Tracker> trackers(List<Tracker> source, List<Session> sessions, long now, Locale locale) {
        Set<Long> inPeriod = new HashSet<>();
        if (period != ALL) {
            for (Session session : sessions) {
                if (matchesDate(session.createdAt, now)) {
                    inPeriod.add(session.trackerId);
                }
            }
        }
        List<Tracker> result = new ArrayList<>();
        for (Tracker tracker : source) {
            if ((trackerIds.isEmpty() || trackerIds.contains(tracker.id)) && (period == ALL || inPeriod.contains(tracker.id))) {
                result.add(tracker);
            }
        }
        if (sort == 1 || sort == 2) {
            Collections.sort(result, (a, b) -> compareDates(a.createdAt, a.id, b.createdAt, b.id));
        } else if (sort == 3) {
            Collator collator = Collator.getInstance(locale);
            Collections.sort(result, (a, b) -> collator.compare(name(a), name(b)));
        }
        return result;
    }

    static List<Tracker> availableTrackers(List<Tracker> trackers, List<Session> sessions, boolean sessionsTab) {
        Set<Long> withSessions = new HashSet<>();
        for (Session session : sessions) {
            withSessions.add(session.trackerId);
        }
        List<Tracker> result = new ArrayList<>();
        for (Tracker tracker : trackers) {
            if (!sessionsTab || withSessions.contains(tracker.id)) {
                result.add(tracker);
            }
        }
        return result;
    }

    boolean reconcile(List<Tracker> available, List<Session> sessions, long now) {
        Set<Long> ids = new HashSet<>();
        for (Tracker tracker : available) {
            ids.add(tracker.id);
        }
        boolean changed = trackerIds.retainAll(ids);
        int oldPeriod = period;
        long oldFrom = fromDay;
        long oldTo = toDay;
        long[] range = sessionRange(sessions);
        if (range == null) {
            period = ALL;
            fromDay = toDay = utcDay(now);
        } else {
            if (period == CUSTOM) {
                fromDay = Math.max(fromDay, range[0]);
                toDay = Math.min(toDay, range[1]);
                if (!validDates() || !hasSessionsInPeriod(CUSTOM, sessions, now)) {
                    period = ALL;
                }
            } else if (!periodAvailable(period, sessions, now)) {
                period = ALL;
            }
            if (period != CUSTOM) {
                fromDay = range[0];
                toDay = range[1];
            }
        }
        return changed || period != oldPeriod || fromDay != oldFrom || toDay != oldTo;
    }

    boolean periodAvailable(int period, List<Session> sessions, long now) {
        return period == ALL || (period == CUSTOM ? sessionRange(sessions) != null : hasSessionsInPeriod(period, sessions, now));
    }

    private boolean hasSessionsInPeriod(int period, List<Session> sessions, long now) {
        for (Session session : sessions) {
            if ((trackerIds.isEmpty() || trackerIds.contains(session.trackerId)) && matchesDate(session.createdAt, now, period)) {
                return true;
            }
        }
        return false;
    }

    void useSessionRange(List<Session> sessions) {
        long[] range = sessionRange(sessions);
        fromDay = range == null ? utcDay(System.currentTimeMillis()) : range[0];
        toDay = range == null ? fromDay : range[1];
    }

    long[] sessionRange(List<Session> sessions) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (Session session : sessions) {
            if (trackerIds.isEmpty() || trackerIds.contains(session.trackerId)) {
                min = Math.min(min, session.createdAt);
                max = Math.max(max, session.createdAt);
            }
        }
        return min == Long.MAX_VALUE ? null : new long[]{utcDay(min), utcDay(max)};
    }

    static long utcDay(long timestamp) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(timestamp);
        return utcDay(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DATE));
    }

    static long utcDay(int year, int month, int day) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month, day);
        return utc.getTimeInMillis();
    }

    private static Calendar localMidnight(long utcDay) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcDay);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DATE));
        return local;
    }

    static String displayDay(long utcDay) {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(utcDay);
    }

    private int compareDates(long aDate, long aId, long bDate, long bId) {
        int comparison = Long.compare(aDate, bDate);
        if (comparison == 0) {
            comparison = Long.compare(aId, bId);
        }
        return sort == 1 ? -comparison : comparison;
    }

    private static String name(Tracker tracker) {
        return tracker.name == null ? "" : tracker.name;
    }

    private static int validIndex(int index, int count) {
        return index >= 0 && index < count ? index : 0;
    }
}
