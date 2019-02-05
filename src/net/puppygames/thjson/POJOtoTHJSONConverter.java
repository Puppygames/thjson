package net.puppygames.thjson;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stream plain old Java objects to THJSON output.
 * <p>
 * We can turn Maps into THJSON maps, Collections into THJSON arrays, and arrays into THJSON lists, Strings into THJSON strings, primitives and their boxed
 * equivalents into THJSON primitives, and finally Objects into THJSON objects.
 * <p>
 * We make no attempt to refer back to previous references with objects however we will explode if we enter a circular graph.
 */
public class POJOtoTHJSONConverter {

	private SimpleTHJSONWriter writer;
	private IdentityHashMap<Object, Object> alreadyWritten = new IdentityHashMap<>();
	private final Map<Class<?>, Boolean> compact = new TreeMap<>((o1, o2) -> {
		if (o1 == o2) {
			return 0;
		} else if (o1.isAssignableFrom(o2)) {
			return 1;
		} else if (o2.isAssignableFrom(o1)) {
			return -1;
		} else {
			return o2.getName().compareTo(o1.getName());
		}
	});

	public POJOtoTHJSONConverter(SimpleTHJSONWriter writer) {
		this.writer = requireNonNull(writer, "writer cannot be null");
	}

	public void setCompact(Class<?> clazz, boolean compacted) {
		compact.put(clazz, compacted);
	}

	private boolean isCompact(Class<?> clazz) {
		for (Map.Entry<Class<?>, Boolean> entry : compact.entrySet()) {
			if (entry.getKey().isAssignableFrom(clazz)) {
				return entry.getValue();
			}
		}
		return false;
	}

	public void write(Object object) throws IllegalArgumentException, IllegalAccessException {

		alreadyWritten.clear();

		writeObject(null, object);
//
//		alreadyWritten.put(object, object);
//
//		Class<?> clazz = object.getClass();
//		while (clazz != Object.class) {
//			Field[] fields = clazz.getDeclaredFields();
//			for (Field field : fields) {
//				field.setAccessible(true);
//				int modifiers = field.getModifiers();
//				if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || Modifier.isVolatile(modifiers)) {
//					// Skip
//					continue;
//				}
//				if (field.isSynthetic()) {
//					// Skip
//					continue;
//				}
//
//				writeProperty(field.getName(), field.get(object));
//			}
//
//			clazz = clazz.getSuperclass();
//		}
	}

	private void writeObject(String key, Object object) throws IllegalArgumentException, IllegalAccessException {
		if (alreadyWritten.containsKey(object)) {
			throw new IllegalArgumentException("Object " + object + " is already present in the stream");
		}
		alreadyWritten.put(object, object);

		if (key != null) {
			writer.property(key);
		}
		writer.setCompact(isCompact(object.getClass()));
		writer.beginObject(object.getClass().getName());

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
		if ("children".equals(key)) {
			if (!(value instanceof List)) {
				return;
			}
			List<Object> children = (List<Object>) value;
			for (Object child : children) {
				writeObject(null, child);
			}
		} else {
			if (value instanceof Map) {
				writeMap(key, (Map<Object, Object>) value);
			} else if (value instanceof List) {
				writeList(key, (List<Object>) value);
			} else if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
				writePrimitive(key, value);
			} else if (value.getClass().isArray()) {
				writeArray(key, value);
			} else {
				writeObject(key, value);
			}
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
		if (key != null) {
			writer.property(key);
		}
		writer.beginList(null);
		for (Object o : array) {
			writeValue(o);
		}
		writer.endList();
	}

	private void writeArray(String key, Object array) throws ArrayIndexOutOfBoundsException, IllegalArgumentException, IllegalAccessException {
		Class<?> elementType = array.getClass().getComponentType();
		if (key != null) {
			writer.property(key);
		}
		int length = Array.getLength(array);
		if (elementType.isPrimitive() || length <= 16 || isCompact(elementType)) {
			writer.setCompact(true);
		}
		String tag;
		if (elementType == String.class || elementType.isPrimitive()) {
			tag = null;
		} else {
			tag = elementType.getTypeName();
		}
		writer.beginList(tag);
		for (int i = 0; i < length; i++) {
			writeValue(Array.get(array, i));
		}
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
			if (num instanceof Float || num instanceof Double || num instanceof BigDecimal) {
				writer.value(num.floatValue());
			} else {
				writer.value(num.intValue());
			}
		} else {
			writer.value(prim.toString());
		}
	}

	private void writeMap(String key, Map<Object, Object> object) throws IllegalArgumentException, IllegalAccessException {
		if (key != null) {
			writer.property(key);
		}
		writer.beginObject(null);
		for (Map.Entry<Object, Object> e : object.entrySet()) {
			writeProperty(e.getKey().toString(), e.getValue());
		}
		writer.endObject();
	}

}
