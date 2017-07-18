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
import static net.puppygames.thjson.THJSONPrimitiveType.*;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Stack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

/**
 * A streaming Tagged Human JSON reader
 */
public class THJSONReader {

	private static final byte[] NULL_BYTES = "null".getBytes(UTF_8);
	private static final byte[] TRUE_BYTES = "true".getBytes(UTF_8);
	private static final byte[] FALSE_BYTES = "false".getBytes(UTF_8);
	private static final byte[] HEX_LITERAL_BYTES = "0x".getBytes(UTF_8);

	/** Assume a tab is this many spaces by default - used when reading triple quoted strings */
	private static final int DEFAULT_TAB_SIZE = 4;

	/**
	 * Determines if the incoming character is whitespace
	 * @param c
	 * @return true if c is a space, tab, newline, backspace, formfeed, or carriage return
	 */
	static boolean isWhitespace(int c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\b' || c == '\f';
	}

	private static boolean isBinaryDigit(int c) {
		return c == '0' || c == '1';
	}

	private static boolean isDecimalDigit(int c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isHexDigit(int c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	/**
	 * Determines if the subsequence of a is equal to the contents of b
	 * @param a
	 * @param start
	 * @return boolean
	 */
	private static boolean arrayEquals(byte[] a, int start, int end, byte[] b) {
		int length = end - start;
		if (length != b.length || a.length < length) {
			return false;
		}
		for (int i = 0; i < b.length; i++) {
			if (a[start++] != b[i]) {
				return false;
			}
		}
		return true;
	}

	private static THJSONPrimitiveType checkHexLiteral(byte[] src, int start, int end) {
		for (int i = start; i < end; i++) {
			if (!isHexDigit(src[i])) {
				// Not actually a hex literal after all - so it's a string
				return STRING;
			}
		}
		return INTEGER;
	}

	private static THJSONPrimitiveType checkBinaryLiteral(byte[] src, int start, int end) {
		for (int i = start; i < end; i++) {
			if (!isBinaryDigit(src[i])) {
				// Not actually a binary literal after all - so it's a string
				return STRING;
			}
		}
		return INTEGER;
	}

	private static THJSONPrimitiveType checkNumberLiteral(byte[] src, int start, int end) {
		// Skip initial sign if present
		if (src[start] == '+' || src[start] == '-') {
			start++;
		}

		// Otherwise, first char must be a digit or a dot
		if (src[start] != '.' && !isDecimalDigit(src[start])) {
			// Nope, so 'tis a string
			return STRING;
		}

		// These are floats: 1.0, .0, 1e1, 1.1e1, 1.1e+1, 1.1e-1
		int esignIndex = -1;
		int eIndex = -1;
		int dotIndex = -1;
		int digitsAfterE = 0;
		for (int i = start; i < end; i++) {
			byte c = src[i];
			if (c == '.') {
				if (dotIndex == -1) {
					// Dot must come before e
					if (eIndex != -1) {
						return STRING;
					}
					dotIndex = i;
				} else {
					return STRING;
				}
			} else if (c == 'e' || c == 'E') {
				if (eIndex == -1) {
					// e must come before sign
					if (esignIndex != -1) {
						return STRING;
					}
					eIndex = i;
				} else {
					return STRING;
				}
			} else if (c == '+' || c == '-') {
				if (esignIndex == -1) {
					// esign must come after an e
					if (eIndex == -1) {
						return STRING;
					}
					esignIndex = i;
				} else {
					return STRING;
				}
			} else if (!isDecimalDigit(c)) {
				return STRING;
			} else if (eIndex != -1) {
				digitsAfterE++;
			}
		}
		if (eIndex != -1 && digitsAfterE == 0) {
			// Must be at least one digit after the e
			return STRING;
		}
		if (dotIndex != -1 || eIndex != -1) {
			// It's a float
			return FLOAT;
		} else {
			// Must be an actual int
			return INTEGER;
		}
	}

	/**
	 * Determines the primitive type of the incoming sequence of bytes.
	 * @param src
	 * @param start
	 * @param end
	 * @return the {@link THJSONPrimitiveType}; never null
	 */
	private static THJSONPrimitiveType determinePrimitiveType(byte[] src, int start, int end) {
		if (end - start == 0 || arrayEquals(src, start, end, NULL_BYTES)) {
			return NULL;
		}
		if (arrayEquals(src, start, end, TRUE_BYTES) || arrayEquals(src, start, end, FALSE_BYTES)) {
			return BOOLEAN;
		}
		// What sort of number is it?
		if (arrayEquals(src, start, start + 2, HEX_LITERAL_BYTES)) {
			// Unsigned hex integer
			return checkHexLiteral(src, start + 2, end);
		} else if (src[start] == '%') {
			// Unsigned binary integer
			return checkBinaryLiteral(src, start + 1, end);
		} else {
			// Either float or integer
			return checkNumberLiteral(src, start, end);
		}
	}

	public static void readResource(String resourceURL, THJSONListener listener) throws IOException {
		URL url = THJSONReader.class.getResource(resourceURL);
		URLConnection conn = url.openConnection();
		int length = conn.getContentLength();
		if (length == -1) {
			throw new IOException("length of " + resourceURL + " cannot be determined");
		}
		byte[] buffer = new byte[length];

		try (InputStream is = conn.getInputStream()) {
			int bytesRead;
			int pos = 0;
			while (pos < length && (bytesRead = is.read(buffer, pos, length - pos)) != -1) {
				pos += bytesRead;
			}
			new THJSONReader(buffer, 0, length, listener);
		}
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
		Map<String, Object> map = convertToMap("test2.thjson");
		System.out.println(map);

		JsonObject json = convertToJSON("test2.thjson");
		StringWriter out = new StringWriter();
		JsonWriter writer = new JsonWriter(out);
		writer.setIndent("    ");
		Gson gson = new Gson();
		gson.toJson(json, writer);
		writer.flush();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out.getBuffer().toString()), null);
		System.out.println(out.getBuffer().toString());
	}

