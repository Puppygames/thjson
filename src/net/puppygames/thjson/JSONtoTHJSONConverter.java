package net.puppygames.thjson;

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
		writeValue(json);
	}

	private void writeProperty(String key, JsonElement value) {
		if (value.isJsonObject()) {
			writeMap(key, value.getAsJsonObject());
		} else if (value.isJsonArray()) {
			if (key.equals("children")) {
				return;
			} else {
				writeArray(key, value.getAsJsonArray());
			}
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
		if (key != null) {
			writer.property(key);
		}
		writer.beginList(null);
		array.forEach(this::writeValue);
		writer.endList();
	}

	private void writePrimitive(String key, JsonPrimitive prim) {
		if (key != null) {
			writer.property(key);
		}
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
	}

	private void writeMap(String key, JsonObject json) {
		if (key != null) {
			writer.property(key);
		}
		if (json.has("class")) {
			String clazz = json.get("class").getAsString();
			if ("array".equals(clazz)) {
				String type = json.get("type").getAsString();
				JsonArray elements = json.get("elements").getAsJsonArray();
				writer.beginList(type);
				elements.forEach(this::writeValue);
				writer.endList();
			} else {
				writer.beginObject(clazz);
				json.entrySet().forEach(e -> {
					if (!e.getKey().equals("class")) {
						writeProperty(e.getKey(), e.getValue());
					}
				});
				if (json.has("children")) {
					writeArray(null, json.get("children").getAsJsonArray());
				}
				writer.endObject();
			}
		} else {
			writer.beginObject(null);
			json.entrySet().forEach(e -> writeProperty(e.getKey(), e.getValue()));
			if (json.has("children")) {
				JsonArray children = json.get("children").getAsJsonArray();
				children.forEach(this::writeValue);
			}
			writer.endObject();
		}
	}

}
