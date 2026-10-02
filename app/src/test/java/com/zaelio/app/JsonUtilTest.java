package com.zaelio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class JsonUtilTest {
    @Test
    public void stringifyAndToMapKeepValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("reps", 12);
        values.put("note", "ok");
        values.put("empty", null);

        Map<String, Object> out = JsonUtil.toMap(JsonUtil.stringify(values));

        assertEquals(12, ((Number) out.get("reps")).intValue());
        assertEquals("ok", out.get("note"));
        assertTrue(out.containsKey("empty"));
        assertNull(out.get("empty"));
    }

    @Test
    public void trackerToJsonExportsTrackerFields() throws Exception {
        Tracker tracker = new Tracker();
        tracker.name = "Training";
        tracker.description = "Plan";
        FieldDefinition field = new FieldDefinition();
        field.key = "weight";
        field.label = "Gewicht";
        field.type = "float";
        field.defaultValue = "0";
        field.increment = 2.5;
        field.required = true;
        field.prefillFromPrevious = true;
        tracker.fields.add(field);

        JSONObject json = new JSONObject(JsonUtil.trackerToJson(tracker));
        JSONArray fields = json.getJSONArray("fields");

        assertEquals("Training", json.getString("name"));
        assertEquals("Plan", json.getString("description"));
        assertEquals("weight", fields.getJSONObject(0).getString("key"));
        assertTrue(fields.getJSONObject(0).getBoolean("required"));
    }
}
