package com.zaelio.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class BackupJsonRepository {
    private BackupJsonRepository() {
    }

    static String exportAll(TrackingDatabase helper) throws JSONException {
        return export(helper, true, true);
    }

    static String exportTrackers(TrackingDatabase helper) throws JSONException {
        return export(helper, true, false);
    }

    static String exportSessions(TrackingDatabase helper) throws JSONException {
        return export(helper, false, true);
    }

    private static String export(TrackingDatabase helper, boolean includeTrackers, boolean includeSessions) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("type", "zaelio-backup");
        root.put("version", 1);

        JSONArray trackers = new JSONArray();
        if (includeTrackers) for (Tracker tracker : helper.trackers()) {
            JSONObject trackerJson = new JSONObject();
            trackerJson.put("id", tracker.id);
            trackerJson.put("name", tracker.name);
            trackerJson.put("description", tracker.description == null ? "" : tracker.description);
            trackerJson.put("createdAt", tracker.createdAt);
            trackerJson.put("updatedAt", tracker.updatedAt);

            JSONArray fields = new JSONArray();
            for (FieldDefinition field : tracker.fields) {
                fields.put(JsonUtil.fieldToJson(field));
            }
            trackerJson.put("fields", fields);
            trackers.put(trackerJson);
        }
        root.put("trackers", trackers);

        JSONArray sessions = new JSONArray();
        if (includeSessions) for (Session session : helper.sessions()) {
            JSONObject sessionJson = new JSONObject();
            sessionJson.put("id", session.id);
            sessionJson.put("trackerId", session.trackerId);
            sessionJson.put("createdAt", session.createdAt);
            sessionJson.put("updatedAt", session.updatedAt);
            JSONArray records = new JSONArray();
            for (FieldRecord record : helper.records(session.id).values()) {
                JSONObject recordJson = new JSONObject();
                recordJson.put("fieldId", record.fieldId);
                recordJson.put("values", new JSONObject(record.valuesJson));
                recordJson.put("createdAt", record.createdAt);
                recordJson.put("updatedAt", record.updatedAt);
                records.put(recordJson);
            }
            sessionJson.put("records", records);
            sessions.put(sessionJson);
        }
        root.put("sessions", sessions);
        return root.toString(2);
    }

    static int importAll(TrackingDatabase helper, String json) throws JSONException {
        return importAll(helper, json, "Importierter Tracker");
    }

    static int importAll(TrackingDatabase helper, String json, String importedTrackerName) throws JSONException {
        return importData(helper, json, true, true, importedTrackerName);
    }

    static int importTrackers(TrackingDatabase helper, String json) throws JSONException {
        return importTrackers(helper, json, "Importierter Tracker");
    }

    static int importTrackers(TrackingDatabase helper, String json, String importedTrackerName) throws JSONException {
        return importData(helper, json, true, false, importedTrackerName);
    }

    static int importSessions(TrackingDatabase helper, String json) throws JSONException {
        return importData(helper, json, false, true, "Importierter Tracker");
    }

    private static int importData(TrackingDatabase helper, String json, boolean includeTrackers, boolean includeSessions, String importedTrackerName) throws JSONException {
        JSONObject root = new JSONObject(json);
        SQLiteDatabase db = helper.getWritableDatabase();
        Map<Long, Long> trackerIds = new HashMap<>();
        Map<Long, Long> fieldIds = new HashMap<>();
        int imported = 0;
        long now = System.currentTimeMillis();

        if (!includeTrackers) {
            try (Cursor trackerCursor = db.rawQuery("SELECT id FROM trackers", null)) {
                while (trackerCursor.moveToNext()) {
                    long id = trackerCursor.getLong(0);
                    trackerIds.put(id, id);
                }
            }
            try (Cursor fieldCursor = db.rawQuery("SELECT id FROM fields", null)) {
                while (fieldCursor.moveToNext()) {
                    long id = fieldCursor.getLong(0);
                    fieldIds.put(id, id);
                }
            }
        }

        db.beginTransaction();
        try {
            JSONArray trackers = root.optJSONArray("trackers");
            if (trackers == null) {
                trackers = new JSONArray();
                trackers.put(root);
            }

            if (includeTrackers) for (int i = 0; i < trackers.length(); i++) {
                JSONObject tracker = trackers.getJSONObject(i);
                ContentValues trackerValues = new ContentValues();
                trackerValues.put("name", tracker.optString("name", importedTrackerName));
                trackerValues.put("description", tracker.optString("description", ""));
                trackerValues.put("createdAt", tracker.optLong("createdAt", now));
                trackerValues.put("updatedAt", tracker.optLong("updatedAt", now));
                long newTrackerId = db.insertOrThrow("trackers", null, trackerValues);
                long oldTrackerId = tracker.optLong("id", newTrackerId);
                trackerIds.put(oldTrackerId, newTrackerId);
                imported++;

                JSONArray fields = tracker.optJSONArray("fields");
                if (fields == null) {
                    fields = new JSONArray();
                }
                for (int j = 0; j < fields.length(); j++) {
                    JSONObject field = fields.getJSONObject(j);
                    long newFieldId = db.insertOrThrow("fields", null, JsonUtil.fieldValuesFromJson(field, newTrackerId, j));
                    fieldIds.put(field.optLong("id", newFieldId), newFieldId);
                }
            }

            JSONArray sessions = root.optJSONArray("sessions");
            if (includeSessions && sessions != null) {
                for (int i = 0; i < sessions.length(); i++) {
                    JSONObject session = sessions.getJSONObject(i);
                    Long newTrackerId = trackerIds.get(session.optLong("trackerId"));
                    if (newTrackerId == null) {
                        continue;
                    }
                    ContentValues sessionValues = new ContentValues();
                    sessionValues.put("trackerId", newTrackerId);
                    sessionValues.put("createdAt", session.optLong("createdAt", now));
                    sessionValues.put("updatedAt", session.optLong("updatedAt", now));
                    long newSessionId = db.insertOrThrow("sessions", null, sessionValues);
                    if (!includeTrackers) {
                        imported++;
                    }

                    JSONArray records = session.optJSONArray("records");
                    if (records == null) {
                        continue;
                    }
                    for (int j = 0; j < records.length(); j++) {
                        JSONObject record = records.getJSONObject(j);
                        Long newFieldId = fieldIds.get(record.optLong("fieldId"));
                        if (newFieldId == null) {
                            continue;
                        }
                        JSONObject valuesJson = record.optJSONObject("values");
                        ContentValues recordValues = new ContentValues();
                        recordValues.put("sessionId", newSessionId);
                        recordValues.put("trackerId", newTrackerId);
                        recordValues.put("fieldId", newFieldId);
                        recordValues.put("fieldKey", valuesJson == null || !valuesJson.keys().hasNext() ? "" : valuesJson.keys().next());
                        recordValues.put("valuesJson", valuesJson == null ? "{}" : valuesJson.toString());
                        recordValues.put("createdAt", record.optLong("createdAt", now));
                        recordValues.put("updatedAt", record.optLong("updatedAt", now));
                        db.insertOrThrow("field_records", null, recordValues);
                    }
                }
            }

            db.setTransactionSuccessful();
            return imported;
        } finally {
            db.endTransaction();
        }
    }
}
