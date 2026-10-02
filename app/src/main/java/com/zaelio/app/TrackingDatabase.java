package com.zaelio.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

final class TrackingDatabase extends SQLiteOpenHelper {
    static final Object NO_PREVIOUS = new Object();

    TrackingDatabase(Context context) {
        super(context, "tracking.sqlite", null, 8);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,description TEXT,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,overviewOrder INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE fields(id INTEGER PRIMARY KEY AUTOINCREMENT,trackerId INTEGER NOT NULL,fieldKey TEXT NOT NULL,label TEXT NOT NULL,type TEXT NOT NULL,sortOrder INTEGER NOT NULL,defaultValue TEXT,incrementValue REAL,required INTEGER NOT NULL,prefillFromPrevious INTEGER NOT NULL,FOREIGN KEY(trackerId) REFERENCES trackers(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,trackerId INTEGER NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,overviewOrder INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(trackerId) REFERENCES trackers(id))");
        db.execSQL("CREATE TABLE field_records(id INTEGER PRIMARY KEY AUTOINCREMENT,sessionId INTEGER NOT NULL,trackerId INTEGER NOT NULL,fieldId INTEGER NOT NULL,fieldKey TEXT NOT NULL,valuesJson TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,UNIQUE(sessionId,fieldId),FOREIGN KEY(sessionId) REFERENCES sessions(id),FOREIGN KEY(fieldId) REFERENCES fields(id))");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS item_records");
        db.execSQL("DROP TABLE IF EXISTS items");
        db.execSQL("DROP TABLE IF EXISTS field_records");
        db.execSQL("DROP TABLE IF EXISTS sessions");
        db.execSQL("DROP TABLE IF EXISTS fields");
        db.execSQL("DROP TABLE IF EXISTS trackers");
        onCreate(db);
    }

    private void seed(SQLiteDatabase db) {
        long trackerId = insertTracker(db, "Training", "Beispiel-Tracker");
        insertField(db, trackerId, "reps", "Wiederholungen", "int", 0, "8", 1, true);
        insertField(db, trackerId, "weight", "Zusatzgewicht", "float", 1, "0", 2.5, true);
        insertField(db, trackerId, "note", "Notiz", "string", 2, "", 1, false);
        insertField(db, trackerId, "duration", "Dauer", "duration", 3, "60000", 1, true);
    }

    long insertTracker(SQLiteDatabase db, String name, String desc) {
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        values.put("name", name);
        values.put("description", desc);
        values.put("createdAt", now);
        values.put("updatedAt", now);
        values.put("overviewOrder", nextOverviewOrder(db, "trackers"));
        return db.insert("trackers", null, values);
    }

    long insertField(
            SQLiteDatabase db,
            long trackerId,
            String key,
            String label,
            String type,
            int order,
            String def,
            double inc,
            boolean prefillFromPrevious) {
        ContentValues values = new ContentValues();
        values.put("trackerId", trackerId);
        values.put("fieldKey", key);
        values.put("label", label);
        values.put("type", type);
        values.put("sortOrder", order);
        values.put("defaultValue", def);
        values.put("incrementValue", inc);
        values.put("required", 0);
        values.put("prefillFromPrevious", prefillFromPrevious ? 1 : 0);
        return db.insert("fields", null, values);
    }

    List<Tracker> trackers() {
        SQLiteDatabase db = getReadableDatabase();
        List<Tracker> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT id FROM trackers ORDER BY overviewOrder,id", null)) {
            while (cursor.moveToNext()) {
                list.add(readTracker(db, cursor.getLong(0)));
            }
        }
        return list;
    }

    Tracker readTracker(long id) {
        return readTracker(getReadableDatabase(), id);
    }

    Tracker readTracker(SQLiteDatabase db, long id) {
        try (Cursor trackerCursor = db.rawQuery(
                "SELECT id,name,description,createdAt,updatedAt FROM trackers WHERE id=?",
                new String[]{String.valueOf(id)})) {
            if (!trackerCursor.moveToFirst()) {
                return null;
            }

            Tracker tracker = new Tracker();
            tracker.id = trackerCursor.getLong(0);
            tracker.name = trackerCursor.getString(1);
            tracker.description = trackerCursor.getString(2);
            tracker.createdAt = trackerCursor.getLong(3);
            tracker.updatedAt = trackerCursor.getLong(4);

            try (Cursor fieldCursor = db.rawQuery(
                    "SELECT id,trackerId,fieldKey,label,type,sortOrder,defaultValue,incrementValue,required,prefillFromPrevious FROM fields WHERE trackerId=? ORDER BY sortOrder,id",
                    new String[]{String.valueOf(id)})) {
                while (fieldCursor.moveToNext()) {
                    FieldDefinition definition = new FieldDefinition();
                    definition.id = fieldCursor.getLong(0);
                    definition.trackerId = fieldCursor.getLong(1);
                    definition.key = fieldCursor.getString(2);
                    definition.label = fieldCursor.getString(3);
                    definition.type = fieldCursor.getString(4);
                    definition.order = fieldCursor.getInt(5);
                    definition.defaultValue = fieldCursor.getString(6);
                    definition.increment = fieldCursor.getDouble(7);
                    definition.required = fieldCursor.getInt(8) == 1;
                    definition.prefillFromPrevious = fieldCursor.getInt(9) == 1;
                    tracker.fields.add(definition);
                }
            }

            return tracker;
        }
    }

