package com.zaelio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DeleteGestureHelperTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private ThemeStore theme;
    private AppUi ui;
    private TextView target;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class);
        activity = controller.get();
        activity.setTheme(R.style.AppTheme);
        controller.setup();
        theme = new ThemeStore(activity);
        theme.setLanguage("de");
        ui = new AppUi(activity, theme);
        target = new TextView(activity);
        target.setText("Feld");
        target.setClickable(true);
        activity.setContentView(target);
    }

    @After
    public void tearDown() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        shadowOf(Looper.getMainLooper()).idle();
        controller.pause().stop().destroy();
    }

    @Test
    public void dialogsDoNotStackAndDeletionRequiresConfirmationAndDelay() {
        int[] restored = {0};
        int[] secondRestored = {0};
        int[] animated = {0};
        int[] deleted = {0};
        DeleteGestureHelper.runDelete(activity, ui, "Löschen", "Wirklich?",
                () -> restored[0]++, () -> animated[0]++, () -> deleted[0]++);
        Dialog first = ShadowDialog.getLatestDialog();
        assertTrue(first.isShowing());

        DeleteGestureHelper.runDelete(activity, ui, "Löschen", "Zweiter Versuch",
                () -> secondRestored[0]++, () -> animated[0]++, () -> deleted[0]++);

        assertSame(first, ShadowDialog.getLatestDialog());
        assertEquals(1, secondRestored[0]);
        clickDialogButton("Abbrechen");
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, restored[0]);
        assertEquals(0, animated[0]);
        assertEquals(0, deleted[0]);

        DeleteGestureHelper.runDelete(activity, ui, "Löschen", "Wirklich?",
                () -> restored[0]++, () -> animated[0]++, () -> deleted[0]++);
        assertNotSame(first, ShadowDialog.getLatestDialog());
        clickDialogButton("Löschen");
        assertEquals(1, animated[0]);
        assertEquals(0, deleted[0]);
        shadowOf(Looper.getMainLooper()).idleFor(DeleteGestureHelper.REMOVE_AFTER_DELETE_MS - 1, TimeUnit.MILLISECONDS);
        assertEquals(0, deleted[0]);
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS);
        assertEquals(1, deleted[0]);
    }

    @Test
    public void onlyLongLeftSwipeMarksCandidateAndRestoreClearsNonColorFeedback() {
        theme.setAccentIndex(4);
        Drawable background = ui.makeRoundedCard(theme.surfaceColor(), theme.borderColor());
        target.setBackground(background);
        int[] selected = {0};
        Runnable[] restore = {null};
        boolean[] skipClick = {false};
        DeleteGestureHelper.attach(activity, theme, ui, target, target, (reset, animate) -> {
            selected[0]++;
            restore[0] = reset;
        }, skipClick);
        int[][] ignoredSwipes = {{160, 0}, {-100, 0}, {-160, 200}};
        for (int[] swipe : ignoredSwipes) {
            touch(target, MotionEvent.ACTION_DOWN, 0, 0);
            touch(target, MotionEvent.ACTION_MOVE, ui.px(swipe[0]), ui.px(swipe[1]));
            touch(target, MotionEvent.ACTION_UP, ui.px(swipe[0]), ui.px(swipe[1]));
            assertEquals(0, selected[0]);
        }

        touch(target, MotionEvent.ACTION_DOWN, 0, 0);
        touch(target, MotionEvent.ACTION_MOVE, -ui.px(160), 0);
        touch(target, MotionEvent.ACTION_UP, -ui.px(160), 0);

        assertEquals(1, selected[0]);
        assertTrue(skipClick[0]);
        assertTrue((target.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0);
        assertNotNull(restore[0]);
        restore[0].run();
        assertFalse((target.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0);
        assertSame(background, target.getBackground());
    }

    @Test
    public void cancelledSwipeNeverSelectsDeleteCandidate() {
        int[] selected = {0};
        DeleteGestureHelper.attach(activity, theme, ui, target, target,
                (restore, animate) -> selected[0]++, null);
        touch(target, MotionEvent.ACTION_DOWN, 0, 0);
        touch(target, MotionEvent.ACTION_MOVE, -ui.px(160), 0);

        touch(target, MotionEvent.ACTION_CANCEL, -ui.px(160), 0);
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);

        assertEquals(0, selected[0]);
        assertFalse((target.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0);
    }

    @Test
    public void excludedSubtreesAndCancelledLongPressDoNotSelectCandidates() {
        LinearLayout root = new LinearLayout(activity);
        LinearLayout excluded = new LinearLayout(activity);
        TextView interactive = new TextView(activity);
        interactive.setClickable(true);
        excluded.addView(interactive);
        root.addView(excluded);
        ((ViewGroup) target.getParent()).removeView(target);
        root.addView(target);
        activity.setContentView(root);
        int[] selected = {0};
        DeleteGestureHelper.attachToTree(activity, theme, ui, root, root, (restore, animate) -> {
            selected[0]++;
            restore.run();
        }, null, excluded);
        long wait = ViewConfiguration.getLongPressTimeout() + 501L;
        touch(interactive, MotionEvent.ACTION_DOWN, 0, 0);
        interactive.setPressed(true);
        shadowOf(Looper.getMainLooper()).idleFor(wait, TimeUnit.MILLISECONDS);
        touch(interactive, MotionEvent.ACTION_CANCEL, 0, 0);
        assertEquals(0, selected[0]);
        touch(target, MotionEvent.ACTION_DOWN, 0, 0);
        touch(target, MotionEvent.ACTION_CANCEL, 0, 0);
        shadowOf(Looper.getMainLooper()).idleFor(wait, TimeUnit.MILLISECONDS);
        assertEquals(0, selected[0]);

        touch(target, MotionEvent.ACTION_DOWN, 0, 0);
        target.setPressed(true);
        shadowOf(Looper.getMainLooper()).idleFor(wait, TimeUnit.MILLISECONDS);

        assertEquals(1, selected[0]);
        touch(target, MotionEvent.ACTION_UP, 0, 0);
    }

    private void clickDialogButton(String label) {
        Button button = findButton(ShadowDialog.getLatestDialog().getWindow().getDecorView(), label);
        assertNotNull(button);
        button.performClick();
    }

    private Button findButton(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button button = findButton(group.getChildAt(i), label);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }

    private void touch(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 10, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }
}