	/** This reports the events */
	private final THJSONListener listener;

	/** Parse this data */
	private final byte[] in;

	/** Length */
	private final int length;

	/** Byte position */
	private int pos;

	/** Current line number (1-based) */
	private int line = 1;

	/** Current column number (1-based) */
	private int col = 0;

	/** Ignore the next \n in the input */
	private boolean ignoreNextSlashN = false;

	/** New line next character */
	private boolean newLineNext = false;

	/** Alignment column for triple-quoted string */
	private int align;

	/** Start of current token */
	private int start;

	/** Key position */
	private int keyStart, keyEnd;

	/** Value position */
	private int valueStart, valueEnd;

	/** Tab size */
	private int tabSize = DEFAULT_TAB_SIZE;

	/** Whether we got a root brace */
	private boolean hasRootBrace;

	/** Whether we closed the root brace */
	private boolean closedRootBrace;

	private final Stack<Runnable> objectStack = new Stack<>();

	private class AccessibleByteArrayOutputStream extends ByteArrayOutputStream {

		AccessibleByteArrayOutputStream(int size) {
			super(size);
		}

		byte[] getBuf() {
			return buf;
		}
	}

	/** Key source: used when escaping */
	private AccessibleByteArrayOutputStream keySource;

	/** Value source: used when escaping */
	private AccessibleByteArrayOutputStream valueSource;

	/** State */
	private State state = this::readRoot;
	private Stack<State> stack = new Stack<>();

	private interface State {
		void read(int c) throws IOException;
	}

	/** Temp byte array output */
	private AccessibleByteArrayOutputStream baos;

	/**
	 * C'tor
	 * @param in Cannot be null
	 * @param offset Byte offset into in at which to start
	 * @param length Number of bytes to parse
	 * @param listener Cannot be null
	 * @throws IOException if the stream is corrupt
	 */
	public THJSONReader(byte[] in, int offset, int length, THJSONListener listener) throws IOException {
		this.in = requireNonNull(in, "in cannot be null");
		this.listener = requireNonNull(listener, "listener cannot be null");
		if (pos < 0) {
			throw new IllegalArgumentException("pos must be >= 0");
		}
		if (length < 0) {
			throw new IllegalArgumentException("length must be >= 0");
		}
		if (pos + length > in.length) {
			throw new IllegalArgumentException("pos + length is greater than buffer size: " + (pos + length) + " vs " + in.length);
		}
		this.pos = offset;
		this.length = length;

		listener.begin();
		parse();
		listener.end();
	}

	/**
	 * Sets the tab size. This is used when working out indentation for triple quoted strings.
	 * @param tabSize
	 */
	public void setTabSize(int tabSize) {
		this.tabSize = tabSize;
	}

	/**
	 * Gets the tab size used when calculating indentations for triple quoted strings. Defaults to {@link #DEFAULT_TAB_SIZE}
	 * @return tab size, in spaces
	 */
	public int getTabSize() {
		return tabSize;
	}

	/**
	 * The incoming character is in UCS2 (16-bit) format; we will write it out to the temporarary baos buffer as a UTF8 sequence
	 * @param c
	 */
	private void writeUTF8fromUCS2(int c, ByteArrayOutputStream out) {
		if (c < 0x80) {
			out.write(c);
		} else if (c < 0x800) {
			int byte1 = 0x80 | (c & 0x3F);
			c >>= 6;
			int byte0 = 0xC0 | c;
			out.write(byte0);
			out.write(byte1);
		} else {
			int byte2 = 0x80 | (c & 0x3F);
			c >>= 6;
			int byte1 = 0x80 | (c & 0x3F);
			c >>= 6;
			int byte0 = 0xE0 | c;
			out.write(byte0);
			out.write(byte1);
			out.write(byte2);
		}
	}

