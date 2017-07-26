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

import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

/**
 * A streaming Tagged Human JSON reader
 */
public class THJSONReader {

	/** Allowed levels of function nesting */
	public static final int MAX_RECURSION = 16;

	public static void readResource(String resourceURL, THJSONListener listener) throws IOException {
		URL url = THJSONReader.class.getResource(resourceURL);
		URLConnection conn = url.openConnection();
		new THJSONReader(conn.getInputStream(), listener).parse();
	}

	public static JsonObject convertToJSON(String resourceURL) throws IOException {
		THJSONtoJSONConverter converter = new THJSONtoJSONConverter();
		readResource(resourceURL, converter);
		return converter.getJson();
	}

	public static Map<String, Object> convertToMap(String resourceURL) throws IOException {
		THJSONtoMapConverter converter = new THJSONtoMapConverter();
		converter.setDebug(true);
		readResource(resourceURL, converter);
		return converter.getMap();
	}

	public static void main(String[] args) throws Exception {
		JsonObject json = convertToJSON("test2.thjson");
		StringWriter out = new StringWriter();
		JsonWriter writer = new JsonWriter(out);
		writer.setIndent("\t");
		Gson gson = new Gson();
		gson.toJson(json, writer);
		writer.flush();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out.getBuffer().toString()), null);
		System.out.println(out.getBuffer().toString());
	}

	private final THJSONTokenizer tokenizer;

	/** This reports the events */
	private final THJSONListener listener;

	/** Recursion level */
	private final int recursionLevel;

	/** Whether we got a root brace */
	private boolean hasRootBrace;

	/** Whether we got a member */
	private boolean hasMember;

	/** Whether we closed the root brace */
	private boolean closedRootBrace;

	/**
	 * C'tor
	 * @param in Cannot be null
	 * @param listener Cannot be null
	 */
	public THJSONReader(InputStream in, THJSONListener listener) {
		tokenizer = new THJSONTokenizer(in);
		this.listener = requireNonNull(listener, "listener cannot be null");
		this.recursionLevel = 0;
	}

	/**
	 * C'tor
	 * @param in Cannot be null
	 * @param listener Cannot be null
	 * @param recursionLevel
	 */
	private THJSONReader(InputStream in, THJSONListener listener, int recursionLevel) throws IOException {
		if (recursionLevel >= MAX_RECURSION) {
			throw new IOException("Maximum recursion level exceeded");
		}
		tokenizer = new THJSONTokenizer(in);
		this.listener = requireNonNull(listener, "listener cannot be null");
		this.recursionLevel = recursionLevel;
	}

	public void parse() throws IOException {
		listener.begin();

		Token t;

		while ((t = peek(0)) != Token.EOF) {

			if (consumeComment(t)) {
				continue;
			}

			// Only allowed one root brace, and only if we've not yet had a member definition
			if (t == Token.OPEN_CURLY_BRACKET) {
				if (hasRootBrace || hasMember) {
					throw new IOException("Unexpected { at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
				}
				hasRootBrace = true;
				read();
			} else if (t == Token.CLOSE_CURLY_BRACKET) {
				if (!hasRootBrace || closedRootBrace) {
					throw new IOException("Unexpected } at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
				}
				closedRootBrace = true;
				read();
			} else if (t.getType() == TokenType.DIRECTIVE) {
				read();
				listener.directive(t.getString());
			} else if (t.getType() == TokenType.STRING) {
				// Reading a root-level property:value
				readMember(true);
			} else {
				throw new IOException("Unexpected token " + t + " at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
			}

		}

		if (hasRootBrace && !closedRootBrace) {
			throw new EOFException("Unexpected EOF at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
		}

		listener.end();
	}

	/**
	 * Sets the tab size. This is used when working out indentation for triple quoted strings.
	 * @param tabSize
	 */
	public void setTabSize(int tabSize) {
		tokenizer.setTabSize(tabSize);
	}

	/**
	 * Gets the tab size used when calculating indentations for triple quoted strings. Defaults to {@link #DEFAULT_TAB_SIZE}
	 * @return tab size, in spaces
	 */
	public int getTabSize() {
		return tokenizer.getTabSize();
	}

	/* --- */

	private void ensureNotEOF(Token t, String message) throws EOFException {
		if (t == Token.EOF) {
			throw new EOFException("Unexpected EOF " + message + " at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
		}
	}

	private boolean consumeComment(Token t) throws IOException {
		if (t.getType().isComment()) {
			read();
			if (t.getType() == TokenType.HASH_COMMENT) {
				listener.comment(t.getString(), CommentType.HASH);
			} else if (t.getType() == TokenType.SLASHSLASH_COMMENT) {
				listener.comment(t.getString(), CommentType.SLASHSLASH);
			} else if (t.getType() == TokenType.BLOCK_COMMENT) {
				listener.comment(t.getString(), CommentType.BLOCK);
			}
			return true;
		} else {
			return false;
		}
	}

	private void emitProperty(String key, Token value) {
		switch (value.getType()) {
			case BINARY:
				listener.property(key, value.getInteger(), IntegerType.BINARY);
				break;
			case BOOLEAN:
				listener.property(key, value.getBoolean());
				break;
			case FLOAT:
				listener.property(key, value.getFloat());
				break;
			case HEX:
				listener.property(key, value.getInteger(), IntegerType.HEX);
				break;
			case INTEGER:
				listener.property(key, value.getInteger(), IntegerType.PLAIN);
				break;
			case MULTILINE_STRING:
				listener.property(key, value.getString(), StringType.MULTI_LINE);
				break;
			case NULL:
				listener.nullProperty(key);
				break;
			case SIGNED:
				listener.property(key, value.getInteger(), IntegerType.SIGNED);
				break;
			case STRING:
				listener.property(key, value.getString(), StringType.SINGLE_LINE);
				break;
			default:
				assert false : value;
		}
		key = null;
		value = null;
	}

	private void emitValue(Token value) {
		switch (value.getType()) {
			case BINARY:
				listener.value(value.getInteger(), IntegerType.BINARY);
				break;
			case BOOLEAN:
				listener.value(value.getBoolean());
				break;
			case FLOAT:
				listener.value(value.getFloat());
				break;
			case HEX:
				listener.value(value.getInteger(), IntegerType.HEX);
				break;
			case INTEGER:
				listener.value(value.getInteger(), IntegerType.PLAIN);
				break;
			case MULTILINE_STRING:
				listener.value(value.getString(), StringType.MULTI_LINE);
				break;
			case NULL:
				listener.nullValue();
				break;
			case SIGNED:
				listener.value(value.getInteger(), IntegerType.SIGNED);
				break;
			case STRING:
				listener.value(value.getString(), StringType.SINGLE_LINE);
				break;
			default:
				assert false : value;
		}
		value = null;
	}

	private Token peek(int ahead) throws IOException {
		return tokenizer.peek(ahead);
	}

	private Token read() throws IOException {
		return tokenizer.read();
	}

	private void readMapOrObject() throws IOException {
		Token t;

		for (;;) {
			t = peek(0);
			ensureNotEOF(t, "expected key");
			if (consumeComment(t)) {
				continue;
			}

			if (t == Token.CLOSE_CURLY_BRACKET) {
				// Done
				read();
				return;
			}

			// Otherwise, read member
			readMember(false);
		}
	}

	private void readMember(boolean root) throws IOException {
		Token t;
		for (;;) {
			t = peek(0);
			ensureNotEOF(t, "expected key");
			if (consumeComment(t)) {
				continue;
			}
			if (t == Token.CLOSE_CURLY_BRACKET) {
				// Finished here
				return;
			}
			String key = readKey(root);
			readColon();
			readMemberValue(key);

			// Optionally consume up to one comma token
			readComments();
			readOptionalComma();
			return;
		}
	}

	private void readComments() throws IOException {
		Token t;
		for (;;) {
			t = peek(0);
			if (!consumeComment(t)) {
				return;
			}
		}
	}

	private void readOptionalComma() throws IOException {
		if (peek(0) == Token.COMMA) {
			read();
		}
	}

	private void readColon() throws IOException {
		Token t;
		for (;;) {
			t = peek(0);
			ensureNotEOF(t, "expected key");
			if (consumeComment(t)) {
				continue;
			}
			if (t == Token.COLON) {
				// Finished here
				read();
				return;
			}
			throw new IOException("Expected colon value but got " + t + " at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
		}
	}

	private String readKey(boolean root) throws IOException {
		Token t = read();
		// Expect a string, followed by a colon
		if (t.getType() != TokenType.STRING) {
			throw new IOException("Expected key value but got " + t + " at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
		}
		return t.getString();
	}

	private void readMemberValue(String key) throws IOException {
		// Either a literal, or a string followed by { (an object), or a { (a map), or a string followed by a [ (a list), or a [ (an array)
		Token t;
		for (;;) {
			t = tokenizer.peek(0);
			if (recursionLevel == 0) {
				ensureNotEOF(t, "expecting value");
			}
			if (t == Token.EOF) {
				// It's a null when at deeper recursion levels
				emitProperty(key, Token.NULL);
				return;
			} else if (consumeComment(t)) {
				continue;
			} else if (t.getType().isLiteral()) {
				read();
				emitProperty(key, t);
				return;
			} else if (t == Token.OPEN_CURLY_BRACKET) {
				read();
				listener.beginMap(key);
				readMapOrObject();
				listener.endMap();
				return;
			} else if (t == Token.OPEN_SQUARE_BRACKET) {
				read();
				listener.beginArray(key);
				readArray();
				listener.endArray();
				return;
			} else if (t.getType() == TokenType.STRING && tokenizer.peek(1) == Token.OPEN_CURLY_BRACKET) {
				read();
				read();
				listener.beginObject(key, t.getString());
				readMapOrObject();
				listener.endObject();
				return;
			} else if (t.getType() == TokenType.STRING && tokenizer.peek(1) == Token.OPEN_SQUARE_BRACKET) {
				read();
				read();
				listener.beginList(key, t.getString());
				readArray();
				listener.endList();
				return;
			} else if (t.getType() == TokenType.DIRECTIVE) {
				// Function call
				read();
				String result = listener.function(t.getString());
				if (result == null) {
					result = "null";
				} else {
					result += "\n";
				}
				// Parse result!
				new THJSONReader(new ByteArrayInputStream(result.getBytes(UTF_8)), listener, recursionLevel + 1).readMemberValue(key);
				return;
			} else if (!t.getType().isLiteral() && !(t.getType() == TokenType.STRING || t.getType() == TokenType.MULTILINE_STRING)) {
				throw new IOException("Unexpected " + t + " when expecting literal or string value at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
			}

			read();
			emitProperty(key, t);
			return;
		}
	}

	private void readArray() throws IOException {
		Token t;
		for (;;) {
			t = tokenizer.peek(0);
			ensureNotEOF(t, "expecting value");
			if (consumeComment(t)) {
				continue;
			}
			if (t == Token.CLOSE_SQUARE_BRACKET) {
				// We're done
				read();
				return;
			}
			readArrayValue();
			// Optionally consume up to one comma token
			readComments();
			readOptionalComma();
		}
	}

	private void readArrayValue() throws IOException {
		// Either a literal, or a string followed by { (an object), or a { (a map), or a string followed by a [ (a list), or a [ (an array)
		Token t;
		for (;;) {
			t = tokenizer.peek(0);
			if (recursionLevel == 0) {
				ensureNotEOF(t, "expecting value");
			}
			if (t == Token.EOF) {
				// It's a null when at deeper recursion levels
				emitValue(Token.NULL);
				return;
			} else if (consumeComment(t)) {
				continue;
			} else if (t.getType().isLiteral()) {
				read();
				emitValue(t);
				return;
			} else if (t == Token.OPEN_CURLY_BRACKET) {
				read();
				listener.beginMapValue();
				readMapOrObject();
				listener.endMap();
				return;
			} else if (t == Token.OPEN_SQUARE_BRACKET) {
				read();
				listener.beginArrayValue();
				readArray();
				listener.endArray();
				return;
			} else if (t.getType() == TokenType.STRING && tokenizer.peek(1) == Token.OPEN_CURLY_BRACKET) {
				read();
				read();
				listener.beginObjectValue(t.getString());
				readMapOrObject();
				listener.endObject();
				return;
			} else if (t.getType() == TokenType.STRING && tokenizer.peek(1) == Token.OPEN_SQUARE_BRACKET) {
				read();
				read();
				listener.beginListValue(t.getString());
				readArray();
				listener.endList();
				return;
			} else if (t.getType() == TokenType.DIRECTIVE) {
				// Function call
				read();
				String result = listener.function(t.getString());
				if (result == null) {
					result = "null";
				} else {
					result += "\n";
				}
				// Parse result!
				new THJSONReader(new ByteArrayInputStream(result.getBytes(UTF_8)), listener, recursionLevel + 1).readArrayValue();
				return;
			} else if (!t.getType().isLiteral() && !(t.getType() == TokenType.STRING || t.getType() == TokenType.MULTILINE_STRING)) {
				throw new IOException("Unexpected " + t + " when expecting literal or string value at line " + tokenizer.getLine() + ":" + tokenizer.getCol());
			}

			read();
			emitValue(t);
			return;
		}
	}

}
