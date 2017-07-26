package net.puppygames.thjson;

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

	private final Stack<Object> stack = new Stack<>();
	private final Map<String, Object> root = new LinkedHashMap<>();

	private boolean debug = true;
	private Object current = root;

	/**
	 * C'tor
	 */
	public THJSONtoMapConverter() {
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
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
	public void beginObject(String key, String clazz) {
		if (debug) {
			System.out.println("BEGIN OBJECT " + key + " " + clazz);
		}
		stack.push(current);
		Map<String, Object> newCurrent = new LinkedHashMap<>();
		((Map<String, Object>) current).put(key, newCurrent);
		current = newCurrent;
		newCurrent.put("class", clazz);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginObjectValue(String clazz) {
		if (debug) {
			System.out.println("BEGIN OBJECT VALUE " + clazz);
		}
		stack.push(current);
		Map<String, Object> newCurrent = new LinkedHashMap<>();
		((List<Object>) current).add(newCurrent);
		current = newCurrent;
		newCurrent.put("class", clazz);
	}

	@Override
	public void endObject() {
		if (debug) {
			System.out.println("END OBJECT");
		}
		current = stack.pop();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void property(String key, boolean value) {
		if (debug) {
			System.out.println("PROPERTY " + key + "=" + value);
		}
		((Map<String, Object>) current).put(key, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void property(String key, float value) {
		if (debug) {
			System.out.println("PROPERTY " + key + "=" + value);
		}
		((Map<String, Object>) current).put(key, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void property(String key, int value, IntegerType type) {
		if (debug) {
			System.out.println(type + " PROPERTY " + key + "=" + value);
		}
		((Map<String, Object>) current).put(key, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void property(String key, String value, StringType type) {
		if (debug) {
			System.out.println(type + " PROPERTY " + key + "=" + value + "<");
		}
		((Map<String, Object>) current).put(key, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void nullProperty(String key) {
		if (debug) {
			System.out.println("PROPERTY " + key + "=null");
		}
		((Map<String, Object>) current).put(key, null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void value(boolean value) {
		if (debug) {
			System.out.println("VALUE " + value);
		}
		((List<Object>) current).add(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void value(float value) {
		if (debug) {
			System.out.println("VALUE " + value);
		}
		((List<Object>) current).add(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void value(int value, IntegerType type) {
		if (debug) {
			System.out.println(type + " VALUE " + value);
		}
		((List<Object>) current).add(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void value(String value, StringType type) {
		if (debug) {
			System.out.println(type + " VALUE " + value);
		}
		((List<Object>) current).add(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void nullValue() {
		if (debug) {
			System.out.println("NULL VALUE");
		}
		((List<Object>) current).add(null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginArray(String key) {
		if (debug) {
			System.out.println("BEGIN ARRAY " + key);
		}
		stack.push(current);
		List<Object> newCurrent = new ArrayList<>();
		((Map<String, Object>) current).put(key, newCurrent);
		current = newCurrent;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginArrayValue() {
		if (debug) {
			System.out.println("BEGIN ARRAY VALUE");
		}
		stack.push(current);
		List<Object> newCurrent = new ArrayList<>();
		((List<Object>) current).add(newCurrent);
		current = newCurrent;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginList(String key, String clazz) {
		if (debug) {
			System.out.println("BEGIN LIST " + key + " " + clazz);
		}
		stack.push(current);

		// Create a special "array" object
		Map<String, Object> array = new LinkedHashMap<>();
		array.put("class", "array");
		array.put("type", clazz);
		((Map<String, Object>) current).put(key, array);

		stack.push(array);

		List<Object> elements = new ArrayList<>();
		array.put("elements", elements);
		current = elements;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginListValue(String clazz) {
		if (debug) {
			System.out.println("BEGIN LIST VALUE " + clazz);
		}
		stack.push(current);

		// Create a special "array" object
		Map<String, Object> array = new LinkedHashMap<>();
		array.put("class", "array");
		array.put("type", clazz);
		((List<Object>) current).add(array);

		stack.push(array);

		List<Object> elements = new ArrayList<>();
		array.put("elements", elements);
		current = elements;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginMap(String key) {
		if (debug) {
			System.out.println("BEGIN MAP " + key);
		}
		stack.push(current);
		Map<String, Object> newCurrent = new LinkedHashMap<>();
		((Map<String, Object>) current).put(key, newCurrent);
		current = newCurrent;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beginMapValue() {
		if (debug) {
			System.out.println("BEGIN MAP VALUE");
		}
		stack.push(current);
		Map<String, Object> newCurrent = new LinkedHashMap<>();
		((List<Object>) current).add(newCurrent);
		current = newCurrent;
	}

	@Override
	public void endMap() {
		if (debug) {
			System.out.println("END MAP");
		}
		current = stack.pop();
	}

	@Override
	public void endList() {
		if (debug) {
			System.out.println("END LIST");
		}
		// Pop elements and discard
		stack.pop();
		// Then pop the array object
		current = stack.pop();
	}

	@Override
	public void endArray() {
		if (debug) {
			System.out.println("END ARRAY");
		}
		current = stack.pop();
	}

	@Override
	public void comment(String text, CommentType type) {
		System.out.println(type + ":" + text + "<");
	}

	@Override
	public void directive(String text) {
		System.out.println("DIRECTIVE:" + text + "<");
	}

}