	private void emitValue() {
		byte[] vs = in;
		if (valueSource != null) {
			vs = valueSource.getBuf();
			valueSource = null;
		}
		if (valueEnd - valueStart < 0) {
			throw new RuntimeException("Illegal value arguments: " + valueStart + ", " + valueEnd);
		}
		listener.value(vs, determinePrimitiveType(vs, valueStart, valueEnd), valueStart, valueEnd - valueStart);
	}

	private void emitStringValue() {
		byte[] vs = in;
		if (valueSource != null) {
			vs = valueSource.getBuf();
			valueSource = null;
		}
		if (valueEnd - valueStart < 1) {
			throw new RuntimeException("Illegal value arguments: " + valueStart + ", " + valueEnd);
		}
		listener.value(vs, STRING, valueStart, valueEnd - valueStart);
	}

	private void emitProperty() {
		byte[] ks = in;
		if (keySource != null) {
			ks = keySource.getBuf();
			keySource = null;
		}
		byte[] vs = in;
		if (valueSource != null) {
			vs = valueSource.getBuf();
			valueSource = null;
		}
		if (keyEnd - keyStart < 1) {
			throw new RuntimeException("Illegal key arguments: " + keyStart + ", " + keyEnd);
		}
		if (valueEnd - valueStart < 0) {
			throw new RuntimeException("Illegal value arguments: " + valueStart + ", " + valueEnd);
		}
		listener.property(ks, keyStart, keyEnd - keyStart, vs, determinePrimitiveType(vs, valueStart, valueEnd), valueStart, valueEnd - valueStart);
	}

	private void emitStringProperty() {
		byte[] ks = in;
		if (keySource != null) {
			ks = keySource.getBuf();
			keySource = null;
		}
		byte[] vs = in;
		if (valueSource != null) {
			vs = valueSource.getBuf();
			valueSource = null;
		}
		if (keyEnd - keyStart < 1) {
			throw new RuntimeException("Illegal key arguments: " + keyStart + ", " + keyEnd);
		}
		if (valueEnd - valueStart < 1) {
			throw new RuntimeException("Illegal value arguments: " + valueStart + ", " + valueEnd);
		}
		listener.property(ks, keyStart, keyEnd - keyStart, vs, STRING, valueStart, valueEnd - valueStart);
	}

	private int trimLeadingWhitespace(int from) {
		for (;;) {
			int cc = in[from];
			if (isWhitespace(cc)) {
				from++;
			} else {
				return from;
			}
		}
	}

	private int trimTrailingWhitespace(int from, byte[] buf) {
		for (;;) {
			int cc = buf[from - 1];
			if (isWhitespace(cc)) {
				from--;
			} else {
				return from;
			}
		}
	}

	private int trimTrailingWhitespaceAndComma(int from, byte[] buf) {
		for (;;) {
			int cc = buf[from - 1];
			if (cc == ',') {
				from--;
			} else if (isWhitespace(cc)) {
				from--;
			} else {
				return from;
			}
		}
	}

	/**
	 * Read the next byte from the input.
	 * @return an unsigned byte, or -1 if we've reached EOF
	 * @throws IOException
	 */
	private int read() {
		if (newLineNext) {
			newLineNext = false;
			line++;
			col = 0;
		}
		if (pos == length) {
			// EOF
			return -1;
		}
		int c = in[pos++] & 0xFF;
		if (c == '\r') {
			// Turn into \n
			c = '\n';
			ignoreNextSlashN = true;
			newLineNext = true;
		} else if (c == '\n') {
			if (ignoreNextSlashN) {
				ignoreNextSlashN = false;
				// Recurse
				return read();
			} else {
				newLineNext = true;
			}
		} else if (c == '\t') {
			col += tabSize;
			col -= col % tabSize;
		} else {
			col++;
		}
		// System.out.print((char) c);
		return c;
	}

	/**
	 * Peek at the n'th character ahead
	 * @param n
	 * @return the character or -1 if we peek past the end
	 */
	private int peek(int n) {
		if (pos + n >= in.length) {
			return -1;
		} else {
			return in[pos + n];
		}
	}

	/**
	 * Accept whitespace, comments, root braces, members
	 */
	private void readRoot(int c) throws IOException {
		switch (c) {
			case -1:
				// End
				if ((hasRootBrace && closedRootBrace) || !hasRootBrace) {
					// Done
					return;
				} else {
					throw new IOException("Unexpected EOF at line " + line + ":" + col);
				}
			case ' ':
			case '\t':
			case '\n':
			case '\r':
			case '\b':
			case '\f':
				// Ignore
				return;
			case '{':
				if (hasRootBrace) {
					// Complain
					throw new IOException("Unexpected { looking for member at line " + line + ":" + col);
				}
				// Root brace (optional construct).
				hasRootBrace = true;
				return;
			case '}':
				if (!hasRootBrace) {
					throw new IOException("Unexpected } looking for member at line " + line + ":" + col);
				}
				if (closedRootBrace) {
					throw new IOException("Unexpected } at line " + line + ":" + col);
				}
				hasRootBrace = false;
				closedRootBrace = true;
				return;
			case '@':
				// Directive. Pass to listener.
				start = pos;
				push(this::readDirective);
				return;
			case '#':
				// Comment to end of line
				start = pos;
				push(this::readHashComment);
				return;
			case '/':
				// Start of C comment or C++ comment
				c = peek(0);
				if (c == '/') {
					start = ++pos;
					push(this::readSlashSlashComment);
					return;
				} else if (c == '*') {
					start = pos++;
					push(this::readBlockComment);
					return;
				}
				// Intentional fallthrough
			default:
				if (closedRootBrace) {
					// Only allowed comments from now on
					throw new IOException("Unexpected " + (char) c + "(0x" + Integer.toHexString(c) + ") after final root brace at line " + line + ":" + col);
				}
				// We must be looking for a member. Don't allow root braces any more
				start = pos--;
				push(this::readMember);
		}

	}

