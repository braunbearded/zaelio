package com.zaelio.app;

import android.content.ContentValues;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class JsonUtil {
    static Map<String, Object> toMap(String json) {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            if (json == null || json.trim().isEmpty()) {
                return out;
            }

            JSONObject object = new JSONObject(json);
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                out.put(key, object.isNull(key) ? null : object.get(key));
            }
            return out;
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String stringify(Map<String, Object> values) {
        try {
            JSONObject object = new JSONObject();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                object.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            }
            return object.toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String trackerToJson(Tracker tracker) {
        try {
            JSONObject root = new JSONObject();
            root.put("name", tracker.name);
            root.put("description", tracker.description == null ? "" : tracker.description);

            JSONArray fields = new JSONArray();
            for (FieldDefinition field : tracker.fields) {
                fields.put(fieldToJson(field));
            }
            root.put("fields", fields);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static JSONObject fieldToJson(FieldDefinition field) throws JSONException {
        JSONObject fieldJson = new JSONObject();
        fieldJson.put("id", field.id);
        fieldJson.put("key", field.key);
        fieldJson.put("label", field.label);
        fieldJson.put("type", field.type);
        fieldJson.put("order", field.order);
        fieldJson.put("defaultValue", field.defaultValue == null ? JSONObject.NULL : field.defaultValue);
        fieldJson.put("increment", field.increment);
        fieldJson.put("required", field.required);
        fieldJson.put("prefillFromPrevious", field.prefillFromPrevious);
        return fieldJson;
    }

    static ContentValues fieldValuesFromJson(JSONObject field, long trackerId, int fallbackOrder) throws JSONException {
        ContentValues values = new ContentValues();
        values.put("trackerId", trackerId);
        values.put("fieldKey", field.getString("key"));
        values.put("label", field.optString("label", field.getString("key")));
        values.put("type", field.optString("type", "string"));
        values.put("sortOrder", field.optInt("order", fallbackOrder));
        if (field.has("defaultValue") && !field.isNull("defaultValue")) {
            values.put("defaultValue", String.valueOf(field.get("defaultValue")));
        } else {
            values.putNull("defaultValue");
        }
        values.put("incrementValue", field.optDouble("increment", 1));
        values.put("required", field.optBoolean("required", false) ? 1 : 0);
        values.put("prefillFromPrevious", field.optBoolean("prefillFromPrevious", false) ? 1 : 0);
        return values;
    }
}

