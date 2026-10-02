package com.zaelio.app;

import androidx.fragment.app.FragmentActivity;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

public final class HomeUi {
    private final FragmentActivity activity;
    private final TrackingDatabase db;
    private final ThemeStore theme;
    private final AppUi ui;
    private final LongConsumer openSession;
    private final LongConsumer editTracker;
    private final Runnable refresh;
    private final OverviewOptions sessionOptions;
    private final OverviewOptions trackerOptions;
    private final OverviewFilterUi filters;

    public HomeUi(FragmentActivity activity, TrackingDatabase db, ThemeStore theme, AppUi ui,
                  LongConsumer openSession, LongConsumer editTracker, Runnable refresh) {
        this.activity = activity;
        this.db = db;
        this.theme = theme;
        this.ui = ui;
        this.openSession = openSession;
        this.editTracker = editTracker;
        this.refresh = refresh;
        sessionOptions = new OverviewOptions(activity, "sessions");
        trackerOptions = new OverviewOptions(activity, "trackers");
        filters = new OverviewFilterUi(activity, theme, ui, refresh);
    }

    public void renderSessions(FrameLayout body) {
        List<Session> all = db.sessions();
        List<Tracker> trackers = OverviewOptions.availableTrackers(db.trackers(), all, true);
        reconcileFilters(sessionOptions, trackers, all);
        Map<Long, Tracker> byId = new HashMap<>();
        for (Tracker tracker : trackers) {
            byId.put(tracker.id, tracker);
        }
        List<Session> sessions = sessionOptions.sessions(all, byId, System.currentTimeMillis(), new Locale(theme.resolvedLanguage()));
        LinearLayout box = overviewList(body, true, sessionOptions, trackers, all, sessions.size(), all.size());
        for (Session session : sessions) {
            Tracker tracker = byId.get(session.trackerId);

            LinearLayout card = overviewCard(
                    tracker.name,
                    date(session.createdAt),
                    preview(session.id, tracker),
                    () -> openSession.accept(session.id),
                    null,
                    (restore, animateDelete) -> confirmDeleteSession(session, restore, animateDelete),
                    box,
                    sessionOptions.canReorder() ? () -> db.reorderSessions(childIds(box)) : null);
            card.setTag(session.id);
            box.addView(card, cardLayoutParams());
        }

        if (sessions.isEmpty()) {
            box.addView(emptyState(all.isEmpty() ? "Noch keine Sessions vorhanden" : "Keine passenden Einträge", null));
        }
    }

    public void renderTrackers(FrameLayout body) {
        List<Tracker> all = db.trackers();
        List<Session> sessions = db.sessions();
        reconcileFilters(trackerOptions, all, sessions);
        List<Tracker> trackers = trackerOptions.trackers(all, sessions, System.currentTimeMillis(), new Locale(theme.resolvedLanguage()));
        LinearLayout box = overviewList(body, false, trackerOptions, all, sessions, trackers.size(), all.size());
        for (Tracker tracker : trackers) {
            LinearLayout card = overviewCard(
                    tracker.name == null || tracker.name.trim().isEmpty() ? ui.t("Unbenannter Tracker") : tracker.name,
                    null,
                    fieldPreview(tracker),
                    () -> editTracker.accept(tracker.id),
                    () -> duplicateTracker(tracker),
                    (restore, animateDelete) -> confirmDeleteTracker(tracker, restore, animateDelete),
                    box,
                    trackerOptions.canReorder() ? () -> db.reorderTrackers(childIds(box)) : null);
            card.setTag(tracker.id);
            box.addView(card, cardLayoutParams());
        }

        if (trackers.isEmpty()) {
            box.addView(emptyState(all.isEmpty() ? "Noch keine Tracker vorhanden" : "Keine passenden Einträge", null));
        }
    }

    private void reconcileFilters(OverviewOptions options, List<Tracker> trackers, List<Session> sessions) {
        if (options.reconcile(trackers, sessions, System.currentTimeMillis())) {
            options.save();
        }
    }