	/**
	 * Accept anything up to a newline
	 */
	private void readHashComment(int c) {
		if (c == '\n' || c == -1) {
			// Done!
			start = trimLeadingWhitespace(start);
			int end = trimTrailingWhitespace(pos, in);
			listener.comment(in, start, end - start, THJSONCommentType.HASH);
			pop();
		}
	}

	/**
	 * Accept anything up to a newline, EOF, or single line comment
	 */
	private void readDirective(int c) {
		boolean endDirective = false;
		State newState = null;
		int posd = 0;

		if (c == '\n' || c == -1) {
			endDirective = true;
		} else if (c == '#') {
			endDirective = true;
			newState = this::readHashComment;
		} else if (c == '/' && peek(0) == '/') {
			endDirective = true;
			newState = this::readSlashSlashComment;
			posd = 1;
		}

		if (endDirective) {
			int end = trimTrailingWhitespace(pos - 1, in);
			pos += posd;
			listener.directive(in, start, end - start);
			pop();
			if (newState != null) {
				start = pos;
				push(newState);
			}
		}
	}

	/**
	 * Accept anything up to a newline, comma, or comment
	 */
	private void readFunctionAsMemberValue(int c) throws IOException {
		readFunction(c, this::emitProperty);
	}

	/**
	 * Accept anything up to a newline, comma, or comment
	 */
	private void readFunctionAsArrayValue(int c) throws IOException {
		readFunction(c, this::emitValue);
	}

	/**
	 * Accept anything up to a newline, comma, or comment
	 */
	private void readFunction(int c, Runnable r) throws IOException {
		boolean endFunction = false;
		State newState = null;
		int posd = 0;

		if (c == '\n' || c == ',') {
			endFunction = true;
		} else if (c == '#') {
			endFunction = true;
			newState = this::readHashComment;
		} else if (c == '/') {
			if (peek(0) == '/') {
				endFunction = true;
				newState = this::readSlashSlashComment;
				posd = 1;
			} else if (peek(0) == '*') {
				endFunction = true;
				newState = this::readBlockComment;
				posd = 1;
			}
		}

		if (endFunction) {
			int end = trimTrailingWhitespaceAndComma(pos - 1, in);
			pos += posd;
			String ret = listener.function(in, start, end - start);
			byte[] bytes = ret.getBytes(UTF_8);
			valueStart = maybeInitBAOS();
			valueSource = baos;
			valueSource.write(bytes);
			valueEnd = valueSource.size();
			r.run();
			pop();
			if (newState != null) {
				start = pos;
				push(newState);
			}
		}
	}

	/**
	 * Accept anything up to a newline
	 */
	private void readSlashSlashComment(int c) {
		if (c == '\n' || c == -1) {
			// Done!
			start = trimLeadingWhitespace(start);
			int end = trimTrailingWhitespace(pos, in);
			listener.comment(in, start, end - start, THJSONCommentType.SLASHSLASH);
			pop();
		}
	}

	/**
	 * Maybe terminate when we get a *
	 */
	private void readBlockComment(int c) {
		if (c == '*' && peek(0) == '/') {
			pop();
			listener.comment(in, start, pos - start, THJSONCommentType.BLOCK);
			pos++;
		}
	}

	/**
	 * Looking for String : value
	 */
	private void readMember(int c) {
		pop();
		push(this::readMemberValue);
		push(this::readKey);
		start = --pos;
	}

	/**
	 * Can be true | false | null | [object] | [array] | [number] | [string]
	 */
	private void readMemberValue(int c) {
		if (c == '"') {
			valueStart = pos;
			pop();
			push(this::readQuotedStringMemberValue);
			return;
		}

		if (c == '\'' && peek(0) == '\'' && peek(1) == '\'') {
			pos += 2;
			valueStart = maybeInitBAOS();
			valueSource = baos;
			align = col - 1;
			pop();
			push(this::readTripleQuotedStringPropertyFirstLine);
			return;
		}

		if (c == '{') {
			listener.beginMap(in, keyStart, keyEnd - keyStart);
			pop();
			pushObject(listener::endMap);
			push(this::readMapMemberValue);
			push(this::readWhitespace);
			return;
		}

		if (c == '[') {
			listener.beginArray(in, keyStart, keyEnd - keyStart);
			pop();
			pushObject(listener::endArray);
			push(this::readArrayMemberValue);
			push(this::readWhitespace);
			return;
		}

		if (c == '@') {
			pop();
			push(this::readFunctionAsMemberValue);
			return;
		}

		valueStart = --pos;
		pop();
		push(this::readSimpleMemberValueOrMaybeClass);
	}

