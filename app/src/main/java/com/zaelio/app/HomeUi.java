package com.zaelio.app;

import android.app.Activity;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;

import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

public final class HomeUi {
    private final Activity activity;
    private final TrackingDatabase db;
    private final ThemeStore theme;
    private final AppUi ui;
    private final LongConsumer openSession;
    private final LongConsumer editTracker;
    private final Runnable refresh;

    public HomeUi(Activity activity, TrackingDatabase db, ThemeStore theme, AppUi ui,
                  LongConsumer openSession, LongConsumer editTracker, Runnable refresh) {
        this.activity = activity;
        this.db = db;
        this.theme = theme;
        this.ui = ui;
        this.openSession = openSession;
        this.editTracker = editTracker;
        this.refresh = refresh;
    }

    public void renderSessions(FrameLayout body) {
        ScrollView scrollView = createScrollView();
        LinearLayout box = createListBox(scrollView);

        java.util.List<Session> sessions = db.sessions();
        for (Session session : sessions) {
            Tracker tracker = db.readTracker(session.trackerId);
            if (tracker == null) {
                continue;
            }

            LinearLayout card = overviewCard(
                    tracker.name,
                    date(session.createdAt),
                    preview(session.id, tracker),
                    () -> openSession.accept(session.id),
                    null,
                    (restore, animateDelete) -> confirmDeleteSession(session, restore, animateDelete),
                    box,
                    () -> db.reorderSessions(childIds(box)));
            card.setTag(session.id);
            box.addView(card, cardLayoutParams());
        }

        if (sessions.isEmpty()) {
            box.addView(emptyState("Noch keine Sessions vorhanden", null));
        }

        body.addView(scrollView);
    }

    public void renderTrackers(FrameLayout body) {
        ScrollView scrollView = createScrollView();
        LinearLayout box = createListBox(scrollView);

        java.util.List<Tracker> trackers = db.trackers();
        for (Tracker tracker : trackers) {
            LinearLayout card = overviewCard(
                    tracker.name == null || tracker.name.trim().isEmpty() ? ui.t("Unbenannter Tracker") : tracker.name,
                    null,
                    fieldPreview(tracker),
                    () -> editTracker.accept(tracker.id),
                    () -> duplicateTracker(tracker),
                    (restore, animateDelete) -> confirmDeleteTracker(tracker, restore, animateDelete),
                    box,
                    () -> db.reorderTrackers(childIds(box)));
            card.setTag(tracker.id);
            box.addView(card, cardLayoutParams());
        }

        body.addView(scrollView);
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

        TextView handle = ui.listIcon("⠿");

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
        attachOverviewReorder(handle, reorderContainer, card, onReorder);
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

    private void attachOverviewReorder(View handle, LinearLayout container, View movedView, Runnable onChange) {
        ReorderHelper.attach(ui, handle, container, movedView, onChange);
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
            long millis = value instanceof Number ? ((Number) value).longValue() : parseLong(value);
            return FormatUtil.formatMs(millis);
        }
        if ("float".equals(field.type) && value instanceof Number) {
            return String.format(java.util.Locale.US, "%." + field.decimals + "f", ((Number) value).doubleValue())
;
        }
        return String.valueOf(value);
    }

    private long parseLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
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
