package net.puppygames.thjson;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MapToTHJSONConverter {

	private THJSONWriter writer;

	public MapToTHJSONConverter(THJSONWriter writer) {
		this.writer = requireNonNull(writer, "writer cannot be null");
	}

	public void write(Map<String, Object> map) {
		for (Entry<String, Object> entry : map.entrySet()) {
			writeProperty(entry.getKey(), entry.getValue());
		}
	}

	@SuppressWarnings("unchecked")
	private void writeProperty(String key, Object value) {
		if (value instanceof Map) {
			writeMap(key, (Map<String, Object>) value);
		} else if (value instanceof List) {
			writeArray(key, (List<Object>) value);
		} else {
			writePrimitive(key, value);
		}
	}

	@SuppressWarnings("unchecked")
	private <Stsring> void writeValue(Object value) {
		if (value == null) {
			writer.valueNull();
		} else if (value instanceof Map) {
			writeMap(null, (Map<String, Object>) value);
		} else if (value instanceof List) {
			writeArray(null, (List<Object>) value);
		} else {
			writePrimitive(null, value);
		}
	}

	private void writeArray(String key, List<Object> array) {
		if (key != null) {
			writer.property(key);
		}
		writer.beginList(null);
		array.forEach(this::writeValue);
		writer.endList();
	}

	private void writePrimitive(String key, Object prim) {
		if (key != null) {
			writer.property(key);
		}
		if (prim == null) {
			writer.valueNull();
		} else if (prim instanceof Boolean) {
			writer.value(((Boolean) prim).booleanValue());
		} else if (prim instanceof Number) {
			Number num = (Number) prim;
			if (num instanceof Float) {
				writer.value(num.floatValue());
			} else {
				writer.value(num.intValue());
			}
		} else {
			writer.value(prim.toString());
		}
	}

	private void writeMap(String key, Map<String, Object> object) {
		if (key != null) {
			writer.property(key);
		}
		if (object.containsKey("class")) {
			String clazz = (String) object.get("class");
			if ("array".equals(clazz)) {
				String type = (String) object.get("type");
				@SuppressWarnings("unchecked")
				List<Object> elements = (List<Object>) object.get("elements");
				writer.beginList(type);
				elements.forEach(this::writeValue);
				writer.endList();
			} else {
				writer.beginObject(clazz);
				object.entrySet().forEach(e -> {
					if (!e.getKey().equals("class")) {
						writeProperty(e.getKey(), e.getValue());
					}
				});
				writer.endObject();
			}
		} else {
			writer.beginObject(null);
			object.entrySet().forEach(e -> writeProperty(e.getKey(), e.getValue()));
			writer.endObject();
		}
	}

}