	private void pushObject(Runnable r) {
		objectStack.push(r);
	}

	private void popObject() {
		objectStack.pop().run();
	}

	/**
	 * Reading a {} map of key:value pairs.
	 */
	private void readMapMemberValue(int c) {
		// We must be looking for a member or } to finish
		if (c == '}') {
			// Done
			popObject();
			pop();
			push(this::readWhitespaceAndComma);
			return;
		}

		start = pos--;
		push(this::readWhitespace);
		push(this::readMember);
	}

	private void readArrayMemberValue(int c) {
		// Read values or ] to finish
		if (c == ']') {
			// Done
			popObject();
			pop();
			return;
		}

		start = pos--;
		push(this::readArrayValue);
	}

	private void readArrayValue(int c) {
		if (c == '"') {
			valueStart = pos;
			pop();
			push(this::readWhitespace);
			push(this::readQuotedStringArrayValue);
			return;
		}

		if (c == '\'' && peek(0) == '\'' && peek(1) == '\'') {
			align = col - 2;
			pos += 2;
			valueStart = maybeInitBAOS();
			valueSource = baos;
			pop();
			push(this::readWhitespaceAndComma);
			push(this::readTripleQuotedStringValueFirstLine);
			return;
		}

		if (c == '{') {
			listener.beginMapValue(in);
			pushObject(listener::endMap);
			pop();
			push(this::readMapMemberValue);
			push(this::readWhitespace);
			return;
		}

		if (c == '[') {
			listener.beginArrayValue(in);
			pop();
			pushObject(listener::endArray);
			push(this::readArrayMemberValue);
			push(this::readWhitespace);
			return;
		}

		if (c == '@') {
			push(this::readFunctionAsArrayValue);
			return;
		}

		valueStart = --pos;
		pop();
		push(this::readWhitespace);
		push(this::readSimpleArrayValueOrMaybeClass);
	}

	private boolean hasComma(int from, int to, byte[] buf) {
		for (int i = from; i < to; i++) {
			if (buf[i] == ',') {
				return true;
			}
		}
		return false;
	}

	private void readSimpleArrayValueOrMaybeClass(int c) {
		switch (c) {
			case '{':
				// Object; string we've read so far is class name... unless it was numerical, or got a comma in it
				valueStart = trimLeadingWhitespace(valueStart);
				valueEnd = trimTrailingWhitespace(pos - 1, in);
				if (determinePrimitiveType(in, valueStart, valueEnd) != STRING || hasComma(valueStart, valueEnd, in)) {
					// Not a classname, looks like another array element
					if (valueEnd - valueStart > 0) {
						emitValue();
					}
					listener.beginMapValue(in);
					pop();
					pushObject(listener::endMap);
					push(this::readArrayMemberValue);
					push(this::readWhitespace);
				} else {
					listener.beginObjectValue(in, valueStart, valueEnd - valueStart);
					pop();
					pushObject(listener::endObject);
					push(this::readArrayMemberValue);
					push(this::readWhitespace);
				}
				return;
			case '[':
				// List; string we've read so far is class name... unless it was numerical, or got a comma in it
				valueStart = trimLeadingWhitespace(valueStart);
				valueEnd = trimTrailingWhitespace(pos - 1, in);
				if (determinePrimitiveType(in, valueStart, valueEnd) != STRING || hasComma(valueStart, valueEnd, in)) {
					if (valueEnd - valueStart > 0) {
						emitValue();
					}
					listener.beginArrayValue(in);
					pop();
					pushObject(listener::endArray);
					push(this::readArrayMemberValue);
					push(this::readWhitespace);
				} else {
					listener.beginListValue(in, valueStart, valueEnd - valueStart);
					pop();
					pushObject(listener::endList);
					push(this::readArrayMemberValue);
					push(this::readWhitespace);
				}
				return;
			case ',':
				// Special case... if what we have so far is a valid number, true, false, or null, we end the value; otherwise carry on
				if (determinePrimitiveType(in, valueStart, pos - 1) == STRING) {
					return;
				}
				// Intentional fallthrough
			case '\n':
				// End of line - that's the whole value
				valueStart = trimLeadingWhitespace(valueStart);
				valueEnd = trimTrailingWhitespaceAndComma(pos, in);
				if (valueEnd - valueStart > 0) {
					emitValue();
				}
				pop();
				return;
			case ']':
				// End the list, possibly in a value
				valueStart = trimLeadingWhitespace(valueStart);
				valueEnd = trimTrailingWhitespaceAndComma(--pos, in);
				if (valueEnd - valueStart > 0) {
					emitValue();
				}
				pop();
				return;
		}
	}

