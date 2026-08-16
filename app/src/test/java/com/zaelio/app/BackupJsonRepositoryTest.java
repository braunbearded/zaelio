package com.zaelio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupJsonRepositoryTest {
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
    public void exportAllIncludesTrackersSessionsAndRecords() throws Exception {
        Tracker tracker = db.trackers().get(0);
        FieldDefinition field = tracker.fields.get(0);
        Session session = db.session(db.createSession(tracker.id));
        Map<String, Object> values = new HashMap<>();
        values.put(field.key, 15);
        Map<Long, Map<String, Object>> valuesByFieldId = new HashMap<>();
        valuesByFieldId.put(field.id, values);
        db.saveRecords(session, valuesByFieldId);

        JSONObject export = new JSONObject(BackupJsonRepository.exportAll(db));

        assertEquals("zaelio-backup", export.getString("type"));
        assertEquals(1, export.getJSONArray("trackers").length());
        assertEquals(1, export.getJSONArray("sessions").length());
        assertEquals(1, export.getJSONArray("sessions").getJSONObject(0).getJSONArray("records").length());
    }

    @Test
    public void importAllRecreatesExportedDataWithNewIds() throws Exception {
        Tracker tracker = db.trackers().get(0);
        FieldDefinition field = tracker.fields.get(0);
        Session session = db.session(db.createSession(tracker.id));
        Map<String, Object> values = new HashMap<>();
        values.put(field.key, 20);
        Map<Long, Map<String, Object>> valuesByFieldId = new HashMap<>();
        valuesByFieldId.put(field.id, values);
        db.saveRecords(session, valuesByFieldId);
        String json = BackupJsonRepository.exportAll(db);

        db.deleteTracker(tracker.id);
        int imported = BackupJsonRepository.importAll(db, json);
        Tracker importedTracker = db.trackers().get(0);
        Session importedSession = db.sessions().get(0);

        assertEquals(1, imported);
        assertEquals("Training", importedTracker.name);
        assertEquals(importedTracker.id, importedSession.trackerId);
        assertEquals(1, db.recordCount(importedSession.id));
        assertTrue(BackupJsonRepository.exportSessions(db).contains("sessions"));
    }

    @Test
    public void trackerOnlyExportSkipsSessions() throws Exception {
        Tracker tracker = db.trackers().get(0);
        db.createSession(tracker.id);

        JSONObject export = new JSONObject(BackupJsonRepository.exportTrackers(db));

        assertEquals(1, export.getJSONArray("trackers").length());
        assertEquals(0, export.getJSONArray("sessions").length());
    }

    @Test
    public void exportVariantsMatchExampleJson() throws Exception {
        resetDb();
        BackupJsonRepository.importAll(db, fixture("backup-fixtures/all.json"));
        assertJsonEquals(fixture("backup-fixtures/all.json"), BackupJsonRepository.exportAll(db));
        assertJsonEquals(fixture("backup-fixtures/trackers.json"), BackupJsonRepository.exportTrackers(db));
        assertJsonEquals(fixture("backup-fixtures/sessions.json"), BackupJsonRepository.exportSessions(db));
    }

    @Test
    public void importVariantsLoadExampleJson() throws Exception {
        resetDb();
        assertEquals(1, BackupJsonRepository.importAll(db, fixture("backup-fixtures/all.json")));
        assertEquals(1, db.trackers().size());
        assertEquals(1, db.sessions().size());
        assertEquals(1, db.recordCount(db.sessions().get(0).id));

        resetDb();
        assertEquals(1, BackupJsonRepository.importTrackers(db, fixture("backup-fixtures/trackers.json")));
        assertEquals(1, db.trackers().size());
        assertEquals(0, db.sessions().size());

        resetDb();
        BackupJsonRepository.importTrackers(db, fixture("backup-fixtures/trackers.json"));
        assertEquals(1, BackupJsonRepository.importSessions(db, fixture("backup-fixtures/sessions.json")));
        assertEquals(1, db.trackers().size());
        assertEquals(1, db.sessions().size());
        assertEquals(1, db.recordCount(db.sessions().get(0).id));
    }

    private void resetDb() {
        SQLiteDatabase writable = db.getWritableDatabase();
        writable.delete("field_records", null, null);
        writable.delete("sessions", null, null);
        writable.delete("fields", null, null);
        writable.delete("trackers", null, null);
        writable.delete("sqlite_sequence", null, null);
    }

    private String fixture(String path) throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int read; (read = input.read(buffer)) != -1; ) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private void assertJsonEquals(String expected, String actual) throws Exception {
        assertEquals(new JSONObject(expected).toString(), new JSONObject(actual).toString());
    }
}
