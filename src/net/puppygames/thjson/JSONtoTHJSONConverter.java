package net.puppygames.thjson;

import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JSONtoTHJSONConverter {

	private final THJSONWriter writer;

	public JSONtoTHJSONConverter(THJSONWriter writer) {
		this.writer = writer;
	}

	public void write(JsonObject json) {
		for (Entry<String, JsonElement> entry : json.entrySet()) {
			writeProperty(entry.getKey(), entry.getValue());
		}
	}

	private void writeProperty(String key, JsonElement value) {
		if (value.isJsonNull()) {
			writer.propertyNull(key);
		} else if (value.isJsonObject()) {
			writeMap(key, value.getAsJsonObject());
		} else if (value.isJsonArray()) {
			writeArray(key, value.getAsJsonArray());
		} else {
			writePrimitive(key, value.getAsJsonPrimitive());
		}
	}

	private void writeValue(JsonElement value) {
		if (value.isJsonNull()) {
			writer.valueNull();
		} else if (value.isJsonObject()) {
			writeMap(null, value.getAsJsonObject());
		} else if (value.isJsonArray()) {
			writeArray(null, value.getAsJsonArray());
		} else {
			writePrimitive(null, value.getAsJsonPrimitive());
		}
	}

	private void writeArray(String key, JsonArray array) {
		writer.beginArray(key);
		array.forEach(this::writeValue);
		writer.endArray();
	}

	private void writePrimitive(String key, JsonPrimitive prim) {
		if (key == null) {
			if (prim.isJsonNull()) {
				writer.valueNull();
			} else if (prim.isBoolean()) {
				writer.value(prim.getAsBoolean());
			} else if (prim.isNumber()) {
				Number num = prim.getAsNumber();
				if (num instanceof Float) {
					writer.value(num.floatValue());
				} else {
					writer.value(num.intValue());
				}
			} else {
				writer.value(prim.getAsString());
			}
		} else {
			if (prim.isJsonNull()) {
				writer.propertyNull(key);
			} else if (prim.isBoolean()) {
				writer.property(key, prim.getAsBoolean());
			} else if (prim.isNumber()) {
				Number num = prim.getAsNumber();
				if (num instanceof Float) {
					writer.property(key, num.floatValue());
				} else {
					writer.property(key, num.intValue());
				}
			} else {
				writer.property(key, prim.getAsString());
			}
		}
	}

	private void writeMap(String key, JsonObject json) {
		if (json.has("class")) {
			String clazz = json.get("class").getAsString();
			if ("array".equals(clazz)) {
				String type = json.get("type").getAsString();
				JsonArray elements = json.get("elements").getAsJsonArray();
				writer.beginList(key, type);
				elements.forEach(this::writeValue);
				writer.endList();
			} else {
				writer.beginObject(key, clazz);
				json.entrySet().forEach(e -> {
					if (!e.getKey().equals("class")) {
						writeProperty(e.getKey(), e.getValue());
					}
				});
				writer.endObject();
			}
		} else {
			writer.beginMap(key);
			json.entrySet().forEach(e -> writeProperty(e.getKey(), e.getValue()));
			writer.endMap();
		}
	}

}
