package dev.skyprofile.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Null-safe JSON path helpers. SkyCrypt's API can change without notice, so every lookup
 * falls back to a default instead of throwing.
 */
public final class J {
	private J() {}

	public static JsonElement at(JsonElement root, String... path) {
		JsonElement cur = root;
		for (String key : path) {
			if (cur == null || cur.isJsonNull()) return null;
			if (cur.isJsonObject()) {
				cur = cur.getAsJsonObject().get(key);
			} else if (cur.isJsonArray()) {
				try {
					int i = Integer.parseInt(key);
					JsonArray arr = cur.getAsJsonArray();
					cur = i >= 0 && i < arr.size() ? arr.get(i) : null;
				} catch (NumberFormatException e) {
					return null;
				}
			} else {
				return null;
			}
		}
		return cur == null || cur.isJsonNull() ? null : cur;
	}

	public static JsonObject obj(JsonElement root, String... path) {
		JsonElement e = at(root, path);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	public static JsonArray arr(JsonElement root, String... path) {
		JsonElement e = at(root, path);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	public static List<JsonObject> objects(JsonElement root, String... path) {
		List<JsonObject> out = new ArrayList<>();
		for (JsonElement e : arr(root, path)) if (e != null && e.isJsonObject()) out.add(e.getAsJsonObject());
		return out;
	}

	/** Entries of a JSON object whose values are objects, in document order. */
	public static Map<String, JsonObject> entries(JsonElement root, String... path) {
		Map<String, JsonObject> out = new LinkedHashMap<>();
		JsonObject o = obj(root, path);
		if (o != null) for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			if (e.getValue() != null && e.getValue().isJsonObject()) out.put(e.getKey(), e.getValue().getAsJsonObject());
		}
		return out;
	}

	public static double num(JsonElement root, double def, String... path) {
		JsonElement e = at(root, path);
		if (e == null || !e.isJsonPrimitive()) return def;
		try {
			return e.getAsDouble();
		} catch (Exception ex) {
			return def;
		}
	}

	public static double num(JsonElement root, String... path) {
		return num(root, 0, path);
	}

	public static boolean has(JsonElement root, String... path) {
		return at(root, path) != null;
	}

	public static String strOr(JsonElement root, String def, String... path) {
		JsonElement e = at(root, path);
		if (e == null || !e.isJsonPrimitive()) return def;
		return e.getAsString();
	}

	public static String str(JsonElement root, String... path) {
		return strOr(root, "", path);
	}

	public static boolean bool(JsonElement root, String... path) {
		JsonElement e = at(root, path);
		if (e == null || !e.isJsonPrimitive()) return false;
		try {
			return e.getAsBoolean();
		} catch (Exception ex) {
			return false;
		}
	}

	public static List<String> strings(JsonElement root, String... path) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : arr(root, path)) if (e != null && e.isJsonPrimitive()) out.add(e.getAsString());
		return out;
	}
}
