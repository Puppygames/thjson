package net.puppygames.thjson;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.google.gson.JsonObject;

/**
 * Converts a THJSON input stream into a {@link Map}. Ordering is maintained.
 */
public class THJSONtoMapConverter implements THJSONListener {

	private interface Acceptor {
		void onString(String s);

		void onFloat(float f);

		void onInteger(int i);

		void onBoolean(boolean b);

		void onNull();
	}

	private final Stack<Object> stack = new Stack<>();
	private final Map<String, Object> root = new LinkedHashMap<>();

	private Object current = root;

	/**
	 * C'tor
	 */
	public THJSONtoMapConverter() {
	}

	/**
	 * Gets the {@link JsonObject} that we create from listening to a THJSON stream. If the parsing throws at any point, then the JsonObject thus returned will
	 * only be partially complete.
	 * @return the {@link JsonObject} thus created.
	 */
	public Map<String, Object> getMap() {
		return root;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginObject(byte[] src, int key, int keyLength, int clazz, int clazzLength) {
		stack.push(current);
		Map<String, Object> newCurrent = new LinkedHashMap<>();
		if (current instanceof Map) {
			String keyString = new String(src, key, keyLength, StandardCharsets.UTF_8);
			((Map<String, Object>) current).put(keyString, newCurrent);
		} else {
			((List<Object>) current).add(newCurrent);
		}
		current = newCurrent;
		if (clazzLength != 0) {
			newCurrent.put("class", new String(src, clazz, clazzLength, StandardCharsets.UTF_8));
		}
	}

	@Override
	public void endObject() {
		current = stack.pop();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void property(byte[] src, int key, int keyLength, byte[] valueSrc, THJSONPrimitiveType type, int value, int valueLength) {
		String keyString = new String(src, key, keyLength, StandardCharsets.UTF_8);
		onValue(type, valueSrc, value, valueLength, new Acceptor() {
			@Override
			public void onString(String s) {
				((Map<String, Object>) current).put(keyString, s);
			}

			@Override
			public void onFloat(float f) {
				((Map<String, Object>) current).put(keyString, f);
			}

			@Override
			public void onInteger(int i) {
				((Map<String, Object>) current).put(keyString, i);
			}

			@Override
			public void onBoolean(boolean b) {
				((Map<String, Object>) current).put(keyString, b);
			}

			@Override
			public void onNull() {
				((Map<String, Object>) current).put(keyString, null);
			}
		});
	}

	private void onValue(THJSONPrimitiveType type, byte[] src, int start, int length, Acceptor a) {
		if (type == THJSONPrimitiveType.NULL) {
			a.onNull();
			return;
		}
		String value = new String(src, start, length, StandardCharsets.UTF_8);
		switch (type) {
			case BOOLEAN:
				a.onBoolean(Boolean.parseBoolean(value));
				break;
			case FLOAT:
				try {
					a.onFloat(Float.parseFloat(value));
				} catch (NumberFormatException e) {
					// Treat as string instead
					a.onString(value);
				}
				break;
			case INTEGER:
				if (value.startsWith("0x")) {
					// Unsigned hex integer
					try {
						a.onInteger(Integer.parseUnsignedInt(value.substring(2), 16));
					} catch (NumberFormatException e) {
						// Treat as string instead
						a.onString(value);
					}
				} else if (value.startsWith("%")) {
					// Unsigned binary integer
					try {
						a.onInteger(Integer.parseUnsignedInt(value.substring(1), 2));
					} catch (NumberFormatException e) {
						// Treat as string instead
						a.onString(value);
					}
				} else {
					a.onInteger(Integer.parseInt(value));
				}
				break;
			default:
				a.onString(value);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void value(byte[] src, THJSONPrimitiveType type, int value, int valueLength) {
		onValue(type, src, value, valueLength, new Acceptor() {
			@Override
			public void onString(String s) {
				((List<Object>) current).add(s);
			}

			@Override
			public void onFloat(float f) {
				((List<Object>) current).add(f);
			}

			@Override
			public void onInteger(int i) {
				((List<Object>) current).add(i);
			}

			@Override
			public void onBoolean(boolean b) {
				((List<Object>) current).add(b);
			}

			@Override
			public void onNull() {
				((List<Object>) current).add(null);
			}
		});
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginArray(byte[] src, int key, int keyLength) {
		stack.push(current);
		List<Object> newCurrent = new ArrayList<>();
		if (current instanceof Map) {
			String keyString = new String(src, key, keyLength, StandardCharsets.UTF_8);
			((Map<String, Object>) current).put(keyString, newCurrent);
		} else {
			((List<Object>) current).add(newCurrent);
		}
		current = newCurrent;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginList(byte[] src, int key, int keyLength, int clazz, int clazzLength) {
		stack.push(current);

		// Create a special "array" object
		Map<String, Object> array = new LinkedHashMap<>();
		array.put("class", "array");
		String typeString = new String(src, clazz, clazzLength, StandardCharsets.UTF_8);
		array.put("type", typeString);
		if (current instanceof Map) {
			String keyString = new String(src, key, keyLength, StandardCharsets.UTF_8);
			((Map<String, Object>) current).put(keyString, array);
		} else {
			((List<Object>) current).add(array);
		}

		stack.push(array);

		List<Object> elements = new ArrayList<>();
		array.put("elements", elements);
		current = elements;
	}

	@Override
	public void endList() {
		// Pop elements and discard
		stack.pop();
		// Then pop the array object
		current = stack.pop();
	}

	@Override
	public void endArray() {
		current = stack.pop();
	}

}