    List<Session> sessions() {
        List<Session> sessions = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,trackerId,createdAt,updatedAt FROM sessions ORDER BY overviewOrder,id",
                null)) {
            while (cursor.moveToNext()) {
                Session session = new Session();
                session.id = cursor.getLong(0);
                session.trackerId = cursor.getLong(1);
                session.createdAt = cursor.getLong(2);
                session.updatedAt = cursor.getLong(3);
                sessions.add(session);
            }
        }
        return sessions;
    }

    Session session(long id) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,trackerId,createdAt,updatedAt FROM sessions WHERE id=?",
                new String[]{String.valueOf(id)})) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            Session session = new Session();
            session.id = cursor.getLong(0);
            session.trackerId = cursor.getLong(1);
            session.createdAt = cursor.getLong(2);
            session.updatedAt = cursor.getLong(3);
            return session;
        }
    }

    long createSession(long trackerId) {
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        values.put("trackerId", trackerId);
        values.put("createdAt", now);
        values.put("updatedAt", now);
        values.put("overviewOrder", nextOverviewOrder(getWritableDatabase(), "sessions"));
        return getWritableDatabase().insert("sessions", null, values);
    }

    void reorderTrackers(List<Long> ids) {
        reorderOverview("trackers", ids);
    }

    void reorderSessions(List<Long> ids) {
        reorderOverview("sessions", ids);
    }

    private void reorderOverview(String table, List<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                ContentValues values = new ContentValues();
                values.put("overviewOrder", i);
                db.update(table, values, "id=?", new String[]{String.valueOf(ids.get(i))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private long nextOverviewOrder(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COALESCE(MIN(overviewOrder),0)-1 FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    void deleteSession(long sessionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("field_records", "sessionId=?", new String[]{String.valueOf(sessionId)});
            db.delete("sessions", "id=?", new String[]{String.valueOf(sessionId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void deleteTracker(long trackerId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("field_records", "trackerId=?", new String[]{String.valueOf(trackerId)});
            db.delete("sessions", "trackerId=?", new String[]{String.valueOf(trackerId)});
            db.delete("fields", "trackerId=?", new String[]{String.valueOf(trackerId)});
            db.delete("trackers", "id=?", new String[]{String.valueOf(trackerId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    Map<Long, FieldRecord> records(long sessionId) {
        Map<Long, FieldRecord> records = new HashMap<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,sessionId,trackerId,fieldId,valuesJson,createdAt,updatedAt FROM field_records WHERE sessionId=?",
                new String[]{String.valueOf(sessionId)})) {
            while (cursor.moveToNext()) {
                FieldRecord record = new FieldRecord();
                record.id = cursor.getLong(0);
                record.sessionId = cursor.getLong(1);
                record.trackerId = cursor.getLong(2);
                record.fieldId = cursor.getLong(3);
                record.valuesJson = cursor.getString(4);
                record.createdAt = cursor.getLong(5);
                record.updatedAt = cursor.getLong(6);
                records.put(record.fieldId, record);
            }
        }
        return records;
    }

    void saveRecords(Session session, Map<Long, Map<String, Object>> valuesByFieldId) {
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map.Entry<Long, Map<String, Object>> entry : valuesByFieldId.entrySet()) {
                saveRecordRow(db, session, entry.getKey(), entry.getValue(), now);
            }
            touchSession(db, session.id, now);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void saveRecordRow(SQLiteDatabase db, Session session, long fieldId, Map<String, Object> values, long now) {
        ContentValues valuesToSave = new ContentValues();
        valuesToSave.put("sessionId", session.id);
        valuesToSave.put("trackerId", session.trackerId);
        valuesToSave.put("fieldId", fieldId);
        valuesToSave.put("fieldKey", values.isEmpty() ? "" : values.keySet().iterator().next());
        valuesToSave.put("valuesJson", JsonUtil.stringify(values));
        valuesToSave.put("updatedAt", now);
        valuesToSave.put("createdAt", now);
        db.insertWithOnConflict("field_records", null, valuesToSave, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void touchSession(SQLiteDatabase db, long sessionId, long now) {
        ContentValues sessionValues = new ContentValues();
        sessionValues.put("updatedAt", now);
        db.update("sessions", sessionValues, "id=?", new String[]{String.valueOf(sessionId)});
    }

    Object previousValue(long trackerId, long fieldId, String key) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT valuesJson FROM field_records WHERE trackerId=? AND fieldId=? ORDER BY updatedAt DESC LIMIT 1",
                new String[]{String.valueOf(trackerId), String.valueOf(fieldId)})) {
            if (!cursor.moveToFirst()) {
                return NO_PREVIOUS;
            }

            JSONObject object = new JSONObject(cursor.getString(0));
            if (!object.has(key)) {
                return NO_PREVIOUS;
            }
            return object.isNull(key) ? null : object.get(key);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    int recordCount(long sessionId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM field_records WHERE sessionId=?",
                new String[]{String.valueOf(sessionId)})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

}
