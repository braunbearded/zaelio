package com.zaelio.app;

import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputLayout;
import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OverviewFilterUi {
    private final FragmentActivity activity;
    private final ThemeStore theme;
    private final AppUi ui;
    private final Runnable refresh;
    private AlertDialog dialog;

    OverviewFilterUi(FragmentActivity activity, ThemeStore theme, AppUi ui, Runnable refresh) {
        this.activity = activity;
        this.theme = theme;
        this.ui = ui;
        this.refresh = refresh;
    }

    void show(OverviewOptions options, boolean sessionsTab, List<Tracker> trackers, List<Session> sessions) {
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        OverviewOptions draft = options.copy();
        draft.reconcile(trackers, sessions, System.currentTimeMillis());
        LinearLayout card = ui.contentCard();
        card.setPadding(0, ui.spaceS(), 0, 0);
        populate(card, options, draft, sessionsTab, trackers, sessions);
        dialog = ui.showCardDialog(card);
        if (dialog.getWindow() != null) {
            int height = activity.getResources().getDisplayMetrics().heightPixels * 4 / 5;
            dialog.getWindow().setLayout(dialog.getWindow().getAttributes().width, height);
        }
    }

    private void populate(LinearLayout card, OverviewOptions options, OverviewOptions draft,
                          boolean sessionsTab, List<Tracker> trackers, List<Session> sessions) {
        card.removeAllViews();
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ui.spaceL(), 0, ui.spaceS(), ui.spaceS());
        header.addView(ui.text("Filter & Sortierung", 22, theme.primaryTextColor(), true),
                new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(ui.iconButton(R.drawable.ic_close_24, ui.t("Schließen"), v -> dialog.dismiss()),
                new LinearLayout.LayoutParams(ui.buttonHeight(), ui.buttonHeight()));
        card.addView(header);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = column();
        content.setPadding(ui.spaceL(), 0, ui.spaceL(), ui.spaceL());
        scroll.addView(content);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button apply = ui.primaryButton("");
        ChipGroup periods = new ChipGroup(activity);
        android.widget.TextView dateHint = ui.metaText("");
        LinearLayout dates = new LinearLayout(activity);
        dates.setOrientation(LinearLayout.HORIZONTAL);
        EditText from = dateInput();
        EditText to = dateInput();
        TextInputLayout fromLayout = ui.outlinedInput("Von", from);
        TextInputLayout toLayout = ui.outlinedInput("Bis", to);
        LinearLayout.LayoutParams fromLp = new LinearLayout.LayoutParams(0, -2, 1);
        fromLp.rightMargin = ui.spaceS();
        dates.addView(fromLayout, fromLp);
        dates.addView(toLayout, new LinearLayout.LayoutParams(0, -2, 1));
        Map<Long, Tracker> byId = new HashMap<>();
        for (Tracker tracker : trackers) {
            byId.put(tracker.id, tracker);
        }
        Locale locale = new Locale(theme.resolvedLanguage());
        Runnable update = () -> {
            long now = System.currentTimeMillis();
            int count = sessionsTab
                    ? draft.sessions(sessions, byId, now, locale).size()
                    : draft.trackers(trackers, sessions, now, locale).size();
            apply.setText(String.format(locale, ui.t(sessionsTab ? "%d Sessions anzeigen" : "%d Tracker anzeigen"), count));
            apply.setEnabled(draft.validDates() && (draft.period == OverviewOptions.ALL || count > 0));
            for (int i = 0; i < periods.getChildCount(); i++) {
                Chip chip = (Chip) periods.getChildAt(i);
                int period = (Integer) chip.getTag();
                boolean available = draft.periodAvailable(period, sessions, now);
                chip.setEnabled(available);
                chip.setAlpha(available ? 1f : 0.45f);
                if (period == draft.period && !chip.isChecked()) {
                    periods.check(chip.getId());
                }
            }
            dateHint.setText(ui.t(draft.sessionRange(sessions) == null ? "Keine Sessions für diesen Zeitraum verfügbar."
                    : draft.period == OverviewOptions.CUSTOM && count == 0 && draft.validDates()
                    ? "Keine Sessions im gewählten Zeitraum." : "Start- und Enddatum eingeschlossen"));
            dates.setVisibility(draft.period == OverviewOptions.CUSTOM ? View.VISIBLE : View.GONE);
            from.setText(OverviewOptions.displayDay(draft.fromDay));
            to.setText(OverviewOptions.displayDay(draft.toDay));
            fromLayout.setError(draft.validDates() ? null : ui.t("Von darf nicht nach Bis liegen."));
        };
        configureDate(fromLayout, from, () -> pickDate(draft.fromDay, draft.sessionRange(sessions), day -> {
            draft.fromDay = day;
            update.run();
        }));
        configureDate(toLayout, to, () -> pickDate(draft.toDay, draft.sessionRange(sessions), day -> {
            draft.toDay = day;
            update.run();
        }));

        ui.addSectionHeader(content, "Tracker", "Mehrere auswählen");
        if (trackers.isEmpty()) {
            content.addView(ui.bodyText(sessionsTab ? "Noch keine Sessions vorhanden" : "Noch keine Tracker vorhanden"));
        }
        for (Tracker tracker : trackers) {
            MaterialCheckBox check = new MaterialCheckBox(activity);
            check.setText(tracker.name == null || tracker.name.trim().isEmpty() ? ui.t("Unbenannter Tracker") : tracker.name);
            check.setTextSize(ui.sp(16));
            check.setTextColor(theme.primaryTextColor());
            check.setUseMaterialThemeColors(false);
            check.setButtonTintList(ui.checkedColorStateList());
            check.setMinHeight(ui.buttonHeight());
            check.setChecked(draft.trackerIds.contains(tracker.id));
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    draft.trackerIds.add(tracker.id);
                } else {
                    draft.trackerIds.remove(tracker.id);
                }
                draft.reconcile(trackers, sessions, System.currentTimeMillis());
                update.run();
            });
            content.addView(check, new LinearLayout.LayoutParams(-1, -2));
        }
        divider(content);
        ui.addSectionHeader(content, "Zeitraum", null);
        periods.setSingleSelection(true);
        periods.setSelectionRequired(true);
        periods.setChipSpacing(ui.spaceS());
        for (int i = 0; i < OverviewOptions.PERIOD_LABELS.length; i++) {
            Chip chip = ui.choiceChip(OverviewOptions.PERIOD_LABELS[i]);
            chip.setId(View.generateViewId());
            chip.setTag(i);
            periods.addView(chip);
            if (i == draft.period) {
                periods.check(chip.getId());
            }
        }
        periods.setOnCheckedStateChangeListener((group, ids) -> {
            if (!ids.isEmpty()) {
                int period = (Integer) group.findViewById(ids.get(0)).getTag();
                if (period == OverviewOptions.CUSTOM && draft.period != OverviewOptions.CUSTOM) {
                    draft.useSessionRange(sessions);
                }
                draft.period = period;
                update.run();
            }
        });
        content.addView(periods);
        content.addView(dates);
        dateHint.setPadding(0, ui.spaceS(), 0, 0);
        content.addView(dateHint);
        divider(content);
        ui.addSectionHeader(content, "Sortierung", null);
        RadioGroup sorts = new RadioGroup(activity);
        for (int i = 0; i < OverviewOptions.SORT_LABELS.length; i++) {
            MaterialRadioButton radio = new MaterialRadioButton(activity);
            radio.setId(View.generateViewId());
            radio.setTag(i);
            radio.setText(ui.t(OverviewOptions.SORT_LABELS[i]));
            radio.setTextSize(ui.sp(16));
            radio.setTextColor(theme.primaryTextColor());
            radio.setUseMaterialThemeColors(false);
            radio.setButtonTintList(ui.checkedColorStateList());
            radio.setMinHeight(ui.buttonHeight());
            sorts.addView(radio, new RadioGroup.LayoutParams(-1, -2));
            if (i == draft.sort) {
                sorts.check(radio.getId());
            }
        }
        sorts.setOnCheckedChangeListener((group, id) -> {
            draft.sort = (Integer) group.findViewById(id).getTag();
            update.run();
        });
        content.addView(sorts);
        divider(card);
        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(ui.spaceL(), 0, ui.spaceL(), ui.spaceL());
        Button reset = ui.button("Zurücksetzen", theme.surfaceColor(), theme.accentColor(), theme.surfaceColor());
        reset.setPadding(0, 0, 0, 0);
        reset.setSingleLine(true);
        reset.setOnClickListener(v -> {
            draft.reset();
            draft.reconcile(trackers, sessions, System.currentTimeMillis());
            populate(card, options, draft, sessionsTab, trackers, sessions);
        });
        footer.addView(reset, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(0, -2, 1);
        applyLp.leftMargin = ui.spaceS();
        footer.addView(apply, applyLp);
        card.addView(footer);
        apply.setOnClickListener(v -> {
            if (!apply.isEnabled() || !draft.validDates()) {
                return;
            }
            options.copyFrom(draft);
            options.save();
            dialog.dismiss();
            refresh.run();
        });
        update.run();
    }

    private EditText dateInput() {
        EditText input = ui.textInput("", "", InputType.TYPE_NULL);
        input.setFocusable(false);
        input.setCursorVisible(false);
        return input;
    }

    private void configureDate(TextInputLayout layout, EditText input, Runnable open) {
        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(R.drawable.ic_calendar_24);
        layout.setEndIconTintList(ColorStateList.valueOf(theme.secondaryTextColor()));
        layout.setEndIconContentDescription(ui.t("Datum auswählen"));
        layout.setEndIconOnClickListener(v -> open.run());
        input.setOnClickListener(v -> open.run());
    }

    private void pickDate(long day, long[] range, java.util.function.LongConsumer onDate) {
        if (range == null || activity.getSupportFragmentManager().findFragmentByTag("overview-date") != null) {
            return;
        }
        long selected = Math.max(range[0], Math.min(day, range[1]));
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(range[0]).setEnd(range[1]).setOpenAt(selected)
                .setValidator(CompositeDateValidator.allOf(Arrays.asList(
                        DateValidatorPointForward.from(range[0]), DateValidatorPointBackward.before(range[1])))).build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTheme(theme.darkMode() ? R.style.OverviewDatePickerDark : R.style.OverviewDatePickerLight)
                .setCalendarConstraints(constraints)
                .setTitleText(ui.t("Datum auswählen")).setSelection(selected).build();
        picker.addOnPositiveButtonClickListener(onDate::accept);
        picker.show(activity.getSupportFragmentManager(), "overview-date");
    }

    private void divider(LinearLayout parent) {
        MaterialDivider divider = new MaterialDivider(activity);
        divider.setDividerColor(theme.borderColor());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.strokeWidth());
        lp.topMargin = ui.spaceM();
        lp.bottomMargin = ui.spaceM();
        parent.addView(divider, lp);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
}
