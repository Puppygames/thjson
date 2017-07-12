package net.puppygames.thjson;

import static java.util.Objects.*;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stream plain old Java objects to THJSON output.
 * <p>
 * We can turn Maps into THJSON maps, Collections into THJSON arrays, and arrays into THJSON lists, Strings into THJSON strings, primitives and their
 * boxed equivalents into THJSON primitives, and finally Objects into THJSON objects.
 * <p>
 * We make no attempt to refer back to previous references with objects however we will explode if we enter a circular graph.
 */
public class POJOtoTHJSONConverter {

	private THJSONWriter writer;
	private IdentityHashMap<Object, Object> alreadyWritten = new IdentityHashMap<>();

	public POJOtoTHJSONConverter(THJSONWriter writer) {
		this.writer = requireNonNull(writer, "writer cannot be null");
	}

	public void write(Object object) throws IllegalArgumentException, IllegalAccessException {

		alreadyWritten.clear();
		alreadyWritten.put(object, object);

		Class<?> clazz = object.getClass();
		while (clazz != Object.class) {
			Field[] fields = clazz.getDeclaredFields();
			for (Field field : fields) {
				field.setAccessible(true);
				int modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || Modifier.isVolatile(modifiers)) {
					// Skip
					continue;
				}
				if (field.isSynthetic()) {
					// Skip
					continue;
				}

				writeProperty(field.getName(), field.get(object));
			}

			clazz = clazz.getSuperclass();
		}
	}

	private void writeObject(String key, Object object) throws IllegalArgumentException, IllegalAccessException {
		if (alreadyWritten.containsKey(object)) {
			throw new IllegalArgumentException("Object " + object + " is already present in the stream");
		}
		alreadyWritten.put(object, object);

		writer.beginObject(key, object.getClass().getName());

		Class<?> clazz = object.getClass();
		while (clazz != Object.class) {
			Field[] fields = clazz.getDeclaredFields();
			for (Field field : fields) {
				field.setAccessible(true);

				int modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || Modifier.isVolatile(modifiers)) {
					// Skip
					continue;
				}
				if (field.isSynthetic()) {
					// Skip
					continue;
				}

				writeProperty(field.getName(), field.get(object));
			}

			clazz = clazz.getSuperclass();
		}

		writer.endObject();
	}

	@SuppressWarnings("unchecked")
	private void writeProperty(String key, Object value) throws IllegalArgumentException, IllegalAccessException {
		if (value == null) {
			writer.propertyNull(key);
			//		} else if (value instanceof Map) {
			//			writeMap(key, (Map<Object, Object>) value);
			//		} else if (value instanceof List) {
			//			writeList(key, (List<Object>) value);
		} else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
			writePrimitive(key, value);
		} else if (value.getClass().isArray()) {
			writeArray(key, value);
		} else {
			writeObject(key, value);
		}
	}

	private <Stsring> void writeValue(Object value) throws IllegalArgumentException, IllegalAccessException {
		if (value == null) {
			writer.valueNull();
		} else {
			writeProperty(null, value);
		}
	}

	private void writeList(String key, List<?> array) throws IllegalArgumentException, IllegalAccessException {
		writer.beginArray(key);
		for (Object o : array) {
			writeValue(o);
		}
		writer.endArray();
	}

	private void writeArray(String key, Object array) throws ArrayIndexOutOfBoundsException, IllegalArgumentException, IllegalAccessException {
		Class<?> elementType = array.getClass().getComponentType();
		writer.beginList(key, elementType.getTypeName());
		int length = Array.getLength(array);
		for (int i = 0; i < length; i++) {
			writeValue(Array.get(array, i));
		}
		writer.endList();
	}

	private void writePrimitive(String key, Object prim) {
		if (key == null) {
			if (prim == null) {
				writer.valueNull();
			} else if (prim instanceof Boolean) {
				writer.value(((Boolean) prim).booleanValue());
			} else if (prim instanceof Number) {
				Number num = (Number) prim;
				if (num instanceof Float || num instanceof Double || num instanceof BigDecimal) {
					writer.value(num.floatValue());
				} else {
					writer.value(num.intValue());
				}
			} else {
				writer.value(prim.toString());
			}
		} else {
			if (prim == null) {
				writer.propertyNull(key);
			} else if (prim instanceof Boolean) {
				writer.property(key, ((Boolean) prim).booleanValue());
			} else if (prim instanceof Number) {
				Number num = (Number) prim;
				if (num instanceof Float || num instanceof Double || num instanceof BigDecimal) {
					writer.property(key, num.floatValue());
				} else {
					writer.property(key, num.intValue());
				}
			} else {
				writer.property(key, prim.toString());
			}
		}
	}

	private void writeMap(String key, Map<Object, Object> object) throws IllegalArgumentException, IllegalAccessException {
		writer.beginMap(key);
		for (Map.Entry<Object, Object> e : object.entrySet()) {
			writeProperty(e.getKey().toString(), e.getValue());
		}
		writer.endMap();
	}

}