	private void readSimpleMemberValueOrMaybeClass(int c) {
		switch (c) {
			case '{':
				// Object; string we've read so far is class name.
				valueStart = trimLeadingWhitespace(valueStart);
				valueEnd = trimTrailingWhitespaceAndComma(pos - 1, in);
				listener.beginObject(in, keyStart, keyEnd - keyStart, valueStart, valueEnd - valueStart);
				pop();
				pushObject(listener::endObject);
				push(this::readMapMemberValue);
				push(this::readWhitespace);
				return;
			case '[':
				// List; string we've read so far is class name.
				valueStart = trimLeadingWhitespace(valueStart);
				valueEnd = trimTrailingWhitespaceAndComma(pos - 1, in);
				listener.beginList(in, keyStart, keyEnd - keyStart, valueStart, valueEnd - valueStart);
				pop();
				pushObject(listener::endList);
				push(this::readArrayMemberValue);
				push(this::readWhitespace);
				return;
			case ',':
				// Special case... if what we have so far is a valid number, true, false, or null, we end the value; otherwise carry on
				if (determinePrimitiveType(in, valueStart, pos - 1) == STRING) {
					return;
				}
				// Intentional fallthrough
			case '\n':
				// End of line - that's the whole value
				valueStart = trimLeadingWhitespace(valueStart);
				valueEnd = trimTrailingWhitespaceAndComma(pos, in);
				emitProperty();
				pop();
				return;
			case '}':
				// End the object
				valueStart = trimLeadingWhitespace(valueStart);
				valueEnd = trimTrailingWhitespaceAndComma(--pos, in);
				emitProperty();
				pop();
				return;
		}
	}

	/**
	 * Looking for quoted-string | key-string
	 * @param c
	 * @throws IOException
	 */
	private void readKey(int c) throws IOException {
		keySource = null;

		if (c == '"') {
			// Quoted string
			keyStart = pos;
			pop();
			push(this::readQuotedStringKey);
			return;
		}

		keyStart = --pos;
		pop();
		push(this::readQuotelessKey);
	}

	private void readQuotelessKey(int c) throws IOException {
		switch (c) {
			case ',':
			case '[':
			case ']':
			case '{':
			case '}':
				// These characters are not allowed
				throw new IOException("Unexpected " + (char) c + " at line " + line + ":" + col);
			case '\n':
				throw new IOException("Unexpected newline reading key at line " + line + ":" + col);
			case ':':
				pos--;
				// Intentional fallthrough
			case '\t':
			case ' ':
			case '\b':
			case '\f':
				// Whitespace means end of key; now we need to find the colon
				keyEnd = trimTrailingWhitespace(pos, in);
				pop();
				push(this::readUpToColon);
				return;
		}
	}

	/**
	 * Ignore whitespace until we get to a colon, then we've got our key
	 */
	private void readUpToColon(int c) throws IOException {
		switch (c) {
			case ':':
				// Got the key. Now ignore gap between colon and start of data.
				pop();
				push(this::readBlankGapAfterColon);
				return;
			case '\n':
				throw new IOException("Unexpected newline expecting colon at line " + line + ":" + col);
			case '\t':
			case '\r':
			case '\b':
			case '\f':
			case ' ':
				return;
			default:
				throw new IOException("Expecting : or , got " + (char) c + " at line " + line + ":" + col);

		}
	}

	/**
	 * Read blank gap after colon until we hit something. If we hit a comment or newline, complain
	 */
	private void readBlankGapAfterColon(int c) {
		switch (c) {
			case ' ':
			case '\t':
			case '\b':
			case '\f':
			case '\n':
				return;
			case '#':
				push(this::readHashComment);
				return;
			// throw new IOException("Got comment but expecting value at line " + line + ":" + col);
			case '/':
				// Might be start of C comment or C++ comment. But we don't want that!
				valueStart = pos;
				c = peek(0);
				if (c == '/') {
					pos++;
					start = pos;
					push(this::readSlashSlashComment);
				} else if (c == '*') {
					start = pos;
					push(this::readBlockComment);
					// throw new IOException("Got comment but expecting value at line " + line + " : " + col);
				}
				return;
			default:
				// We're done
				valueStart = --pos;
				pop();
		}
	}

	/**
	 * Read whitespace until we hit something. If we hit a comment, read that.
	 */
	private void readWhitespace(int c) throws IOException {
		if (isWhitespace(c)) {
			return;
		}

		switch (c) {
			case '#':
				// Comment to end of line
				start = pos;
				push(this::readHashComment);
				return;
			case '/':
				// Start of C comment or C++ comment
				c = peek(0);
				if (c == '/') {
					start = ++pos;
					push(this::readSlashSlashComment);
				} else if (c == '*') {
					start = pos;
					push(this::readBlockComment);
				} else {
					throw new IOException("Expected / or * at line " + line + ":" + col);
				}
				return;
			default:
				// We're done
				pos--;
				pop();
		}
	}

