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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Stack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * This will handily convert a THJSON stream into a {@link JsonObject} object instance. Class objects will have a property, "class", set on them with the class
 * name. Typed arrays are stored as classes with class "array" and one property, "elements", which is the array as a JSON array. Untyped arrays are simply
 * stored as normal JSON arrays.
 */
public class THJSONtoJSONConverter implements THJSONListener {

	private final Stack<JsonElement> stack = new Stack<>();
	private final JsonObject json = new JsonObject();

	private JsonElement current = json;

	/**
	 * C'tor
	 */
	public THJSONtoJSONConverter() {
	}

	/**
	 * Gets the {@link JsonObject} that we create from listening to a THJSON stream. If the parsing throws at any point, then the JsonObject thus returned will
	 * only be partially complete.
	 * @return the {@link JsonObject} thus created.
	 */
	public JsonObject getJson() {
		return json;
	}

	@Override
	public void beginObject(String key, String clazz) {
		stack.push(current);
		JsonObject newCurrent = new JsonObject();
		((JsonObject) current).add(key, newCurrent);
		current = newCurrent;
		newCurrent.addProperty("class", clazz);
	}

	@Override
	public void endObject() {
		current = stack.pop();
	}

	@Override
	public void endMap() {
		current = stack.pop();
	}

	@Override
	public void property(String key, boolean value) {
		((JsonObject) current).addProperty(key, value);
	}

	@Override
	public void property(String key, float value) {
		((JsonObject) current).addProperty(key, value);
	}

	@Override
	public void property(String key, int value, IntegerType type) {
		((JsonObject) current).addProperty(key, value);
	}

	@Override
	public void property(String key, String value, StringType type) {
		((JsonObject) current).addProperty(key, value);
	}

	@Override
	public void property(String key, byte[] value, StringType type) {
		((JsonObject) current).addProperty(key, new String(value, UTF_8));
	}

	@Override
	public void nullProperty(String key) {
		((JsonObject) current).add(key, JsonNull.INSTANCE);
	}

	@Override
	public void value(boolean value) {
		((JsonArray) current).add(value);
	}

	@Override
	public void value(float value) {
		((JsonArray) current).add(value);
	}

	@Override
	public void value(int value, IntegerType type) {
		((JsonArray) current).add(value);
	}

	@Override
	public void value(String value, StringType type) {
		((JsonArray) current).add(value);
	}

	@Override
	public void value(byte[] value, StringType type) {
		((JsonArray) current).add(new String(value, UTF_8));
	}

	@Override
	public void nullValue() {
		((JsonArray) current).add(JsonNull.INSTANCE);
	}

	@Override
	public void beginArray(String key) {
		stack.push(current);
		JsonArray newCurrent = new JsonArray();
		((JsonObject) current).add(key, newCurrent);
		current = newCurrent;
	}

	@Override
	public void beginList(String key, String clazz) {
		stack.push(current);

		// Create a special "array" object
		JsonObject array = new JsonObject();
		array.addProperty("class", "array");
		array.addProperty("type", clazz);
		((JsonObject) current).add(key, array);

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

	private void addChild(JsonElement newChild) {
		if (current instanceof JsonArray) {
			((JsonArray) current).add(newChild);
		} else {
			JsonObject currentObject = (JsonObject) current;
			JsonArray children = currentObject.getAsJsonArray("children");
			if (children == null) {
				children = new JsonArray();
				currentObject.add("children", children);
			}
			children.add(newChild);
		}
	}

	@Override
	public void beginArrayValue() {
		stack.push(current);
		JsonArray newCurrent = new JsonArray();
		addChild(newCurrent);
		current = newCurrent;
	}

	@Override
	public void beginListValue(String clazz) {
		stack.push(current);

		// Create a special "array" object
		JsonObject array = new JsonObject();
		array.addProperty("class", "array");
		array.addProperty("type", clazz);

		addChild(array);

		stack.push(array);

		JsonArray elements = new JsonArray();
		array.add("elements", elements);
		current = elements;
	}

	@Override
	public void beginMap(String key) {
		stack.push(current);
		JsonObject newCurrent = new JsonObject();
		((JsonObject) current).add(key, newCurrent);
		current = newCurrent;
	}

	@Override
	public void beginMapValue() {
		stack.push(current);
		JsonObject newCurrent = new JsonObject();
		addChild(newCurrent);
		current = newCurrent;
	}

	@Override
	public void beginObjectValue(String clazz) {
		stack.push(current);
		JsonObject newCurrent = new JsonObject();
		addChild(newCurrent);
		current = newCurrent;
		newCurrent.addProperty("class", clazz);
	}

}