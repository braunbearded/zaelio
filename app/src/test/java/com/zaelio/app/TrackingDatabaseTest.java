package com.zaelio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TrackingDatabaseTest {
    private Context context;
    private TrackingDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("tracking.sqlite");
        db = new TrackingDatabase(context);
    }

    @After
    public void tearDown() {
        db.close();
        context.deleteDatabase("tracking.sqlite");
    }

    @Test
    public void seedCreatesTrainingTrackerWithFields() {
        Tracker tracker = db.trackers().get(0);

        assertEquals("Training", tracker.name);
        assertEquals(4, tracker.fields.size());
    }

    @Test
    public void sessionsRecordsPreviousValueAndDeletesWork() {
        Tracker tracker = db.trackers().get(0);
        FieldDefinition reps = tracker.fields.get(0);
        long sessionId = db.createSession(tracker.id);
        Session session = db.session(sessionId);
        Map<String, Object> values = new HashMap<>();
        values.put(reps.key, 10);

        db.saveRecord(session, reps.id, values);

        assertEquals(1, db.recordCount(sessionId));
        assertEquals(10, ((Number) db.previousValue(tracker.id, reps.id, reps.key)).intValue());
        db.deleteSession(sessionId);
        assertEquals(0, db.sessions().size());
        assertSame(TrackingDatabase.NO_PREVIOUS, db.previousValue(tracker.id, reps.id, reps.key));
    }

    @Test
    public void saveRecordsWritesFieldsInOneCall() {
        Tracker tracker = db.trackers().get(0);
        long sessionId = db.createSession(tracker.id);
        Session session = db.session(sessionId);
        Map<Long, Map<String, Object>> valuesByFieldId = new HashMap<>();
        for (FieldDefinition field : tracker.fields) {
            Map<String, Object> values = new HashMap<>();
            values.put(field.key, field.key);
            valuesByFieldId.put(field.id, values);
        }

        db.saveRecords(session, valuesByFieldId);

        assertEquals(tracker.fields.size(), db.recordCount(sessionId));
    }

    @Test
    public void deleteTrackerRemovesSessionsToo() {
        Tracker tracker = db.trackers().get(0);
        long sessionId = db.createSession(tracker.id);

        db.deleteTracker(tracker.id);

        assertEquals(0, db.trackers().size());
        assertEquals(0, db.sessions().size());
        assertEquals(0, db.recordCount(sessionId));
    }

    @Test
    public void createSessionUsesNewId() {
        Tracker tracker = db.trackers().get(0);

        long first = db.createSession(tracker.id);
        long second = db.createSession(tracker.id);

        assertNotEquals(first, second);
        assertNotNull(db.session(second));
    }

    @Test
    public void overviewOrderPersistsForTrackersAndSessions() {
        Tracker seed = db.trackers().get(0);
        long secondTrackerId = db.insertTracker(db.getWritableDatabase(), "Second", "");
        long firstSessionId = db.createSession(seed.id);
        long secondSessionId = db.createSession(seed.id);

        assertEquals(secondTrackerId, db.trackers().get(0).id);
        assertEquals(secondSessionId, db.sessions().get(0).id);

        db.reorderTrackers(Arrays.asList(seed.id, secondTrackerId));
        db.reorderSessions(Arrays.asList(firstSessionId, secondSessionId));
        db.close();
        db = new TrackingDatabase(context);

        assertEquals(seed.id, db.trackers().get(0).id);
        assertEquals(secondTrackerId, db.trackers().get(1).id);
        assertEquals(firstSessionId, db.sessions().get(0).id);
        assertEquals(secondSessionId, db.sessions().get(1).id);
    }

    @Test
    public void schemaHasNoItemTablesOrFieldColumns() {
        SQLiteDatabase sqlite = db.getReadableDatabase();
        Cursor tables = sqlite.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('items','item_records')", null);
        try {
            tables.moveToFirst();
            assertEquals(0, tables.getInt(0));
        } finally {
            tables.close();
        }
        Cursor columns = sqlite.rawQuery("PRAGMA table_info(fields)", null);
        try {
            while (columns.moveToNext()) {
                String name = columns.getString(1);
                assertNotEquals("itemTitle", name);
                assertNotEquals("itemOrder", name);
                assertNotEquals("itemId", name);
                assertNotEquals("u" + "nit", name);
            }
        } finally {
            columns.close();
        }
    }

    @Test
    public void upgradeDropsOldDatabaseAndSeedsCleanSchema() {
        db.close();
        context.deleteDatabase("tracking.sqlite");
        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("tracking.sqlite"), null);
        old.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,description TEXT,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,trackerId INTEGER NOT NULL,title TEXT NOT NULL,sortOrder INTEGER NOT NULL)");
        old.execSQL("INSERT INTO trackers(id,name,description,createdAt,updatedAt) VALUES(1,'Old','',100,100)");
        old.execSQL("INSERT INTO items(id,trackerId,title,sortOrder) VALUES(1,1,'Old item',0)");
        old.setVersion(6);
        old.close();

        db = new TrackingDatabase(context);

        assertEquals("Training", db.trackers().get(0).name);
        assertEquals(4, db.trackers().get(0).fields.size());
    }
}