	/**
	 * Read whitespace until we hit something. If we hit a comment, read that.
	 */
	private void readWhitespaceAndComma(int c) throws IOException {
		switch (c) {
			case ' ':
			case '\t':
			case '\b':
			case '\f':
			case '\n':
				return;
			case ',':
				// Ok got the comma, now we only want whitespace
				pop();
				push(this::readWhitespace);
				return;
			case '#':
				// Comment to end of line
				start = pos;
				push(this::readHashComment);
				return;
			case '/':
				// Start of C comment or C++ comment
				c = peek(0);
				if (c == '/') {
					start = ++pos;
					push(this::readSlashSlashComment);
				} else if (c == '*') {
					start = pos;
					push(this::readBlockComment);
				} else {
					throw new IOException("Expected / or * at line " + line + ":" + col);
				}
				return;
			default:
				// We're done
				pos--;
				pop();
		}
	}

	private int maybeInitBAOS() {
		if (baos == null) {
			baos = new AccessibleByteArrayOutputStream(1024);
		}
		return baos.size();
	}

	/**
	 * Read all characters until we get to an unescaped " character, then ...
	 */
	private void readQuotedStringKey(int c) throws IOException {
		if (c == '\\') {
			// Switch to byte array reading
			int newStart = maybeInitBAOS();
			keySource = baos;
			baos.write(in, keyStart, pos - keyStart - 1);
			keyStart = newStart;
			push(this::readQuotedStringKeyEscape);
			return;
		}

		if (c == '"') {
			// Got it
			if (keySource == null) {
				// Not escaped
				keyEnd = pos - 1;
			} else {
				keyEnd = keyStart + keySource.size();
			}
			pop();
			push(this::readUpToColon);
			return;
		}

		if (keySource != null) {
			// We need to collect characters
			keySource.write(c);
		}

		if (c == '\n') {
			throw new IOException("Unexpected newline reading quoted key at line " + line + ":" + col);
		}
	}

	/**
	 * Read all characters until we get to an unescaped " character, then ...
	 */
	private void readQuotedStringMemberValue(int c) throws IOException {
		if (c == '\\') {
			// Maybe switch to byte array reading
			if (valueSource == null) {
				int newStart = maybeInitBAOS();
				valueSource = baos;
				baos.write(in, valueStart, pos - valueStart - 1);
				valueStart = newStart;
			}
			push(this::readQuotedStringMemberValueEscape);
			return;
		}

		if (c == '"') {
			// Got it
			if (valueSource == null) {
				// Not escaped
				valueEnd = pos - 1;
			} else {
				valueEnd = valueSource.size();
			}
			emitStringProperty();
			pop();
			push(this::readWhitespaceAndComma);
			return;
		}

		if (valueSource != null) {
			// We need to collect characters
			valueSource.write(c);
		}

		if (c == '\n') {
			throw new IOException("Unexpected newline reading quoted string at line " + line + ":" + col);
		}
	}

	/**
	 * Read all characters until we get to an unescaped " character, then ...
	 */
	private void readQuotedStringArrayValue(int c) throws IOException {
		if (c == '\\') {
			// Maybe switch to byte array reading
			if (valueSource == null) {
				int newStart = maybeInitBAOS();
				valueSource = baos;
				baos.write(in, valueStart, pos - valueStart - 1);
				valueStart = newStart;
			}
			push(this::readQuotedStringArrayValueEscape);
			return;
		}

		if (c == '"') {
			// Got it
			if (valueSource == null) {
				// Not escaped
				valueEnd = pos - 1;
			} else {
				valueEnd = valueSource.size();
			}
			emitStringValue();
			pop();
			push(this::readWhitespaceAndComma);
			return;
		}

		if (valueSource != null) {
			// We need to collect characters
			valueSource.write(c);
		}

		if (c == '\n') {
			throw new IOException("Unexpected newline reading quoted string at line " + line + ":" + col);
		}
	}

	private void readQuotedStringMemberValueEscape(int c) throws IOException {
		readEscape(c, valueSource);
	}

	private void readQuotedStringArrayValueEscape(int c) throws IOException {
		readEscape(c, valueSource);
	}

	private void readQuotedStringKeyEscape(int c) throws IOException {
		readEscape(c, keySource);
	}

	/**
	 * Read a hex digit (case insensitive)
	 * @return 0...15
	 * @throws IOException
	 */
	private int readHexDigit() throws IOException {
		int c = read();
		if (c == -1) {
			throw new EOFException("Unexpected EOF expecting hex digit at line " + line + ":" + col);
		}
		if (c >= '0' && c <= '9') {
			return c - '0';
		}
		if (c >= 'a' && c <= 'f') {
			return (c - 'a') + 10;
		}
		if (c >= 'A' && c <= 'F') {
			return (c - 'A') + 10;
		}
		throw new IOException("Expected hex digit but got " + (char) c + " at line " + line + ":" + col);
	}

