/*

Copyright 2017 Shaven Puppy Ltd

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
contributors may be used to endorse or promote products derived from this
software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

*/

package net.puppygames.thjson;

import java.nio.charset.StandardCharsets;
import java.util.Stack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * This will handily convert a THJSON stream into a {@link JsonObject} object instance.
 * Class objects will have a property, "class", set on them with the class name. Typed arrays are stored
 * as classes with class "array" and one property, "elements", which is the array as a JSON array. Untyped arrays are
 * simply stored as normal JSON arrays.
 */
public class THJSONtoJSONConverter implements THJSONListener {

	private interface Acceptor {
		void onString(String s);

		void onFloat(float f);

		void onInteger(int i);

		void onBoolean(boolean b);

		void onNull();
	}

	private final Stack<JsonElement> stack = new Stack<>();
	private final JsonObject json = new JsonObject();

	private JsonElement current = json;

	/**
	 * C'tor
	 */
	public THJSONtoJSONConverter() {
	}

	/**
	 * Gets the {@link JsonObject} that we create from listening to a THJSON stream. If the parsing throws at any point, then the JsonObject thus returned
	 * will only be partially complete.
	 * @return the {@link JsonObject} thus created.
	 */
	public JsonObject getJson() {
		return json;
	}

	@Override
	public void beginObject(byte[] src, int key, int keyLength, int clazz, int clazzLength) {
		stack.push(current);
		JsonObject newCurrent = new JsonObject();
		if (current instanceof JsonObject) {
			String keyString = new String(src, key, keyLength, StandardCharsets.UTF_8);
			((JsonObject) current).add(keyString, newCurrent);
		} else {
			((JsonArray) current).add(newCurrent);
		}
		current = newCurrent;
		if (clazzLength != 0) {
			newCurrent.addProperty("class", new String(src, clazz, clazzLength, StandardCharsets.UTF_8));
		}
	}

	@Override
	public void endObject() {
		current = stack.pop();
	}

	@Override
	public void property(byte[] src, int key, int keyLength, byte[] valueSrc, THJSONPrimitiveType type, int value, int valueLength) {
		String keyString = new String(src, key, keyLength, StandardCharsets.UTF_8);
		onValue(type, valueSrc, value, valueLength, new Acceptor() {
			@Override
			public void onString(String s) {
				((JsonObject) current).addProperty(keyString, s);
			}

			@Override
			public void onFloat(float f) {
				((JsonObject) current).addProperty(keyString, f);
			}

			@Override
			public void onInteger(int i) {
				((JsonObject) current).addProperty(keyString, i);
			}

			@Override
			public void onBoolean(boolean b) {
				((JsonObject) current).addProperty(keyString, b);
			}

			@Override
			public void onNull() {
				((JsonObject) current).add(keyString, new JsonNull());
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

	@Override
	public void value(byte[] src, THJSONPrimitiveType type, int value, int valueLength) {
		onValue(type, src, value, valueLength, new Acceptor() {
			@Override
			public void onString(String s) {
				((JsonArray) current).add(new JsonPrimitive(s));
			}

			@Override
			public void onFloat(float f) {
				((JsonArray) current).add(new JsonPrimitive(f));
			}

			@Override
			public void onInteger(int i) {
				((JsonArray) current).add(new JsonPrimitive(i));
			}

			@Override
			public void onBoolean(boolean b) {
				((JsonArray) current).add(new JsonPrimitive(b));
			}

			@Override
			public void onNull() {
				((JsonArray) current).add(new JsonNull());
			}
		});
	}

	@Override
	public void beginArray(byte[] src, int key, int keyLength) {
		stack.push(current);
		JsonArray newCurrent = new JsonArray();
		if (current instanceof JsonObject) {
			String keyString = new String(src, key, keyLength, StandardCharsets.UTF_8);
			((JsonObject) current).add(keyString, newCurrent);
		} else {
			((JsonArray) current).add(newCurrent);
		}
		current = newCurrent;
	}

	@Override
	public void beginList(byte[] src, int key, int keyLength, int clazz, int clazzLength) {
		stack.push(current);

		// Create a special "array" object
		JsonObject array = new JsonObject();
		array.addProperty("class", "array");
		String typeString = new String(src, clazz, clazzLength, StandardCharsets.UTF_8);
		array.addProperty("type", typeString);
		if (current instanceof JsonObject) {
			String keyString = new String(src, key, keyLength, StandardCharsets.UTF_8);
			((JsonObject) current).add(keyString, array);
		} else {
			((JsonArray) current).add(array);
		}

		stack.push(array);

		JsonArray elements = new JsonArray();
		array.add("elements", elements);
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