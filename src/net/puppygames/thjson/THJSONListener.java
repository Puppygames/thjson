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

import static net.puppygames.thjson.THJSONWriter.escape;

/**
 * Listen to the stream of symbols coming in from {@link THJSONReader}
 */
public interface THJSONListener {

	/**
	 * Called every time the tokenizer reads a new token
	 * @param source The source identifier; may be null
	 * @param line The line in the source
	 * @param col The column in the source
	 */
	default void position(String source, int line, int col) {
	}

	/**
	 * Called at the start. Generally we do not need to do anything therefore we provide a default implementation that does nothing.
	 */
	default void begin() throws Exception {
	}

	/**
	 * Called at the end. Generally we do not need to do anything therefore we provide a default implementation that does nothing.
	 */
	default void end() throws Exception {
	}

	/**
	 * A map inside an object with the specified key. Maps are objects without a class.
	 * @param key
	 */
	void beginMap(String key) throws Exception;

	/**
	 * A map inside an array, or an anonymous child
	 */
	void beginMapValue() throws Exception;

	/**
	 * An object inside an object with the specified key and class. Objects always have a "class"
	 * @param key
	 * @param clazz
	 */
	void beginObject(String key, String clazz) throws Exception;

	/**
	 * An object with the specified class inside an array, or an anonymous child. Objects always have a "class"
	 * @param clazz
	 */
	void beginObjectValue(String clazz) throws Exception;

	/**
	 * End an object
	 */
	void endObject() throws Exception;

	/**
	 * End a map
	 */
	void endMap() throws Exception;

	void beginArray(String key) throws Exception;

	void beginArrayValue() throws Exception;

	void beginList(String key, String clazz) throws Exception;

	void beginListValue(String clazz) throws Exception;

	void endList() throws Exception;

	void endArray() throws Exception;

	void value(int value, IntegerType type) throws Exception;

	void value(String value, StringType type) throws Exception;

	void value(byte[] value, StringType type) throws Exception;

	void value(boolean value) throws Exception;

	void value(float value) throws Exception;

	void nullValue() throws Exception;

	void property(String key, int value, IntegerType type) throws Exception;

	void property(String key, String value, StringType type) throws Exception;

	void property(String key, byte[] value, StringType type) throws Exception;

	void property(String key, boolean value) throws Exception;

	void property(String key, float value) throws Exception;

	void nullProperty(String key) throws Exception;

	/**
	 * A comment in the source. Generally we don't need to process comments, so this default implementation is a no-op
	 * @param text
	 * @param type
	 */
	default void comment(String text, CommentType type) {
	}

	/**
	 * A directive in the source. Generally we don't need to process directives, so this default implementation is a no-op
	 * @param text
	 * @throws Exception if directive processing encounters an error
	 */
	default void directive(String text) throws Exception {
	}

	/**
	 * A function in the source. The default implementation is to return the function back again, verbatim, escaped in quotes
	 * @param text
	 * @return a String; null will be interpreted as the thjson literal null
	 * @throws Exception if function processing encounters an error
	 */
	default String function(String text) throws Exception {
		return "\"#" + escape(text) + "\"";
	}
}