	/**
	 * The next four chars must be hex digits referring to a 16-bit unicode escape
	 * @return a 16-bit integer that must be encoded in UTF8
	 * @throws IOException
	 */
	private int readUnicodeEscape() throws IOException {
		return (readHexDigit() << 12) | (readHexDigit() << 8) | (readHexDigit() << 4) | readHexDigit();
	}

	private void readEscape(int c, ByteArrayOutputStream out) throws IOException {
		switch (c) {
			case 't':
				out.write('\t');
				break;
			case 'n':
				out.write('\n');
				break;
			case 'r':
				out.write('\r');
				break;
			case 'b':
				out.write('\b');
				break;
			case 'f':
				out.write('\f');
				break;
			case '"':
				out.write('\"');
				break;
			case '\\':
				out.write('\\');
				break;
			case 'u':
				// 4-char hex escape
				int escaped = readUnicodeEscape();
				writeUTF8fromUCS2(escaped, out);
				break;
			default:
				throw new IOException("Unknown escape sequence " + (char) c + " at line " + line + ":" + col);
		}
		pop();
	}

	private void readTripleQuotedStringPropertyFirstLine(int c) {
		if (maybeEndTripleQuote(c, this::emitStringProperty)) {
			return;
		}

		// Ignore whitespace after the ''' sequence
		if (c == '\t' || c == ' ' || c == '\f' || c == '\b') {
			return;
		}

		if (c == '\n') {
			pop();
			push(this::readTripleQuotedStringPropertyRemaining);
			push(this::readStartColumnWhitespace);
			return;
		}

		valueSource.write(c);
		pop();
		push(this::readTripleQuotedStringPropertyFirstLineRemaining);
	}

	private void readTripleQuotedStringPropertyFirstLineRemaining(int c) {
		if (maybeEndTripleQuote(c, this::emitStringProperty)) {
			return;
		}

		// Collect characters
		valueSource.write(c);

		if (c == '\n') {
			pop();
			push(this::readTripleQuotedStringPropertyRemaining);
			push(this::readStartColumnWhitespace);
			return;
		}

	}

	private void readTripleQuotedStringPropertyRemaining(int c) {
		if (maybeEndTripleQuote(c, this::emitStringProperty)) {
			return;
		}

		// Collect characters
		valueSource.write(c);

		// When we get a newline, ignore up to the start column's worth of whitespace.
		if (c == '\n') {
			push(this::readStartColumnWhitespace);
			return;
		}
	}

	private boolean maybeEndTripleQuote(int c, Runnable r) {
		if (c == '\'' && peek(0) == '\'' && peek(1) == '\'') {
			// The end of the string
			pop();
			valueEnd = trimTrailingWhitespaceAndComma(valueSource.size(), valueSource.getBuf());
			r.run();
			pos += 2;
			return true;
		} else {
			return false;
		}
	}

	private void readStartColumnWhitespace(int c) {
		if (col >= align) {
			// We're ready to start reading actual characters whatever
			pos--;
			pop();
			return;
		}

		if (c == ' ' || c == '\t') {
			// Ignore
			return;
		}

		// We got characters a bit earlier than expected so start reading now
		pos--;
		pop();
	}

	private void readTripleQuotedStringValueFirstLine(int c) {
		if (maybeEndTripleQuote(c, this::emitStringValue)) {
			return;
		}

		// Ignore whitespace after the ''' sequence
		if (c == '\t' || c == ' ' || c == '\f' || c == '\b') {
			return;
		}

		if (c == '\n') {
			pop();
			push(this::readTripleQuotedStringValueRemaining);
			push(this::readStartColumnWhitespace);
			return;
		}

		// Collect characters
		valueSource.write(c);
		push(this::readTripleQuotedStringValueFirstLineRemaining);
	}

	private void readTripleQuotedStringValueFirstLineRemaining(int c) {
		if (maybeEndTripleQuote(c, this::emitStringValue)) {
			return;
		}

		// Collect characters
		valueSource.write(c);

		if (c == '\n') {
			pop();
			push(this::readTripleQuotedStringValueRemaining);
			push(this::readStartColumnWhitespace);
			return;
		}
	}

	private void readTripleQuotedStringValueRemaining(int c) {
		if (maybeEndTripleQuote(c, this::emitStringValue)) {
			return;
		}

		// Collect characters
		valueSource.write(c);

		// When we get a newline, ignore up to the start column's worth of whitespace.
		if (c == '\n') {
			push(this::readStartColumnWhitespace);
			return;
		}
	}

	private void push(State newState) {
		stack.push(state);
		state = newState;
	}

	private void pop() {
		state = stack.pop();
	}

	/**
	 * Parse!
	 */
	private void parse() throws IOException {
		int c;
		while ((c = read()) != -1) {
			state.read(c);
		}
		state.read(c);
		if (!stack.isEmpty() || (hasRootBrace && !closedRootBrace)) {
			throw new EOFException("Unexpected EOF at line " + line + ":" + col + " (stack: " + stack.size() + ")");
		}
	}
}
