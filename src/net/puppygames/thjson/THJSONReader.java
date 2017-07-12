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

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * A streaming Tagged Human JSON reader
 */
public class THJSONReader {

	private static final byte[] NULL_BYTES = "null".getBytes(UTF_8);
	private static final byte[] TRUE_BYTES = "true".getBytes(UTF_8);
	private static final byte[] FALSE_BYTES = "false".getBytes(UTF_8);
	private static final byte[] HEX_LITERAL_BYTES = "0x".getBytes(UTF_8);

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
		readResource(resourceURL, converter);
		return converter.getMap();
	}

	public static void main(String[] args) throws Exception {
		System.out.println(convertToMap("test2.thjson"));
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

	/** Current line length, reset when we get a \n */
	private int linePos;

	/** Whether we're done */
	private boolean done;

	/** Object depth */
	private int objectDepth;

	/** Bracket depth */
	private int bracketDepth;

	/** List depth */
	private int listDepth;

	/** Tag:start */
	private int currentStart, currentEnd;

	/** Temp byte array output */
	private ByteArrayOutputStream baos;

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
		read(ignoreWhitespace(), false, false, 0, 0);
		listener.end();
	}

	/**
	 * Ignore whitespace until we get non-whitespace
	 * @return the first non-whitespace character
	 * @throws IOException
	 */
	private int ignoreWhitespace() throws IOException {
		int c = 0;
		do {
			c = read();
			if (c == -1) {
				done = true;
				break;
			}
		} while (isWhitespace(c));
		return c;
	}

	/**
	 * Ignore at most n spaces of whitespace
	 * @return the first non-whitespace character
	 * @throws IOException
	 */
	private int ignoreAtMostWhitespace() throws IOException {
		int c = 0;
		do {
			c = read();
			if (c == -1) {
				throw new IOException("Unexpected EOF reading triple quoted string on line on line " + line + ":" + col);
			}
			if (c == '\n') {
				return c;
			}
			if (!isWhitespace(c)) {
				return c;
			}
		} while (col < linePos);
		return c;
	}

	/**
	 * The next character must be the start of a comment
	 * @return the character we got
	 */
	private int expectStartOfComment() throws IOException {
		int c = read();
		ensureStartOfComment(c);
		return c;
	}

	/**
	 * The next character must be the start of an identifier
	 * @return the character we got
	 */
	private int expectStartOfIdentifier() throws IOException {
		int c = read();
		ensureStartOfIdentifier(c);
		return c;
	}

	/**
	 * The incoming character must be the start of a comment
	 */
	private void ensureStartOfComment(int c) throws IOException {
		if (c == '/' || c == '*') {
			return;
		} else if (c == -1) {
			throw new EOFException("Expected comment but got EOF on line " + line + ":" + col);
		} else {
			throw new IOException("Expected start of comment; got " + (char) c + " (0x" + Integer.toHexString(c) + ") on line " + line + ":" + col);
		}
	}

	/**
	 * The incoming character must be the start of a identifier
	 */
	private void ensureStartOfIdentifier(int c) throws IOException {
		//@formatter:off
		if 	(
				(c >= 'a' && c <= 'z')
			|| 	(c >= 'A' && c <= 'Z')
			||	c == '_'
			)
		//@formatter:on
		{
			return;
		}
		if (c == -1) {
			throw new EOFException("Expected start of identifier but got EOF on line " + line + ":" + col);
		}
		throw new IOException("Expected start of identifier; got " + (char) c + " (0x" + Integer.toHexString(c) + ") on line " + line + ":" + col);
	}

	/**
	 * The incoming character must be the rest of an identifier
	 */
	private void ensureRestOfIdentifier(int c) throws IOException {
		//@formatter:off
		if 	(
				(c >= 'a' && c <= 'z')
			|| 	(c >= 'A' && c <= 'Z')
			|| 	(c >= '0' && c <= '9')
			||	c == '_'
			)
		//@formatter:on
		{
			return;
		}
		if (c == -1) {
			throw new EOFException("Expected rest of identifier but got EOF on line " + line + ":" + col);
		}
		throw new IOException("Expected rest of identifier; got " + (char) c + " (0x" + Integer.toHexString(c) + ") on line " + line + ":" + col);
	}

	/**
	 * The incoming character must be the rest of a quoted identifier
	 */
	private void ensureRestOfQuotedIdentifier(int c) throws IOException {
		//@formatter:off
		if 	(
				(c >= 'a' && c <= 'z')
			|| 	(c >= 'A' && c <= 'Z')
			|| 	(c >= '0' && c <= '9')
			||	c == '_'
			||	c == ' '
			)
		//@formatter:on
		{
			return;
		}
		if (c == -1) {
			throw new EOFException("Expected rest of identifier but got EOF on line " + line + ":" + col);
		}
		throw new IOException("Expected rest of identifier; got " + (char) c + " (0x" + Integer.toHexString(c) + ") on line " + line + ":" + col);
	}

	private void ignoreToEndOfLine() {
		int c = 0;
		do {
			c = read();
			if (c == -1) {
				done = true;
				return;
			}
		} while (c != '\n');
	}

	/**
	 * Read up until an end of C-style comment
	 * @throws IOException
	 */
	private void readToEndOfComment() throws IOException {
		boolean gotStar = false;
		while (!done) {
			int c = read();
			if (c == -1) {
				throw new EOFException("Unexpected EOF when reading comment");
			}
			if (gotStar) {
				if (c == '/') {
					// Comment ends
					return;
				} else {
					// Reset and look for a star
					gotStar = c == '*';
				}
			} else {
				gotStar = c == '*';
			}
		}
	}

	private String getCurrentString() {
		return new String(in, currentStart, currentEnd - currentStart, StandardCharsets.UTF_8);
	}

	private void readQuotedIdentifier() throws IOException {
		currentStart = pos;
		int c = expectStartOfIdentifier();
		currentEnd = pos;
		// Now read rest of identifier, ending on quotes
		for (;;) {
			c = read();
			if (c == -1) {
				throw new EOFException("EOF in middle of quoted identifier on line " + line + ":" + col);
			}
			if (c == '"') {
				// Got it. Next character must be :
				c = read();
				if (c != ':') {
					throw new IOException("Expected : after quoted identifer " + getCurrentString() + " on line " + line + ":" + col);
				}
				return;
			}
			ensureRestOfQuotedIdentifier(c);
			currentEnd++;
		}

	}

	private void readIdentifier(int c) throws IOException {
		currentStart = pos - 1;
		currentEnd = pos;
		// Now read rest of identifier, ending on quotes
		for (;;) {
			c = read();
			if (c == -1) {
				throw new EOFException("EOF in middle of identifier on line " + line + ":" + col);
			}
			if (c == ':') {
				// Got it.
				return;
			}
			ensureRestOfIdentifier(c);
			currentEnd++;
		}
	}

	private int readTripleQuotedString(int c, boolean asProperty) throws IOException {
		int quoteCount = 0;
		boolean escape = false;
		if (baos == null) {
			baos = new ByteArrayOutputStream(1024);
		}
		// Read lines
		for (;;) {
			if (c == -1) {
				throw new EOFException("Unexpected EOF reading triple quoted string on line " + line + ":" + col);
			}
			if (escape) {
				// Next character is escaped
				switch (c) {
					case '\\':
						baos.write('\\');
						break;
					case '\'':
						baos.write('\'');
						break;
					case 't':
						baos.write('\t');
						break;
					case 'n':
						baos.write('\n');
						break;
					case 'r':
						baos.write('\r');
						break;
					case 'b':
						baos.write('\b');
						break;
					case 'f':
						baos.write('\f');
						break;
					case 'u':
						// Unicode escape sequence
						writeUTF8fromUCS2(readUnicodeEscape());
						break;
					default:
						throw new IOException("Unrecognised escape \\" + (char) c + " at line " + line + ":" + col);
				}
				escape = false;
			} else {
				if (c == '\\') {
					// Escape next character
					escape = true;
				} else {
					if (c == '\'') {
						quoteCount++;
						if (quoteCount == 3) {
							// Done
							break;
						}
					} else {
						for (int i = 0; i < quoteCount; i++) {
							baos.write('\'');
						}
						quoteCount = 0;
						baos.write(c);
						if (c == '\n') {
							c = ignoreAtMostWhitespace();
							continue;
						}
					}
				}
			}
			c = read();
		}
		byte[] buf = baos.toByteArray();
		baos.reset();
		// Strip last newline
		int length = buf.length;
		if (buf[length - 1] == '\n') {
			length--;
		}

		if (asProperty) {
			listener.property(in, currentStart, currentEnd - currentStart, buf, STRING, 0, length);
		} else {
			listener.value(buf, STRING, 0, length);
		}

		// Ignore whitespace
		c = ignoreWhitespace();

		// Ignore comma
		if (c == ',') {
			c = ignoreWhitespace();
		}

		return c;
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

	/**
	 * The incoming character is in UCS2 (16-bit) format; we will write it out to the temporarary baos buffer as a UTF8 sequence
	 * @param c
	 */
	private void writeUTF8fromUCS2(int c) {
		if (c < 0x80) {
			baos.write(c);
		} else if (c < 0x800) {
			int byte1 = 0x80 | (c & 0x3F);
			c >>= 6;
			int byte0 = 0xC0 | c;
			baos.write(byte0);
			baos.write(byte1);
		} else {
			int byte2 = 0x80 | (c & 0x3F);
			c >>= 6;
			int byte1 = 0x80 | (c & 0x3F);
			c >>= 6;
			int byte0 = 0xE0 | c;
			baos.write(byte0);
			baos.write(byte1);
			baos.write(byte2);
		}
	}

	/**
	 * Read up to the next quote. If we encounter a newline, that's an error. Quoted values are a bit of a pain as they require us to handle escapes - but if we
	 * process an escape, then the string can't be just a view of the original source bytes. So we will have to create a new source array of bytes just to hold
	 * it. As an optimisation we'll only bother if and only if we encounter an escape.
	 */
	private int readQuotedValue(boolean asProperty) throws IOException {
		int startOfValue = pos, endOfValue = pos;
		boolean escape = false;
		ByteArrayOutputStream scratch = null;
		for (;;) {
			int c = read();
			if (c == -1) {
				throw new EOFException("EOF in middle of quoted value on line " + line + ":" + col);
			}
			if (c == '\n' || c == '\r') {
				throw new IOException("Unexpected CR or LF in quoted string on line " + line + ":" + col);
			}
			if (escape) {
				// Next character is escaped and is accepted verbatim. We know we must be using transformed storage
				assert scratch != null;
				switch (c) {
					case 'n':
						scratch.write('\n');
						break;
					case 'r':
						scratch.write('\r');
						break;
					case 't':
						scratch.write('\t');
						break;
					case 'b':
						scratch.write('\b');
						break;
					case 'f':
						scratch.write('\f');
						break;
					case '\\':
						scratch.write('\\');
						break;
					case '"':
						scratch.write('"');
						break;
					case 'u':
						// Unicode escape sequence
						writeUTF8fromUCS2(readUnicodeEscape());
						break;
					default:
						throw new IOException("Unrecognised escape \\" + (char) c + " at line " + line + ":" + col);
				}
				escape = false;
			} else {
				if (c == '\\') {
					// Escape next character. We need to be using special transformed storage now.
					if (scratch == null) {
						if (baos == null) {
							baos = new ByteArrayOutputStream(1024);
						}
						scratch = baos;

						// Copy what we've got so far
						baos.write(in, startOfValue, endOfValue - startOfValue);
					}
					escape = true;
				} else if (c == '"') {
					// Got the end
					if (scratch == null) {
						// No escapes were used so we can just do a view of the original byte source
						if (asProperty) {
							listener.property(in, currentStart, currentEnd - currentStart, in, STRING, startOfValue, endOfValue - startOfValue);
						} else {
							listener.value(in, STRING, startOfValue, endOfValue - startOfValue);
						}
					} else {
						byte[] buf = scratch.toByteArray();
						scratch.reset();
						if (asProperty) {
							listener.property(in, currentStart, currentEnd - currentStart, buf, STRING, 0, buf.length);
						} else {
							listener.value(buf, STRING, 0, buf.length);
						}
					}

					// Ignore whitespace
					c = ignoreWhitespace();

					// Ignore comma
					if (c == ',') {
						c = ignoreWhitespace();
					}

					return c;
				} else if (scratch != null) {
					scratch.write(c);
				}
			}
			endOfValue++;
		}

	}

	private void emitValue(int startOfValue, int endOfValue) {
		listener.value(in, determinePrimitiveType(in, startOfValue, endOfValue), startOfValue, endOfValue - startOfValue);
	}

	private void emitProperty(int startOfValue, int endOfValue) {
		listener.property(in, currentStart, currentEnd - currentStart, in, determinePrimitiveType(in, startOfValue, endOfValue), startOfValue, endOfValue - startOfValue);
	}

	private int readList(int tagStart, int tagLength) throws IOException {
		if (tagLength > 0) {
			// A typed list
			listener.beginList(in, currentStart, currentEnd - currentStart, tagStart, tagLength);
		} else {
			// An untyped array
			listener.beginArray(in, currentStart, currentEnd - currentStart);
		}
		listDepth++;
		int c = ignoreWhitespace();
		for (;;) {
			if (c == ']') {
				// Got it
				listDepth--;
				if (tagLength > 0) {
					listener.endList();
				} else {
					listener.endArray();
				}
				break;
			}

			c = readValue(c, false);
		}

		// Ignore whitespace
		c = ignoreWhitespace();

		// Ignore comma
		if (c == ',') {
			c = ignoreWhitespace();
		}

		return c;
	}

	private int readValue(int c, boolean asProperty) throws IOException {
		int quoteCount = 0;
		int startOfValue = pos - 1, endOfValue = pos - 1;
		boolean started = false;
		boolean doEndArray = false, doEndObject = false;
		loop: for (;;) {
			if (c == -1) {
				throw new EOFException("EOF in middle of value on line " + line + ":" + col);
			}
			if (!started) {
				// Waiting for first useful character
				if (c == '\'') {
					if (quoteCount == 0) {
						linePos = col;
					}
					quoteCount++;
					if (quoteCount == 3) {
						// Begin triple quoted string
						return readTripleQuotedString(ignoreWhitespace(), asProperty);
					}
				} else if (quoteCount > 0) {
					started = true;
					endOfValue = startOfValue + quoteCount;
					quoteCount = 0;
					if (c == '\n' || c == '\r') {
						break loop;
					}
				} else {
					if (c == '{') {
						// Read an untagged object
						return read(c, true, false, 0, 0);
					}
					if (c == '}') {
						// End an object
						if (objectDepth == 0) {
							throw new IOException("Unexpected } reading value on line " + line + ":" + col);
						}
						doEndObject = true;
						break loop;
					}
					if (c == '[') {
						// Read an array
						return readList(0, 0);
					}
					if (c == ']') {
						// End an array
						if (listDepth == 0) {
							throw new IOException("Unexpected ] reading value on line " + line + ":" + col);
						}
						doEndArray = true;
						break loop;
					}
					if (c == '"') {
						// Read a quoted value
						return readQuotedValue(asProperty);
					}
					if (c == '\n' || c == '\r') {
						throw new IOException("Unexpected CR or LF before value on line " + line + ":" + col);
					}
					started = true;
					endOfValue++;
				}
			} else {
				// Read until comment, or newline, or {
				switch (c) {
					case '/':
						// If next char is / or *, it's a comment
						c = read();
						if (c == -1) {
							throw new EOFException("EOF in middle of value for on line " + line + ":" + col);
						}
						if (c == '/') {
							// Double-slash
							ignoreToEndOfLine();
							break loop;
						} else if (c == '*') {
							// C-style comment, ignore till we get */
							readToEndOfComment();
							break loop;
						} else {
							// Ok, it was just a plain old slash
							endOfValue++;
						}
						break;
					case ']':
						// End an array
						if (listDepth == 0) {
							throw new IOException("Unexpected ] reading value on line " + line + ":" + col);
						}
						doEndArray = true;
						break loop;

					case '#':
						// Ignore to end of line
						ignoreToEndOfLine();
						break loop;
					case '\n':
					case '\r':
						break loop;
					case ',':
						// Special case... comma only ends value if the value is a valid number or boolean literal so far
						if (determinePrimitiveType(in, startOfValue, endOfValue) != STRING) {
							break loop;
						}
						endOfValue++;
						break;
					case '{':
						// Begin a tagged object. Trim whitespace from the end of the value
						endOfValue = trimWhitespace(endOfValue);
						return read(c, true, true, startOfValue, endOfValue - startOfValue);

					case '[':
						// Read a tagged array. Trim whitespace from the end of the value
						endOfValue = trimWhitespace(endOfValue);
						return readList(startOfValue, endOfValue - startOfValue);

					default:
						endOfValue++;
				}
			}

			c = read();
		}
		// Ok, now, scan back from the terminal point to the first non-whitespace and non-comma character
		endOfValue = trimWhitespaceAndComma(endOfValue);
		if (asProperty) {
			emitProperty(startOfValue, endOfValue);
		} else {
			emitValue(startOfValue, endOfValue);
		}
		if (doEndArray) {
			return ']';
		}
		if (doEndObject) {
			return '}';
		}
		return ignoreWhitespace();
	}

	private int trimWhitespace(int from) {
		for (;;) {
			int cc = in[from - 1];
			if (isWhitespace(cc)) {
				from--;
			} else {
				return from;
			}
		}
	}

	private int trimWhitespaceAndComma(int from) {
		for (;;) {
			int cc = in[from - 1];
			if (cc == ',') {
				from--;
			} else if (isWhitespace(cc)) {
				from--;
			} else {
				return from;
			}
		}
	}

	private int read(int c, boolean inValue, boolean isTagged, int tagStart, int tagLength) throws IOException {
		while (!done) {
			// We can be either: a comment (//, #, or /*), the start of an object {, or the start of a key (alphabetical)
			switch (c) {
				case -1:
					// EOF
					done = true;
					return -1;
				case '/':
					// Next char must be / or *
					c = expectStartOfComment();
					if (c == '/') {
						// Double-slash
						ignoreToEndOfLine();
						c = ignoreWhitespace();
						continue;
					} else {
						// C-style comment, ignore till we get */
						readToEndOfComment();
						c = ignoreWhitespace();
						continue;
					}
				case '#':
					// Ignore to end of line
					ignoreToEndOfLine();
					c = ignoreWhitespace();
					break;
				case '{':
					bracketDepth++;
					if (inValue) {
						objectDepth++;
						if (isTagged) {
							// No class tag
							listener.beginObject(in, currentStart, currentEnd - currentStart, tagStart, tagLength);
						} else {
							listener.beginMap(in, currentStart, currentEnd - currentStart);
						}
						c = ignoreWhitespace();
						c = read(c, false, false, 0, 0);
					} else if (objectDepth > 0) {
						throw new IOException("Unexpected { on line " + line + ":" + col);
					} else {
						c = ignoreWhitespace();
					}
					break;
				case '}':
					// End an object.
					if (bracketDepth == 0) {
						throw new IOException("Unexpected } on line " + line + ":" + col);
					}
					bracketDepth--;
					if (objectDepth > 0) {
						objectDepth--;
						if (isTagged) {
							listener.endObject();
						} else {
							listener.endMap();
						}
					}
					c = ignoreWhitespace();
					if (c == ',') {
						c = ignoreWhitespace();
					}
					if (inValue) {
						return c;
					}
					break;
				case '"':
					// Read a quoted identifier; everything up to ":
					readQuotedIdentifier();
					c = ignoreWhitespace();
					c = readValue(c, true);
					break;
				default:
					// Read an identifier. First expect a legal identifier start; then everything up to :
					ensureStartOfIdentifier(c);
					readIdentifier(c);
					c = ignoreWhitespace();
					c = readValue(c, true);
					break;
			}
		}
		return -1;
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
			// 4 space tabs!!
			col = (col + 4) & ~3;
		} else {
			col++;
		}
		return c;
	}

}
