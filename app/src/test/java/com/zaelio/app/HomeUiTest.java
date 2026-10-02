package com.zaelio.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputLayout;
import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HomeUiTest {
    private Context context;
    private TrackingDatabase db;
    private ActivityController<FragmentActivity> controller;
    private FragmentActivity activity;
    private ThemeStore theme;
    private HomeUi home;
    private FrameLayout body;
    private boolean sessionsTab = true;
    private long training;
    private long reading;
    private long first;
    private long second;
    private long third;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("tracking.sqlite");
        context.getSharedPreferences("overview_options", 0).edit().clear().commit();
        db = new TrackingDatabase(context);
        training = db.trackers().get(0).id;
        reading = db.insertTracker(db.getWritableDatabase(), "Lesen", "");
        db.insertTracker(db.getWritableDatabase(), "Ohne Sessions", "");
        first = createSession(training, 1);
        second = createSession(reading, 10);
        third = createSession(training, 30);
        db.reorderSessions(Arrays.asList(second, first, third));
        controller = Robolectric.buildActivity(FragmentActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.AppTheme);
        controller.setup();
        theme = new ThemeStore(activity);
        theme.setLanguage("de");
        theme.setThemeMode(ThemeStore.THEME_DARK);
        body = new FrameLayout(activity);
        activity.setContentView(body);
        home = new HomeUi(activity, db, theme, new AppUi(activity, theme), id -> {}, id -> {}, this::render);
        render();
    }

    @After
    public void tearDown() {
        for (Dialog dialog : new ArrayList<>(ShadowDialog.getShownDialogs())) {
            dialog.dismiss();
        }
        controller.pause().stop().destroy();
        shadowOf(Looper.getMainLooper()).idle();
        db.close();
        context.deleteDatabase("tracking.sqlite");
    }

    @Test
    public void popupUsesCurrentTrackersAndDraftsApplyOnlyOnConfirmation() {
        assertEquals(Arrays.asList(second, first, third), visibleIds());
        assertEquals(3, find(body, View.class, v -> "Verschieben".equals(v.getContentDescription())).size());
        AlertDialog dialog = openFilters();
        assertEquals(2, find(dialogView(dialog), MaterialCheckBox.class, v -> true).size());
        check(dialog, "Lesen").performClick();
        assertEquals(Arrays.asList(second, first, third), visibleIds());
        assertNotNull(text(dialogView(dialog), "1 Sessions anzeigen"));
        find(dialogView(dialog), View.class, view -> "Schließen".equals(view.getContentDescription())).get(0).performClick();
        assertFalse(dialog.isShowing());
        assertEquals(0, new OverviewOptions(activity, "sessions").filterCount());

        dialog = openFilters();
        assertFalse(check(dialog, "Lesen").isChecked());
        check(dialog, "Lesen").performClick();
        button(dialogView(dialog), "1 Sessions anzeigen").performClick();
        assertEquals(Arrays.asList(second), visibleIds());
        assertNotNull(text(body, "1 von 3 Sessions"));
        assertNotNull(text(body, "Filter (1)"));
        assertEquals(0, find(body, View.class, v -> "Verschieben".equals(v.getContentDescription())).size());
        assertEquals(Arrays.asList(second, first, third), databaseSessionIds());
        assertEquals(1, new OverviewOptions(activity, "sessions").filterCount());
        Chip active = find(body, Chip.class, chip -> "Lesen".contentEquals(chip.getText())).get(0);
        assertEquals(255, Color.alpha(active.getChipBackgroundColor().getDefaultColor()));
        assertEquals(ColorUtils.compositeColors(theme.accentSoftColor(), theme.surfaceColor()),
                active.getChipBackgroundColor().getDefaultColor());
        active.performCloseIconClick();
        assertEquals(Arrays.asList(second, first, third), visibleIds());
        assertEquals(0, new OverviewOptions(activity, "sessions").filterCount());
    }

    @Test
    public void sortingDoesNotOverwriteManualOrderAndResetRestoresIt() {
        AlertDialog dialog = openFilters();
        find(dialogView(dialog), MaterialRadioButton.class, radio -> "Neueste zuerst".contentEquals(radio.getText())).get(0).performClick();
        button(dialogView(dialog), "3 Sessions anzeigen").performClick();
        assertEquals(Arrays.asList(third, second, first), visibleIds());
        assertEquals(Arrays.asList(second, first, third), databaseSessionIds());
        assertEquals(0, find(body, View.class, v -> "Verschieben".equals(v.getContentDescription())).size());
        home = new HomeUi(activity, db, theme, new AppUi(activity, theme), id -> {}, id -> {}, this::render);
        render();
        assertEquals(Arrays.asList(third, second, first), visibleIds());
        dialog = openFilters();
        button(dialogView(dialog), "Zurücksetzen").performClick();
        assertNotNull(text(dialogView(dialog), "3 Sessions anzeigen"));
        button(dialogView(dialog), "3 Sessions anzeigen").performClick();
        assertEquals(Arrays.asList(second, first, third), visibleIds());
    }

    @Test
    public void customDateFieldsUseSessionRangeAndOpenMaterialPicker() {
        AlertDialog dialog = openFilters();
        find(dialogView(dialog), Chip.class, chip -> "Eigener Zeitraum".contentEquals(chip.getText())).get(0).performClick();
        TextInputLayout from = find(dialogView(dialog), TextInputLayout.class,
                input -> "Von".contentEquals(input.getHint())).get(0);
        TextInputLayout to = find(dialogView(dialog), TextInputLayout.class,
                input -> "Bis".contentEquals(input.getHint())).get(0);
        assertEquals(theme.mutedTextColor(), from.getDefaultHintTextColor().getDefaultColor());
        assertEquals("01.09.2026", from.getEditText().getText().toString());
        assertEquals("30.09.2026", to.getEditText().getText().toString());
        from.getEditText().performClick();
        activity.getSupportFragmentManager().executePendingTransactions();
        MaterialDatePicker<?> picker = (MaterialDatePicker<?>) activity.getSupportFragmentManager().findFragmentByTag("overview-date");
        assertNotNull(picker);
        assertEquals(OverviewOptions.utcDay(2026, Calendar.SEPTEMBER, 1), picker.getSelection());
        picker.dismissNow();
        button(dialogView(dialog), "3 Sessions anzeigen").performClick();
        assertNotNull(text(body, "01.09.2026–30.09.2026"));
    }

    @Test
    public void trackerDateFilterDependsOnSessionsAndTabsKeepIndependentOptions() {
        sessionsTab = false;
        OverviewOptions options = new OverviewOptions(activity, "trackers");
        options.period = OverviewOptions.CUSTOM;
        options.fromDay = options.toDay = OverviewOptions.utcDay(2026, Calendar.SEPTEMBER, 10);
        options.save();
        home = new HomeUi(activity, db, theme, new AppUi(activity, theme), id -> {}, id -> {}, this::render);
        render();
        assertEquals(Arrays.asList(reading), visibleIds());
        assertNotNull(text(body, "1 von 3 Trackern"));
        sessionsTab = true;
        render();
        assertEquals(Arrays.asList(second, first, third), visibleIds());
        assertEquals(0, new OverviewOptions(activity, "sessions").filterCount());
    }

    @Test
    public void deletingOnlySessionRemovesTrackerChoiceAndObsoleteActiveFilters() {
        AlertDialog dialog = openFilters();
        check(dialog, "Lesen").performClick();
        find(dialogView(dialog), Chip.class, c -> "Eigener Zeitraum".contentEquals(c.getText())).get(0).performClick();
        assertEquals("10.09.2026", dateField(dialog, "Von").getText().toString());
        assertEquals("10.09.2026", dateField(dialog, "Bis").getText().toString());
        button(dialogView(dialog), "1 Sessions anzeigen").performClick();
        deleteSessionViaOverview(second);
        assertNotNull(db.readTracker(reading));
        assertNull(db.session(second));
        assertEquals(Arrays.asList(first, third), visibleIds());
        OverviewOptions restored = new OverviewOptions(activity, "sessions");
        assertEquals(0, restored.filterCount());
        assertEquals("01.09.2026", OverviewOptions.displayDay(restored.fromDay));
        assertEquals("30.09.2026", OverviewOptions.displayDay(restored.toDay));
        dialog = openFilters();
        assertEquals(1, find(dialogView(dialog), MaterialCheckBox.class, v -> true).size());
        assertTrue(find(dialogView(dialog), MaterialCheckBox.class, c -> "Lesen".contentEquals(c.getText())).isEmpty());
        dialog.dismiss();
        createSession(reading, 17);
        render();
        dialog = openFilters();
        assertEquals(2, find(dialogView(dialog), MaterialCheckBox.class, v -> true).size());
        assertNotNull(check(dialog, "Lesen"));
    }

    @Test
    public void deletingDateBoundaryAndAllSessionsUpdatesDatesAndDisablesEmptyPeriods() {
        AlertDialog dialog = openFilters();
        find(dialogView(dialog), Chip.class, c -> "Eigener Zeitraum".contentEquals(c.getText())).get(0).performClick();
        button(dialogView(dialog), "3 Sessions anzeigen").performClick();
        deleteSessionViaOverview(third);
        OverviewOptions restored = new OverviewOptions(activity, "sessions");
        assertEquals(OverviewOptions.CUSTOM, restored.period);
        assertEquals("10.09.2026", OverviewOptions.displayDay(restored.toDay));
        deleteSessionViaOverview(first);
        restored = new OverviewOptions(activity, "sessions");
        assertEquals("10.09.2026", OverviewOptions.displayDay(restored.fromDay));
        assertEquals("10.09.2026", OverviewOptions.displayDay(restored.toDay));
        deleteSessionViaOverview(second);
        assertEquals(0, new OverviewOptions(activity, "sessions").filterCount());
        dialog = openFilters();
        assertTrue(find(dialogView(dialog), MaterialCheckBox.class, v -> true).isEmpty());
        for (Chip chip : find(dialogView(dialog), Chip.class, v -> true)) {
            assertEquals("Alle".contentEquals(chip.getText()), chip.isEnabled());
        }
        assertNotNull(text(dialogView(dialog), "Keine Sessions für diesen Zeitraum verfügbar."));
    }

    @Test
    public void trackerTabKeepsTemplatesWithoutSessionsButDisablesTheirDateChoices() {
        sessionsTab = false;
        render();
        AlertDialog dialog = openFilters();
        assertEquals(3, find(dialogView(dialog), MaterialCheckBox.class, v -> true).size());
        check(dialog, "Ohne Sessions").performClick();
        for (Chip chip : find(dialogView(dialog), Chip.class, v -> true)) {
            assertEquals("Alle".contentEquals(chip.getText()), chip.isEnabled());
        }
        button(dialogView(dialog), "1 Tracker anzeigen").performClick();
        assertEquals(1, visibleIds().size());
        assertEquals(1, new OverviewOptions(activity, "trackers").filterCount());
    }

    @Test
    public void selectedTrackerConstrainsMaterialDatePickerToItsSessionDates() {
        AlertDialog dialog = openFilters();
        check(dialog, "Lesen").performClick();
        find(dialogView(dialog), Chip.class, c -> "Eigener Zeitraum".contentEquals(c.getText())).get(0).performClick();
        dateField(dialog, "Von").performClick();
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        MaterialDatePicker<?> picker = (MaterialDatePicker<?>) activity.getSupportFragmentManager().findFragmentByTag("overview-date");
        long selectedDay = OverviewOptions.utcDay(2026, Calendar.SEPTEMBER, 10);
        assertEquals(selectedDay, picker.getSelection());
        GridView grid = find(picker.requireView(), GridView.class,
                view -> view.getAdapter() != null && view.getAdapter().getCount() > 7).get(0);
        int checkedDays = 0;
        for (int i = 0; i < grid.getAdapter().getCount(); i++) {
            Object day = grid.getAdapter().getItem(i);
            if (day instanceof Long) {
                View cell = grid.getAdapter().getView(i, null, grid);
                assertEquals(((Long) day).longValue() == selectedDay, cell.isEnabled());
                checkedDays++;
            }
        }
        assertEquals(30, checkedDays);
        picker.dismissNow();
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void filterAndLongSortLabelsKeepEqualBoundsInSeparateFilterCard() {
        AppUi ui = new AppUi(activity, theme);
        for (String language : new String[]{"de", "en", "es"}) {
            theme.setLanguage(language);
            for (int scale : new int[]{1, theme.fontScaleCount() - 1}) {
                theme.setFontScaleIndex(scale);
                render();
                body.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
                body.layout(0, 0, 320, 640);
                Button filter = button(body, ui.t("Filter"));
                Button sort = button(body, ui.t("Manuelle Reihenfolge"));
                if ("de".equals(language)) {
                    assertTrue("German label must wrap at font scale " + scale, sort.getLineCount() > 1);
                }
                assertEquals(filter.getTop(), sort.getTop());
                assertEquals(filter.getBottom(), sort.getBottom());
                assertEquals(filter.getHeight(), sort.getHeight());
                assertTrue(filter.getHeight() >= ui.buttonHeight());
                android.widget.LinearLayout controls = (android.widget.LinearLayout) filter.getParent();
                assertFalse(controls.isBaselineAligned());
                ViewGroup panel = (ViewGroup) controls.getParent();
                ViewGroup heading = (ViewGroup) text(body, ui.t("Sessions")).getParent();
                assertNotSame(heading, panel);
                assertSame(heading.getParent(), panel.getParent());
                assertNotNull(panel.getBackground());
                assertTrue(panel.getTop() > heading.getBottom());
                ScrollView list = find(body, ScrollView.class, v -> true).get(0);
                assertTrue(list.getTop() > panel.getBottom());
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void filterCardMatchesOverviewCardsWithAccentButtonsAndEqualSpacingWithAndWithoutChips() {
        AppUi ui = new AppUi(activity, theme);
        for (int mode : new int[]{ThemeStore.THEME_LIGHT, ThemeStore.THEME_DARK}) {
            theme.setThemeMode(mode);
            for (int accent : new int[]{1, 4}) {
                theme.setAccentIndex(accent);
                for (boolean tab : new boolean[]{true, false}) {
                    sessionsTab = tab;
                    for (boolean active : new boolean[]{false, true}) {
                        OverviewOptions options = new OverviewOptions(activity, tab ? "sessions" : "trackers");
                        options.reset();
                        if (active) {
                            options.trackerIds.add(reading);
                            options.period = OverviewOptions.CUSTOM;
                            options.fromDay = options.toDay = OverviewOptions.utcDay(2026, Calendar.SEPTEMBER, 10);
                        }
                        options.save();
                        home = new HomeUi(activity, db, theme, ui, id -> {}, id -> {}, this::render);
                        render();
                        body.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
                        body.layout(0, 0, 320, 640);
                        Button filter = find(body, Button.class, b -> b.getText().toString().startsWith("Filter")).get(0);
                        ViewGroup controls = (ViewGroup) filter.getParent();
                        ViewGroup panel = (ViewGroup) controls.getParent();
                        int panelColor = ((GradientDrawable) panel.getBackground()).getColor().getDefaultColor();
                        assertEquals(theme.surfaceColor(), panelColor);
                        View card = find(body, View.class, v -> v.getTag() instanceof Long).get(0);
                        assertEquals(((GradientDrawable) card.getBackground()).getColor().getDefaultColor(), panelColor);
                        for (MaterialButton control : find(controls, MaterialButton.class, v -> true)) {
                            assertEquals(theme.accentColor(), control.getCurrentTextColor());
                            assertNotNull(control.getIcon());
                            assertEquals(theme.accentColor(), control.getIconTint().getDefaultColor());
                            assertEquals(theme.surfaceColor(), control.getBackgroundTintList().getDefaultColor());
                        }
                        assertEquals(ui.spaceM(), panel.getPaddingTop());
                        assertEquals(ui.spaceM(), panel.getPaddingBottom());
                        assertEquals(ui.spaceM(), controls.getTop());
                        if (active) {
                            View chips = panel.getChildAt(1);
                            assertEquals(ui.spaceM(), chips.getTop() - controls.getBottom());
                            assertEquals(ui.spaceM(), panel.getHeight() - chips.getBottom());
                        } else {
                            assertEquals(ui.spaceM(), panel.getHeight() - controls.getBottom());
                        }
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    public void popupKeepsApplyFooterVisibleOnSmallScreensWithLargeText() {
        theme.setFontScaleIndex(theme.fontScaleCount() - 1);
        render();
        AlertDialog dialog = openFilters();
        assertEquals(512, dialog.getWindow().getAttributes().height);
        Button apply = button(dialogView(dialog), "3 Sessions anzeigen");
        ViewGroup card = (ViewGroup) apply.getParent().getParent();
        card.measure(View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        card.layout(0, 0, 288, 480);
        android.graphics.Rect rect = new android.graphics.Rect();
        apply.getDrawingRect(rect);
        card.offsetDescendantRectToMyCoords(apply, rect);
        assertTrue(rect.top >= 0);
        assertTrue(rect.bottom <= card.getHeight());
        assertTrue(apply.getHeight() >= new AppUi(activity, theme).buttonHeight());
    }

    @Test
    @Config(sdk = {23, 34})
    public void mainActivityStillRoutesBackAndOpensMaterialPopupAfterFragmentSupportChange() {
        ActivityController<MainActivity> mainController = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity main = mainController.get();
        try {
            View root = main.findViewById(android.R.id.content);
            button(root, "Filter").performClick();
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            assertTrue(dialog.isShowing());
            dialog.onBackPressed();
            shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
            assertFalse(dialog.isShowing());
            assertEquals(0, new OverviewOptions(main, "sessions").filterCount());
            find(root, View.class, view -> "Menü".equals(view.getContentDescription())).get(0).performClick();
            button(dialogView(ShadowDialog.getLatestDialog()), "Einstellungen").performClick();
            assertNotNull(text(main.findViewById(android.R.id.content), "Einstellungen"));
            main.getOnBackPressedDispatcher().onBackPressed();
            assertNotNull(text(main.findViewById(android.R.id.content), "3 von 3 Sessions"));
            main.getOnBackPressedDispatcher().onBackPressed();
            assertFalse(main.isFinishing());
            main.getOnBackPressedDispatcher().onBackPressed();
            assertTrue(main.isFinishing());
        } finally {
            mainController.pause().stop().destroy();
        }
    }

    private EditText dateField(Dialog dialog, String label) {
        return find(dialogView(dialog), TextInputLayout.class, input -> label.contentEquals(input.getHint())).get(0).getEditText();
    }

    private void deleteSessionViaOverview(long id) {
        View card = find(body, View.class, view -> Long.valueOf(id).equals(view.getTag())).get(0);
        text(card, "...").performClick();
        button(dialogView(ShadowDialog.getLatestDialog()), "Löschen").performClick();
        shadowOf(Looper.getMainLooper()).idleFor(DeleteGestureHelper.REMOVE_AFTER_DELETE_MS, TimeUnit.MILLISECONDS);
    }

    private AlertDialog openFilters() {
        find(body, Button.class, button -> button.getText().toString().startsWith("Filter")).get(0).performClick();
        Dialog latest = ShadowDialog.getLatestDialog();
        assertTrue(latest instanceof AlertDialog);
        AlertDialog dialog = (AlertDialog) latest;
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        assertEquals(Gravity.CENTER_VERTICAL, dialog.getWindow().getAttributes().gravity & Gravity.VERTICAL_GRAVITY_MASK);
        assertTrue(find(dialogView(dialog), ScrollView.class, view -> true).get(0).getHeight() > 0);
        return dialog;
    }

    private View dialogView(Dialog dialog) {
        return dialog.getWindow().getDecorView();
    }

    private MaterialCheckBox check(Dialog dialog, String label) {
        return find(dialogView(dialog), MaterialCheckBox.class, c -> label.contentEquals(c.getText())).get(0);
    }

    private Button button(View root, String label) {
        return find(root, Button.class, b -> label.contentEquals(b.getText())).get(0);
    }

    private TextView text(View root, String label) {
        return find(root, TextView.class, v -> label.contentEquals(v.getText())).get(0);
    }

    private List<Long> visibleIds() {
        List<Long> ids = new ArrayList<>();
        for (View view : find(body, View.class, v -> v.getTag() instanceof Long)) {
            ids.add((Long) view.getTag());
        }
        return ids;
    }

    private List<Long> databaseSessionIds() {
        List<Long> ids = new ArrayList<>();
        for (Session session : db.sessions()) {
            ids.add(session.id);
        }
        return ids;
    }

    private long createSession(long trackerId, int day) {
        long id = db.createSession(trackerId);
        Calendar date = Calendar.getInstance();
        date.clear();
        date.set(2026, Calendar.SEPTEMBER, day, 22, 30);
        db.getWritableDatabase().execSQL("UPDATE sessions SET createdAt=? WHERE id=?", new Object[]{date.getTimeInMillis(), id});
        return id;
    }

    private void render() {
        body.removeAllViews();
        if (sessionsTab) {
            home.renderSessions(body);
        } else {
            home.renderTrackers(body);
        }
    }

    private <T extends View> List<T> find(View root, Class<T> type, Predicate<T> predicate) {
        List<T> result = new ArrayList<>();
        collect(root, type, predicate, result);
        return result;
    }

    private <T extends View> void collect(View view, Class<T> type, Predicate<T> predicate, List<T> result) {
        if (type.isInstance(view) && predicate.test(type.cast(view))) {
            result.add(type.cast(view));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), type, predicate, result);
            }
        }
    }
}
