package com.zaelio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.json.JSONObject;
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
public class TrackerFlowUiTest {
    private Context context;
    private TrackingDatabase db;
    private ActivityController<Activity> controller;
    private Activity activity;
    private Handler handler;
    private TrackerFlowUi flow;
    private Runnable backAction;
    private int sessionReturns;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("tracking.sqlite");
        db = new TrackingDatabase(context);
        controller = Robolectric.buildActivity(Activity.class);
        activity = controller.get();
        activity.setTheme(R.style.AppTheme);
        controller.setup();
        handler = new Handler(Looper.getMainLooper());
        ThemeStore theme = new ThemeStore(activity);
        theme.setLanguage("de");
        flow = new TrackerFlowUi(activity, db, theme, new AppUi(activity, theme), handler,
                () -> sessionReturns++, () -> {}, back -> backAction = back);
    }

    @After
    public void tearDown() {
        handler.removeCallbacksAndMessages(null);
        controller.pause().stop().destroy();
        db.close();
        context.deleteDatabase("tracking.sqlite");
    }

    @Test
    public void draggingFieldInRealEditorKeepsAllSessionValues() throws Exception {
        Tracker tracker = db.trackers().get(0);
        long sessionId = db.createSession(tracker.id);
        for (FieldDefinition field : tracker.fields) {
            db.saveRecords(db.session(sessionId), Collections.singletonMap(field.id,
                    Collections.singletonMap(field.key, "value-" + field.id)));
        }
        flow.editTracker(tracker.id);
        dragField(0, 1);

        Tracker saved = db.readTracker(tracker.id);
        assertEquals(tracker.fields.get(1).id, saved.fields.get(0).id);
        assertEquals(tracker.fields.get(0).id, saved.fields.get(1).id);
        assertEquals(4, db.recordCount(sessionId));
        for (FieldDefinition field : saved.fields) {
            assertEquals("value-" + field.id,
                    new JSONObject(db.records(sessionId).get(field.id).valuesJson).get(field.key));
        }
    }

    @Test
    public void renamingFieldAutosavesNewKeyAndSessionStillLoadsValue() throws Exception {
        Tracker tracker = db.trackers().get(0);
        FieldDefinition field = tracker.fields.get(0);
        long sessionId = db.createSession(tracker.id);
        db.saveRecords(db.session(sessionId), Collections.singletonMap(field.id, Collections.singletonMap(field.key, 42)));
        flow.editTracker(tracker.id);

        fieldNames().get(0).setText("Anzahl");
        fieldNames().get(0).setText("Neue Anzahl");

        FieldDefinition saved = db.readTracker(tracker.id).fields.get(0);
        assertEquals(field.id, saved.id);
        assertEquals("neue_anzahl", saved.key);
        JSONObject values = new JSONObject(db.records(sessionId).get(field.id).valuesJson);
        assertEquals(42, values.getInt(saved.key));
        assertFalse(values.has(field.key));
        flow.openSession(sessionId);
        assertEquals("42", find(EditText.class, input -> "42".contentEquals(input.getText())).get(0).getText().toString());
    }

    @Test
    public void newFieldsReceiveStableIdsAcrossAutosavesAndEditorReopens() throws Exception {
        flow.createTracker();
        find(Button.class, button -> "Feld hinzufügen".contentEquals(button.getText())).get(0).performClick();
        fieldNames().get(0).setText("Erstes Feld");
        Tracker tracker = db.trackers().get(1);
        long fieldId = tracker.fields.get(0).id;
        long sessionId = db.createSession(tracker.id);
        db.saveRecords(db.session(sessionId), Collections.singletonMap(fieldId, Collections.singletonMap("erstes_feld", "Kept")));

        fieldNames().get(0).setText("Umbenannt");
        find(Button.class, button -> "Feld hinzufügen".contentEquals(button.getText())).get(0).performClick();
        fieldNames().get(1).setText("Umbenannt");
        Tracker saved = db.readTracker(tracker.id);
        assertEquals(fieldId, saved.fields.get(0).id);
        long secondId = saved.fields.get(1).id;
        assertNotEquals(fieldId, secondId);
        assertEquals("umbenannt_2", saved.fields.get(1).key);
        fieldNames().get(1).setText("Zweites Feld");
        flow.editTracker(tracker.id);
        fieldNames().get(0).setText("Nochmals umbenannt");

        saved = db.readTracker(tracker.id);
        assertEquals(fieldId, saved.fields.get(0).id);
        assertEquals(secondId, saved.fields.get(1).id);
        assertEquals(1, db.recordCount(sessionId));
        assertEquals("Kept", new JSONObject(db.records(sessionId).get(fieldId).valuesJson).get("nochmals_umbenannt"));
    }

    @Test
    public void sessionControlsRemainEditableAndAutosaveChanges() throws Exception {
        Tracker tracker = db.trackers().get(0);
        long sessionId = db.createSession(tracker.id);
        flow.openSession(sessionId);
        EditText number = find(TextInputLayout.class, layout -> "Wiederholungen".contentEquals(layout.getHint())).get(0).getEditText();
        EditText note = find(TextInputLayout.class, layout -> "Notiz".contentEquals(layout.getHint())).get(0).getEditText();
        Button plus = find(Button.class, button -> "+".contentEquals(button.getText())).get(0);
        assertTrue(number.isEnabled());
        assertTrue(note.isEnabled());
        assertTrue(plus.isEnabled());

        number.setText("12");
        plus.performClick();
        note.setText("Notiz gespeichert");
        find(Button.class, button -> "Start".contentEquals(button.getText())).get(0).performClick();
        Button stop = find(Button.class, button -> "Stop".contentEquals(button.getText())).get(0);
        assertTrue(stop.isEnabled());
        stop.performClick();
        find(Button.class, button -> "Reset".contentEquals(button.getText())).get(0).performClick();
        assertEquals(0, db.recordCount(sessionId));
        shadowOf(Looper.getMainLooper()).idleFor(700, TimeUnit.MILLISECONDS);

        assertEquals(3, db.recordCount(sessionId));
        assertEquals(13L, new JSONObject(db.records(sessionId).get(tracker.fields.get(0).id).valuesJson).getLong("reps"));
        assertEquals("Notiz gespeichert", new JSONObject(db.records(sessionId).get(tracker.fields.get(2).id).valuesJson).getString("note"));
        assertEquals(0L, new JSONObject(db.records(sessionId).get(tracker.fields.get(3).id).valuesJson).getLong("duration"));
    }

    @Test
    public void copyingAndDeletingFieldsInEditorKeepsOtherSessionValues() throws Exception {
        Tracker tracker = db.trackers().get(0);
        Session session = db.session(db.createSession(tracker.id));
        for (int i = 0; i < 2; i++) {
            FieldDefinition field = tracker.fields.get(i);
            db.saveRecords(session, Collections.singletonMap(field.id, Collections.singletonMap(field.key, 21 + i)));
        }
        flow.editTracker(tracker.id);

        chooseFieldAction(0, "Kopieren");

        Tracker copied = db.readTracker(tracker.id);
        FieldDefinition copy = copied.fields.get(4);
        assertNotEquals(tracker.fields.get(0).id, copy.id);
        assertEquals(tracker.fields.get(0).label, copy.label);
        assertEquals(tracker.fields.get(0).type, copy.type);
        assertFalse(db.records(session.id).containsKey(copy.id));
        fieldNames().get(4).setText("Kopie");
        assertEquals(copy.id, db.readTracker(tracker.id).fields.get(4).id);
        assertEquals(2, db.recordCount(session.id));
        chooseFieldAction(4, "Löschen");
        shadowOf(Looper.getMainLooper()).idleFor(DeleteGestureHelper.REMOVE_AFTER_DELETE_MS, TimeUnit.MILLISECONDS);
        assertEquals(4, db.readTracker(tracker.id).fields.size());
        assertEquals(2, db.recordCount(session.id));
        assertEquals(21, new JSONObject(db.records(session.id).get(tracker.fields.get(0).id).valuesJson).getInt("wiederholungen"));

        chooseFieldAction(0, "Löschen");
        shadowOf(Looper.getMainLooper()).idleFor(DeleteGestureHelper.REMOVE_AFTER_DELETE_MS, TimeUnit.MILLISECONDS);

        assertEquals(3, db.readTracker(tracker.id).fields.size());
        assertEquals(1, db.recordCount(session.id));
        assertFalse(db.records(session.id).containsKey(tracker.fields.get(0).id));
        assertEquals(22, new JSONObject(db.records(session.id).get(tracker.fields.get(1).id).valuesJson).getInt("zusatzgewicht"));
        assertNotNull(db.session(session.id));
    }

    @Test
    public void draggingAfterDeletingMiddleFieldKeepsVisibleAndPersistedOrderInSync() throws Exception {
        Tracker tracker = db.trackers().get(0);
        Session session = db.session(db.createSession(tracker.id));
        for (FieldDefinition field : tracker.fields) {
            db.saveRecords(session, Collections.singletonMap(field.id, Collections.singletonMap(field.key, "value-" + field.id)));
        }
        flow.editTracker(tracker.id);
        chooseFieldAction(1, "Löschen");
        shadowOf(Looper.getMainLooper()).idleFor(DeleteGestureHelper.REMOVE_AFTER_DELETE_MS, TimeUnit.MILLISECONDS);
        Map<Long, FieldRecord> before = db.records(session.id);

        dragField(1, -1);

        Tracker saved = db.readTracker(tracker.id);
        assertEquals(tracker.fields.get(2).id, saved.fields.get(0).id);
        assertEquals(tracker.fields.get(0).id, saved.fields.get(1).id);
        assertEquals(tracker.fields.get(3).id, saved.fields.get(2).id);
        assertEquals(saved.fields.get(0).label, fieldNames().get(0).getText().toString());
        assertEquals(3, db.recordCount(session.id));
        for (FieldDefinition field : saved.fields) {
            FieldRecord record = db.records(session.id).get(field.id);
            assertEquals(before.get(field.id).id, record.id);
            assertEquals(before.get(field.id).updatedAt, record.updatedAt);
            assertEquals("value-" + field.id, new JSONObject(record.valuesJson).get(field.key));
        }
    }

    @Test
    public void clearingExistingFieldNameDoesNotDeleteItsValues() throws Exception {
        Tracker tracker = db.trackers().get(0);
        FieldDefinition field = tracker.fields.get(0);
        Session session = db.session(db.createSession(tracker.id));
        db.saveRecords(session, Collections.singletonMap(field.id, Collections.singletonMap(field.key, 21)));
        flow.editTracker(tracker.id);

        fieldNames().get(0).setText("");

        assertEquals(4, db.readTracker(tracker.id).fields.size());
        assertEquals(field.id, db.readTracker(tracker.id).fields.get(0).id);
        assertEquals(field.label, db.readTracker(tracker.id).fields.get(0).label);
        assertEquals(21, new JSONObject(db.records(session.id).get(field.id).valuesJson).getInt(field.key));
        fieldNames().get(0).setText("Weiter");
        assertEquals(21, new JSONObject(db.records(session.id).get(field.id).valuesJson).getInt("weiter"));
    }

    @Test
    public void leavingSessionFlushesPendingChangesAndStopsTimerTicks() throws Exception {
        Tracker tracker = db.trackers().get(0);
        long sessionId = db.createSession(tracker.id);
        flow.openSession(sessionId);
        find(TextInputLayout.class, layout -> "Wiederholungen".contentEquals(layout.getHint())).get(0).getEditText().setText("11");
        EditText timer = find(TextInputLayout.class, layout -> "Dauer".contentEquals(layout.getHint())).get(0).getEditText();
        find(Button.class, button -> "Start".contentEquals(button.getText())).get(0).performClick();
        assertEquals(0, db.recordCount(sessionId));

        backAction.run();

        assertEquals(1, sessionReturns);
        assertEquals(4, db.recordCount(sessionId));
        Map<Long, FieldRecord> saved = db.records(sessionId);
        assertEquals(11, new JSONObject(saved.get(tracker.fields.get(0).id).valuesJson).getInt("reps"));
        Object stoppedAt = timer.getTag();
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
        assertEquals(stoppedAt, timer.getTag());
        for (FieldRecord record : saved.values()) {
            FieldRecord after = db.records(sessionId).get(record.fieldId);
            assertEquals(record.id, after.id);
            assertEquals(record.updatedAt, after.updatedAt);
        }
    }

    @Test
    public void debounceRestartsAndOnlyWritesDirtyFields() throws Exception {
        Tracker tracker = db.trackers().get(0);
        long sessionId = db.createSession(tracker.id);
        flow.openSession(sessionId);
        EditText number = find(TextInputLayout.class, layout -> "Wiederholungen".contentEquals(layout.getHint())).get(0).getEditText();
        EditText note = find(TextInputLayout.class, layout -> "Notiz".contentEquals(layout.getHint())).get(0).getEditText();
        number.setText("10");
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS);
        assertEquals(0, db.recordCount(sessionId));
        number.setText("12");
        note.setText("Erste Notiz");
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS);
        assertEquals(0, db.recordCount(sessionId));
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
        assertEquals(2, db.recordCount(sessionId));
        FieldRecord numberRecord = db.records(sessionId).get(tracker.fields.get(0).id);
        assertEquals(12, new JSONObject(numberRecord.valuesJson).getInt("reps"));

        note.setText("Zweite Notiz");
        shadowOf(Looper.getMainLooper()).idleFor(700, TimeUnit.MILLISECONDS);

        assertEquals(2, db.recordCount(sessionId));
        assertEquals(numberRecord.id, db.records(sessionId).get(numberRecord.fieldId).id);
        assertEquals(numberRecord.updatedAt, db.records(sessionId).get(numberRecord.fieldId).updatedAt);
        assertEquals("Zweite Notiz", new JSONObject(db.records(sessionId).get(tracker.fields.get(2).id).valuesJson).getString("note"));
        assertFalse(db.records(sessionId).containsKey(tracker.fields.get(1).id));
    }

    @Test
    public void clearingSessionValuePersistsNullAndDoesNotPrefillDefaultInNextSession() throws Exception {
        Tracker tracker = db.trackers().get(0);
        FieldDefinition field = tracker.fields.get(0);
        Session previous = db.session(db.createSession(tracker.id));
        db.saveRecords(previous, Collections.singletonMap(field.id, Collections.singletonMap(field.key, 9)));
        flow.openSession(previous.id);
        find(TextInputLayout.class, layout -> "Wiederholungen".contentEquals(layout.getHint())).get(0).getEditText().setText("");

        shadowOf(Looper.getMainLooper()).idleFor(700, TimeUnit.MILLISECONDS);

        assertEquals(JSONObject.NULL, new JSONObject(db.records(previous.id).get(field.id).valuesJson).get(field.key));
        assertNull(db.previousValue(tracker.id, field.id, field.key));
        flow.openSession(db.createSession(tracker.id));
        assertEquals("", find(TextInputLayout.class, layout -> "Wiederholungen".contentEquals(layout.getHint())).get(0).getEditText().getText().toString());
    }

    @Test
    public void prefillDistinguishesNullMissingKeysAndExistingSessionValues() {
        Tracker tracker = db.trackers().get(0);
        Session previous = db.session(db.createSession(tracker.id));
        Object[] values = {9, 50.5, "Alte Notiz", 3500L};
        Map<Long, Map<String, Object>> records = new HashMap<>();
        for (int i = 0; i < tracker.fields.size(); i++) {
            FieldDefinition field = tracker.fields.get(i);
            records.put(field.id, Collections.singletonMap(field.key, values[i]));
        }
        db.saveRecords(previous, records);
        db.getWritableDatabase().execSQL("UPDATE field_records SET valuesJson=? WHERE fieldId=?",
                new Object[]{"{\"reps\":null}", tracker.fields.get(0).id});
        db.getWritableDatabase().execSQL("UPDATE field_records SET valuesJson='{}' WHERE fieldId=?",
                new Object[]{tracker.fields.get(1).id});
        long sessionId = db.createSession(tracker.id);
        flow.openSession(sessionId);

        assertEquals("", find(TextInputLayout.class, layout -> "Wiederholungen".contentEquals(layout.getHint())).get(0).getEditText().getText().toString());
        assertEquals("0.0", find(TextInputLayout.class, layout -> "Zusatzgewicht".contentEquals(layout.getHint())).get(0).getEditText().getText().toString());
        assertEquals("", find(TextInputLayout.class, layout -> "Notiz".contentEquals(layout.getHint())).get(0).getEditText().getText().toString());
        assertEquals(3500L, find(TextInputLayout.class, layout -> "Dauer".contentEquals(layout.getHint())).get(0).getEditText().getTag());
        assertEquals(0, db.recordCount(sessionId));
        FieldDefinition reps = tracker.fields.get(0);
        db.saveRecords(db.session(sessionId), Collections.singletonMap(reps.id, Collections.singletonMap(reps.key, 7)));
        flow.openSession(sessionId);
        assertEquals("7", find(TextInputLayout.class, layout -> "Wiederholungen".contentEquals(layout.getHint())).get(0).getEditText().getText().toString());
    }

    private void chooseFieldAction(int index, String label) {
        find(android.widget.TextView.class, view -> "⋮".contentEquals(view.getText())).get(index).performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        List<Button> buttons = new ArrayList<>();
        collect(dialog.getWindow().getDecorView(), Button.class, button -> label.contentEquals(button.getText()), buttons);
        assertEquals(1, buttons.size());
        buttons.get(0).performClick();
    }

    private List<EditText> fieldNames() {
        List<EditText> inputs = new ArrayList<>();
        for (TextInputLayout layout : find(TextInputLayout.class, view -> "Feldname".contentEquals(view.getHint()))) {
            inputs.add(layout.getEditText());
        }
        return inputs;
    }

    private <T extends View> List<T> find(Class<T> type, Predicate<T> predicate) {
        List<T> views = new ArrayList<>();
        collect(activity.findViewById(android.R.id.content), type, predicate, views);
        return views;
    }

    private <T extends View> void collect(View view, Class<T> type, Predicate<T> predicate, List<T> views) {
        if (type.isInstance(view) && predicate.test(type.cast(view))) {
            views.add(type.cast(view));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), type, predicate, views);
            }
        }
    }

    private void dragField(int index, int direction) {
        View content = activity.findViewById(android.R.id.content);
        content.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, 1080, 1920);
        View handle = find(View.class, view -> "Verschieben".contentEquals(
                view.getContentDescription() == null ? "" : view.getContentDescription())).get(index);
        ViewGroup container = (ViewGroup) handle.getParent().getParent().getParent();
        View sibling = container.getChildAt(index + direction);
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) sibling.getLayoutParams();
        float delta = direction * (sibling.getHeight() + margins.topMargin + margins.bottomMargin) * 0.75f;
        touch(handle, MotionEvent.ACTION_DOWN, 0);
        touch(handle, MotionEvent.ACTION_MOVE, delta);
        touch(handle, MotionEvent.ACTION_UP, delta);
    }

    private void touch(View view, int action, float y) {
        MotionEvent event = MotionEvent.obtain(0, 10, action, 0, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }
}
