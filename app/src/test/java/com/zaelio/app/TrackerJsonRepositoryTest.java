package com.zaelio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.database.Cursor;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TrackerJsonRepositoryTest {
    private Context context;
    private TrackingDatabase db;
    private Tracker tracker;
    private final List<Session> sessions = new ArrayList<>();
    private final Map<Long, Map<Long, FieldRecord>> originalRecords = new HashMap<>();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("tracking.sqlite");
        db = new TrackingDatabase(context);
        tracker = db.trackers().get(0);
        for (int i = 0; i < 2; i++) {
            Session session = db.session(db.createSession(tracker.id));
            Object[] values = {12 + i, 2.5 + i, "Notiz " + i, 60000 + i};
            Map<Long, Map<String, Object>> records = new HashMap<>();
            for (int j = 0; j < tracker.fields.size(); j++) {
                FieldDefinition field = tracker.fields.get(j);
                records.put(field.id, Collections.singletonMap(field.key, values[j]));
            }
            db.saveRecords(session, records);
            sessions.add(db.session(session.id));
            originalRecords.put(session.id, db.records(session.id));
        }
    }

    @After
    public void tearDown() {
        db.close();
        context.deleteDatabase("tracking.sqlite");
    }

    @Test
    public void reorderAndMetadataEditsKeepFieldIdsAndAllSessionRecordsAfterReopen() throws Exception {
        db.getWritableDatabase().execSQL("UPDATE trackers SET createdAt=123 WHERE id=?", new Object[]{tracker.id});
        JSONObject json = trackerJson();
        JSONArray fields = json.getJSONArray("fields");
        JSONArray reversed = new JSONArray();
        for (int i = fields.length() - 1; i >= 0; i--) {
            reversed.put(fields.getJSONObject(i).put("order", fields.length() - 1 - i));
        }
        json.put("fields", reversed).put("name", "Renamed tracker").put("description", "New description");
        reversed.getJSONObject(0).put("required", true).put("increment", 5).put("defaultValue", JSONObject.NULL);

        update(json);
        update(json);
        db.close();
        db = new TrackingDatabase(context);

        Tracker saved = db.readTracker(tracker.id);
        assertEquals("Renamed tracker", saved.name);
        assertEquals("New description", saved.description);
        assertEquals(123L, saved.createdAt);
        for (int i = 0; i < saved.fields.size(); i++) {
            assertEquals(tracker.fields.get(saved.fields.size() - 1 - i).id, saved.fields.get(i).id);
            assertEquals(i, saved.fields.get(i).order);
        }
        assertEquals(null, saved.fields.get(0).defaultValue);
        assertEquals(5, saved.fields.get(0).increment, 0);
        assertEquals(true, saved.fields.get(0).required);
        assertOriginalRecords(tracker.fields);
    }

    @Test
    public void renameMigratesJsonAndRecordKeysWithoutChangingValuesOrTimestamps() throws Exception {
        FieldDefinition field = tracker.fields.get(0);
        JSONObject json = trackerJson();
        json.getJSONArray("fields").getJSONObject(0).put("label", "Anzahl").put("key", "anzahl");

        update(json);

        assertEquals(field.id, db.readTracker(tracker.id).fields.get(0).id);
        for (Session session : sessions) {
            FieldRecord before = originalRecords.get(session.id).get(field.id);
            FieldRecord after = db.records(session.id).get(field.id);
            assertRecordIdentity(before, after);
            JSONObject values = new JSONObject(after.valuesJson);
            assertFalse(values.has(field.key));
            assertEquals(new JSONObject(before.valuesJson).get(field.key), values.get("anzahl"));
            try (Cursor cursor = db.getReadableDatabase().rawQuery("SELECT fieldKey FROM field_records WHERE id=?",
                    new String[]{String.valueOf(after.id)})) {
                cursor.moveToFirst();
                assertEquals("anzahl", cursor.getString(0));
            }
        }
        assertNotNull(db.previousValue(tracker.id, field.id, "anzahl"));
        assertSame(TrackingDatabase.NO_PREVIOUS, db.previousValue(tracker.id, field.id, field.key));
        assertOriginalRecords(tracker.fields.subList(1, tracker.fields.size()));
    }

    @Test
    public void swappingKeysKeepsValuesAttachedToFieldIds() throws Exception {
        JSONObject json = trackerJson();
        JSONArray fields = json.getJSONArray("fields");
        fields.getJSONObject(0).put("key", tracker.fields.get(1).key);
        fields.getJSONObject(1).put("key", tracker.fields.get(0).key);

        update(json);

        for (Session session : sessions) {
            for (int i = 0; i < 2; i++) {
                FieldDefinition field = tracker.fields.get(i);
                FieldRecord before = originalRecords.get(session.id).get(field.id);
                FieldRecord after = db.records(session.id).get(field.id);
                assertRecordIdentity(before, after);
                assertEquals(new JSONObject(before.valuesJson).get(field.key),
                        new JSONObject(after.valuesJson).get(tracker.fields.get(1 - i).key));
            }
        }
        assertOriginalRecords(tracker.fields.subList(2, tracker.fields.size()));
    }

    @Test
    public void renamePreservesExplicitNullAndMissingValues() throws Exception {
        FieldDefinition field = tracker.fields.get(0);
        db.getWritableDatabase().execSQL("UPDATE field_records SET valuesJson=? WHERE fieldId=? AND sessionId=?",
                new Object[]{"{\"reps\":null}", field.id, sessions.get(0).id});
        db.getWritableDatabase().execSQL("UPDATE field_records SET valuesJson='{}' WHERE fieldId=? AND sessionId=?",
                new Object[]{field.id, sessions.get(1).id});
        JSONObject json = trackerJson();
        json.getJSONArray("fields").getJSONObject(0).put("key", "count");

        update(json);

        assertEquals(JSONObject.NULL, new JSONObject(db.records(sessions.get(0).id).get(field.id).valuesJson).get("count"));
        assertEquals("{}", db.records(sessions.get(1).id).get(field.id).valuesJson);
        assertOriginalRecords(tracker.fields.subList(1, tracker.fields.size()));
    }

    @Test
    public void addingAndRemovingFieldsOnlyRemovesRecordsOfDeletedField() throws Exception {
        long otherId = TrackerJsonRepository.duplicateTracker(db, tracker.id, "Unnamed", "Copy");
        FieldDefinition otherField = db.readTracker(otherId).fields.get(0);
        Session otherSession = db.session(db.createSession(otherId));
        db.saveRecords(otherSession, Collections.singletonMap(otherField.id, Collections.singletonMap(otherField.key, 99)));
        FieldRecord otherRecord = db.records(otherSession.id).get(otherField.id);
        JSONObject json = trackerJson();
        JSONArray fields = json.getJSONArray("fields");
        long removedId = fields.getJSONObject(1).getLong("id");
        fields.remove(1);
        fields.put(new JSONObject().put("id", 0).put("key", "extra").put("label", "Extra"));

        update(json);

        for (Session session : sessions) {
            assertEquals(tracker.fields.size() - 1, db.recordCount(session.id));
            assertFalse(db.records(session.id).containsKey(removedId));
        }
        List<FieldDefinition> retained = new ArrayList<>(tracker.fields);
        retained.remove(1);
        assertOriginalRecords(retained);
        assertEquals(otherRecord.valuesJson, db.records(otherSession.id).get(otherField.id).valuesJson);
        assertRecordIdentity(otherRecord, db.records(otherSession.id).get(otherField.id));
        assertEquals(4, db.readTracker(tracker.id).fields.size());
        assertNotEquals(removedId, db.readTracker(tracker.id).fields.get(3).id);
    }

    @Test
    public void updateWithoutFieldIdRollsBackMetadataFieldsAndRecords() throws Exception {
        JSONObject json = trackerJson();
        JSONArray fields = json.getJSONArray("fields");
        fields.getJSONObject(0).put("key", "renamed").put("order", 3);
        fields.getJSONObject(1).remove("id");
        json.put("name", "Should roll back");

        assertThrows(JSONException.class, () -> update(json));

        assertEquals(JsonUtil.trackerToJson(tracker), JsonUtil.trackerToJson(db.readTracker(tracker.id)));
        assertOriginalRecords(tracker.fields);
    }

    @Test
    public void duplicateAndImportUseNewIdsAndNeverModifyOriginalSessions() throws Exception {
        long copyId = TrackerJsonRepository.duplicateTracker(db, tracker.id, "Unnamed", "Copy");
        assertEquals(2, sessions.size());
        assertEquals(2, db.sessions().size());
        for (int i = 0; i < tracker.fields.size(); i++) {
            assertNotEquals(tracker.fields.get(i).id, db.readTracker(copyId).fields.get(i).id);
        }
        BackupJsonRepository.importAll(db, BackupJsonRepository.exportAll(db));
        Session importedSession = db.sessions().get(2);
        Tracker imported = db.readTracker(importedSession.trackerId);
        JSONObject json = new JSONObject(JsonUtil.trackerToJson(imported));
        json.getJSONArray("fields").getJSONObject(0).put("order", 10);
        TrackerJsonRepository.updateTracker(db, imported.id, json.toString());

        assertEquals(4, db.recordCount(importedSession.id));
        assertOriginalRecords(tracker.fields);
        JSONObject backup = new JSONObject(BackupJsonRepository.exportAll(db));
        assertEquals(4, backup.getJSONArray("sessions").length());
    }

    @Test
    public void invalidUpdatesRollBackMetadataFieldsAndRecords() throws Exception {
        JSONObject json = trackerJson();
        JSONArray fields = json.getJSONArray("fields");
        fields.getJSONObject(0).put("key", "renamed");
        fields.getJSONObject(1).put("id", fields.getJSONObject(0).getLong("id"));
        json.put("name", "Should roll back");
        assertThrows(JSONException.class, () -> update(json));

        long otherId = TrackerJsonRepository.duplicateTracker(db, tracker.id, "Unnamed", "Copy");
        fields.getJSONObject(1).put("id", db.readTracker(otherId).fields.get(0).id);
        assertThrows(JSONException.class, () -> update(json));

        fields.getJSONObject(1).put("id", tracker.fields.get(1).id).put("key", "renamed");
        assertThrows(JSONException.class, () -> update(json));

        fields.getJSONObject(1).put("key", "");
        assertThrows(JSONException.class, () -> update(json));

        fields.getJSONObject(1).remove("key");
        assertThrows(JSONException.class, () -> update(json));

        json.remove("fields");
        assertThrows(JSONException.class, () -> update(json));

        assertEquals(JsonUtil.trackerToJson(tracker), JsonUtil.trackerToJson(db.readTracker(tracker.id)));
        assertOriginalRecords(tracker.fields);
    }

    @Test
    public void failedCreationRollsBackNewTrackerAndFieldsAndAllowsRetry() throws Exception {
        String before = BackupJsonRepository.exportAll(db);
        JSONObject json = trackerJson().put("name", "New tracker");
        json.remove("description");
        json.getJSONArray("fields").getJSONObject(1).remove("key");

        assertThrows(JSONException.class, () -> TrackerJsonRepository.saveTracker(db, -1, json.toString(), true));

        assertFalse(db.getWritableDatabase().inTransaction());
        assertEquals(before, BackupJsonRepository.exportAll(db));
        json.getJSONArray("fields").getJSONObject(1).put("key", tracker.fields.get(1).key);
        long createdId = TrackerJsonRepository.saveTracker(db, -1, json.toString(), true);
        Tracker created = db.readTracker(createdId);
        assertEquals("New tracker", created.name);
        assertEquals("", created.description);
        assertNotEquals(0L, created.createdAt);
        assertEquals(created.createdAt, created.updatedAt);
        for (int i = 0; i < tracker.fields.size(); i++) {
            assertNotEquals(tracker.fields.get(i).id, created.fields.get(i).id);
        }
        assertOriginalRecords(tracker.fields);
    }

    @Test
    public void missingTrackerOperationsLeaveDatabaseUnchanged() throws Exception {
        String before = BackupJsonRepository.exportAll(db);

        assertThrows(JSONException.class,
                () -> TrackerJsonRepository.updateTracker(db, -123, trackerJson().toString()));
        assertEquals(-1L, TrackerJsonRepository.duplicateTracker(db, -123, "Unnamed", "Copy"));

        assertNull(db.readTracker(-123));
        assertFalse(db.getWritableDatabase().inTransaction());
        assertEquals(before, BackupJsonRepository.exportAll(db));
    }

    @Test
    public void removingAllFieldsKeepsSessionsAndTheirTimestamps() throws Exception {
        JSONObject json = trackerJson().put("fields", new JSONArray());

        update(json);
        update(json);

        assertNotNull(db.readTracker(tracker.id));
        assertEquals(0, db.readTracker(tracker.id).fields.size());
        assertEquals(sessions.size(), db.sessions().size());
        for (Session session : sessions) {
            assertEquals(0, db.recordCount(session.id));
            assertEquals(session.trackerId, db.session(session.id).trackerId);
        }
        assertOriginalRecords(Collections.emptyList());
        for (FieldDefinition field : tracker.fields) {
            assertSame(TrackingDatabase.NO_PREVIOUS, db.previousValue(tracker.id, field.id, field.key));
        }
    }

    @Test
    public void malformedSavedJsonRollsBackEarlierRenamesAndMetadata() throws Exception {
        FieldDefinition weight = tracker.fields.get(1);
        FieldRecord damaged = originalRecords.get(sessions.get(1).id).get(weight.id);
        String originalJson = damaged.valuesJson;
        damaged.valuesJson = "not-json";
        db.getWritableDatabase().execSQL("UPDATE field_records SET valuesJson=? WHERE id=?",
                new Object[]{damaged.valuesJson, damaged.id});
        JSONObject json = trackerJson().put("name", "Should roll back");
        json.getJSONArray("fields").getJSONObject(0).put("key", "count");
        json.getJSONArray("fields").getJSONObject(1).put("key", "load");

        assertThrows(JSONException.class, () -> update(json));

        assertFalse(db.getWritableDatabase().inTransaction());
        assertEquals(JsonUtil.trackerToJson(tracker), JsonUtil.trackerToJson(db.readTracker(tracker.id)));
        assertOriginalRecords(tracker.fields);
        db.getWritableDatabase().execSQL("UPDATE field_records SET valuesJson=? WHERE id=?",
                new Object[]{originalJson, damaged.id});
        update(json);
        assertEquals(new JSONObject(originalJson).get(weight.key),
                new JSONObject(db.records(damaged.sessionId).get(weight.id).valuesJson).get("load"));
        for (Session session : sessions) {
            assertEquals(4, db.recordCount(session.id));
        }
    }

    private JSONObject trackerJson() throws JSONException {
        return new JSONObject(JsonUtil.trackerToJson(tracker));
    }

    private void update(JSONObject json) throws JSONException {
        TrackerJsonRepository.updateTracker(db, tracker.id, json.toString());
    }

    private void assertOriginalRecords(List<FieldDefinition> fields) {
        for (Session session : sessions) {
            Map<Long, FieldRecord> saved = db.records(session.id);
            for (FieldDefinition field : fields) {
                FieldRecord before = originalRecords.get(session.id).get(field.id);
                FieldRecord after = saved.get(field.id);
                assertRecordIdentity(before, after);
                assertEquals(before.valuesJson, after.valuesJson);
            }
            assertEquals(session.createdAt, db.session(session.id).createdAt);
            assertEquals(session.updatedAt, db.session(session.id).updatedAt);
        }
    }

    private void assertRecordIdentity(FieldRecord before, FieldRecord after) {
        assertNotNull(after);
        assertEquals(before.id, after.id);
        assertEquals(before.fieldId, after.fieldId);
        assertEquals(before.sessionId, after.sessionId);
        assertEquals(before.trackerId, after.trackerId);
        assertEquals(before.createdAt, after.createdAt);
        assertEquals(before.updatedAt, after.updatedAt);
    }
}