    private LinearLayout overviewList(FrameLayout body, boolean sessionsTab, OverviewOptions options,
                                      List<Tracker> trackers, List<Session> sessions, int visible, int total) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(ui.spaceL(), ui.spaceM(), ui.spaceL(), ui.spaceS());
        header.addView(ui.text(sessionsTab ? "Sessions" : "Tracker", 24, theme.primaryTextColor(), true));
        header.addView(ui.metaText(String.format(new Locale(theme.resolvedLanguage()),
                ui.t(sessionsTab ? "%d von %d Sessions" : "%d von %d Trackern"), visible, total)));
        LinearLayout filterPanel = createCard();
        filterPanel.setPadding(ui.spaceM(), ui.spaceM(), ui.spaceM(), ui.spaceM());
        LinearLayout controls = new LinearLayout(activity);
        controls.setBaselineAligned(false);
        MaterialButton filter = (MaterialButton) ui.button(ui.t("Filter")
                + (options.filterCount() == 0 ? "" : " (" + options.filterCount() + ")"),
                theme.surfaceColor(), theme.accentColor(), theme.borderColor());
        filter.setIcon(activity.getDrawable(R.drawable.ic_filter_24));
        filter.setIconTint(android.content.res.ColorStateList.valueOf(theme.accentColor()));
        filter.setOnClickListener(v -> filters.show(options, sessionsTab, trackers, sessions));
        filter.setPadding(ui.spaceS(), 0, ui.spaceS(), 0);
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(0, -1, 1);
        filterLp.rightMargin = ui.spaceS();
        controls.addView(filter, filterLp);
        MaterialButton sort = (MaterialButton) ui.button(OverviewOptions.SORT_LABELS[options.sort],
                theme.surfaceColor(), theme.accentColor(), theme.borderColor());
        sort.setIcon(activity.getDrawable(R.drawable.ic_expand_more_24));
        sort.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
        sort.setIconTint(android.content.res.ColorStateList.valueOf(theme.accentColor()));
        sort.setOnClickListener(v -> {
            AppUi.ActionItem[] items = new AppUi.ActionItem[OverviewOptions.SORT_LABELS.length];
            for (int i = 0; i < items.length; i++) {
                final int index = i;
                items[i] = ui.action(OverviewOptions.SORT_LABELS[i], () -> {
                    options.sort = index;
                    options.save();
                    refresh.run();
                });
            }
            ui.showActionMenu("Sortierung", items);
        });
        sort.setPadding(ui.spaceS(), 0, ui.spaceS(), 0);
        controls.addView(sort, new LinearLayout.LayoutParams(0, -1, 1));
        filterPanel.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        ChipGroup chips = new ChipGroup(activity);
        chips.setChipSpacing(ui.spaceS());
        for (Tracker tracker : trackers) {
            if (options.trackerIds.contains(tracker.id)) {
                String name = tracker.name == null || tracker.name.trim().isEmpty() ? ui.t("Unbenannter Tracker") : tracker.name;
                chips.addView(ui.filterChip(name, () -> {
                    options.trackerIds.remove(tracker.id);
                    options.save();
                    refresh.run();
                }));
            }
        }
        if (options.period != OverviewOptions.ALL) {
            String label = options.period == OverviewOptions.CUSTOM
                    ? OverviewOptions.displayDay(options.fromDay) + "–" + OverviewOptions.displayDay(options.toDay)
                    : ui.t(OverviewOptions.PERIOD_LABELS[options.period]);
            chips.addView(ui.filterChip(label, () -> {
                options.period = OverviewOptions.ALL;
                options.save();
                refresh.run();
            }));
        }
        if (chips.getChildCount() > 0) {
            LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(-1, -2);
            chipsLp.topMargin = ui.spaceM();
            filterPanel.addView(chips, chipsLp);
        }
        layout.addView(header);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, -2);
        panelLp.leftMargin = panelLp.rightMargin = ui.spaceL();
        panelLp.topMargin = panelLp.bottomMargin = ui.spaceXs();
        layout.addView(filterPanel, panelLp);
        ScrollView scroll = createScrollView();
        LinearLayout box = createListBox(scroll);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(layout, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }

    private ScrollView createScrollView() {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        return scrollView;
    }

    private LinearLayout createListBox(ScrollView scrollView) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ui.spaceL(), ui.spaceM(), ui.spaceL(), ui.bottomSafePadding());
        scrollView.addView(box);
        return box;
    }

    private LinearLayout overviewCard(String title, String meta, String previewText, Runnable open,
                                      Runnable duplicateAction, BiConsumer<Runnable, Runnable> deleteAction,
                                      LinearLayout reorderContainer, Runnable onReorder) {
        LinearLayout card = createCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        final boolean[] skipClick = new boolean[1];
        card.setOnClickListener(v -> {
            if (skipClick[0]) {
                skipClick[0] = false;
                return;
            }
            open.run();
        });
        DeleteGestureHelper.attach(activity, theme, ui, card, card, deleteAction, skipClick);

        TextView handle = onReorder == null ? null : ui.listIcon("⠿");
        if (handle != null) {
            handle.setContentDescription(ui.t("Verschieben"));
        }

        LinearLayout content = ui.twoLineText(ui.titleText(title), meta == null || meta.isEmpty() ? null : ui.metaText(meta));

        TextView preview = ui.text(previewText, 14, theme.primaryTextColor(), false);
        preview.setLineSpacing(0f, 1.15f);
        preview.setMaxLines(2);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(preview);

        TextView menu = ui.listIcon("...");
        menu.setOnClickListener(v -> showCardMenu(menu, duplicateAction, () -> deleteAction.accept(null, () -> DeleteGestureHelper.animateDelete(ui, card))));

        TextView arrow = ui.listIcon("›");
        arrow.setOnClickListener(v -> open.run());

        card.addView(ui.listRow(handle, content, menu, arrow), new LinearLayout.LayoutParams(-1, -2));
        if (handle != null) {
            ReorderHelper.attach(ui, handle, reorderContainer, card, onReorder);
        }
        return card;
    }

    private LinearLayout createCard() {
        LinearLayout card = ui.compactCard();
        card.setBackground(ui.makeRoundedCard(theme.surfaceColor(), theme.accentSoftColor()));
        return card;
    }

    private void showCardMenu(View anchor, Runnable duplicate, Runnable delete) {
        if (duplicate == null) {
            ui.showActionMenu("Aktionen", ui.action("Löschen", delete));
            return;
        }
        ui.showActionMenu("Aktionen", ui.action("Duplizieren", duplicate), ui.action("Löschen", delete));
    }

    private LinearLayout.LayoutParams cardLayoutParams() {
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = ui.spaceMl();
        return cardLp;
    }

    private LinearLayout emptyState(String titleText, String bodyText) {
        LinearLayout empty = ui.altCard();

        TextView emptyTitle = ui.tv(titleText, 18);
        emptyTitle.setPadding(0, 0, 0, ui.spaceXs());
        empty.addView(emptyTitle);

        if (bodyText != null && !bodyText.isEmpty()) {
            TextView emptyBody = ui.bodyText(bodyText);
            empty.addView(emptyBody);
        }

        LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(-1, -2);
        emptyLp.topMargin = ui.spaceXs();
        empty.setLayoutParams(emptyLp);
        return empty;
    }

    private java.util.List<Long> childIds(LinearLayout container) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            Object tag = container.getChildAt(i).getTag();
            if (tag instanceof Long) {
                ids.add((Long) tag);
            }
        }
        return ids;
    }

    private void confirmDeleteSession(Session session, Runnable restore, Runnable animateDelete) {
        DeleteGestureHelper.runDelete(activity, ui, "Session löschen", "Diese Session wirklich löschen?",
                restore, animateDelete, () -> {
                    db.deleteSession(session.id);
                    refresh.run();
                });
    }

    private void confirmDeleteTracker(Tracker tracker, Runnable restore, Runnable animateDelete) {
        String name = tracker.name == null || tracker.name.trim().isEmpty() ? ui.t("Diesen Tracker") : tracker.name;
        DeleteGestureHelper.runDelete(activity, ui, "Tracker löschen", name + ui.t(" wirklich löschen?"),
                restore, animateDelete, () -> {
                    db.deleteTracker(tracker.id);
                    refresh.run();
                });
    }

    private void duplicateTracker(Tracker tracker) {
        try {
            TrackerJsonRepository.duplicateTracker(db, tracker.id, ui.t("Unbenannter Tracker"), ui.t("Kopie"));
            refresh.run();
        } catch (Exception e) {
            android.widget.Toast.makeText(activity, e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private String preview(long sessionId, Tracker tracker) {
        java.util.Map<Long, FieldRecord> records = db.records(sessionId);
        StringBuilder builder = new StringBuilder();
        for (FieldDefinition field : tracker.fields) {
            FieldRecord record = records.get(field.id);
            if (record == null) {
                continue;
            }

            java.util.Map<String, Object> values = JsonUtil.toMap(record.valuesJson);
            Object value = values.get(field.key);
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(field.label == null || field.label.trim().isEmpty() ? field.key : ui.t(field.label))
                    .append(": ")
                    .append(formatValue(field, value));
            if (builder.length() > 110) {
                break;
            }
        }
        return builder.length() == 0 ? ui.t("Noch keine Werte eingetragen.") : builder.toString();
    }

    private String formatValue(FieldDefinition field, Object value) {
        if ("duration".equals(field.type)) {
            long millis = FormatUtil.toLong(value);
            return FormatUtil.formatMs(millis);
        }
        if ("float".equals(field.type) && value instanceof Number) {
            return String.format(java.util.Locale.US, "%." + field.decimals + "f", ((Number) value).doubleValue())
;
        }
        return String.valueOf(value);
    }

    private String fieldPreview(Tracker tracker) {
        if (tracker.fields.isEmpty()) {
            return ui.t("Noch keine Felder angelegt.");
        }

        StringBuilder builder = new StringBuilder();
        for (FieldDefinition field : tracker.fields) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(field.label == null || field.label.trim().isEmpty() ? ui.t("Ohne Label") : ui.t(field.label));
            if (builder.length() > 90) {
                break;
            }
        }
        return builder.toString();
    }

    private String date(long millis) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(new java.util.Date(millis));
    }

}
