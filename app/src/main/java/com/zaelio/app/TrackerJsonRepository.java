package com.zaelio.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class TrackerJsonRepository {
    static long duplicateTracker(TrackingDatabase helper, long id, String unnamedTracker, String copySuffix) throws JSONException {
        Tracker tracker = helper.readTracker(id);
        if (tracker == null) {
            return -1;
        }
        JSONObject json = new JSONObject(JsonUtil.trackerToJson(tracker));
        json.put("name", (tracker.name == null || tracker.name.trim().isEmpty() ? unnamedTracker : tracker.name) + " " + copySuffix);
        return saveTracker(helper, -1, json.toString(), true);
    }

    static void updateTracker(TrackingDatabase helper, long id, String json) throws JSONException {
        saveTracker(helper, id, json, false);
    }

    static long saveTracker(TrackingDatabase helper, long id, String json, boolean createNew) throws JSONException {
        SQLiteDatabase db = helper.getWritableDatabase();
        JSONObject root = new JSONObject(json);
        long now = System.currentTimeMillis();

        db.beginTransaction();
        try {
            long trackerId = id;
            Map<Long, FieldDefinition> remainingFields = new HashMap<>();
            if (!createNew) {
                Tracker existing = helper.readTracker(db, id);
                if (existing == null) {
                    throw new JSONException("Tracker not found: " + id);
                }
                for (FieldDefinition field : existing.fields) {
                    remainingFields.put(field.id, field);
                }
            }
            ContentValues trackerValues = new ContentValues();
            trackerValues.put("name", root.getString("name"));
            trackerValues.put("description", root.optString("description", ""));
            trackerValues.put("updatedAt", now);
            if (createNew) {
                trackerValues.put("createdAt", now);
                trackerId = db.insertOrThrow("trackers", null, trackerValues);
            } else {
                db.update("trackers", trackerValues, "id=?", new String[]{String.valueOf(id)});
            }

            JSONArray fields = createNew ? root.optJSONArray("fields") : root.getJSONArray("fields");
            if (fields == null) {
                fields = new JSONArray();
            }
            Set<String> usedKeys = new HashSet<>();
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                ContentValues values = JsonUtil.fieldValuesFromJson(field, trackerId, i);
                String key = field.getString("key");
                if (key.trim().isEmpty() || !usedKeys.add(key)) {
                    throw new JSONException("Empty or duplicate field key: " + key);
                }
                FieldDefinition existing = null;
                if (!createNew) {
                    long fieldId = field.getLong("id");
                    existing = remainingFields.remove(fieldId);
                    if (fieldId != 0 && existing == null) {
                        throw new JSONException("Unknown or duplicate field ID: " + fieldId);
                    }
                }
                if (existing == null) {
                    db.insertOrThrow("fields", null, values);
                } else {
                    db.update("fields", values, "id=?", new String[]{String.valueOf(existing.id)});
                    if (!existing.key.equals(key)) {
                        renameRecordKey(db, existing.id, existing.key, key);
                    }
                }
            }
            for (long fieldId : remainingFields.keySet()) {
                String[] args = new String[]{String.valueOf(fieldId)};
                db.delete("field_records", "fieldId=?", args);
                db.delete("fields", "id=?", args);
            }

            db.setTransactionSuccessful();
            return trackerId;
        } finally {
            db.endTransaction();
        }
    }

    private static void renameRecordKey(SQLiteDatabase db, long fieldId, String oldKey, String newKey) throws JSONException {
        try (Cursor cursor = db.rawQuery("SELECT id,valuesJson FROM field_records WHERE fieldId=?",
                new String[]{String.valueOf(fieldId)})) {
            while (cursor.moveToNext()) {
                JSONObject json = new JSONObject(cursor.getString(1));
                if (json.has(oldKey)) {
                    json.put(newKey, json.remove(oldKey));
                }
                ContentValues values = new ContentValues();
                values.put("fieldKey", newKey);
                values.put("valuesJson", json.toString());
                db.update("field_records", values, "id=?", new String[]{String.valueOf(cursor.getLong(0))});
            }
        }
    }
}
