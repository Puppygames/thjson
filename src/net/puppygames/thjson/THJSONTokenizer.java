package net.puppygames.thjson;

import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static net.puppygames.thjson.TokenType.BINARY;
import static net.puppygames.thjson.TokenType.BOOLEAN;
import static net.puppygames.thjson.TokenType.FLOAT;
import static net.puppygames.thjson.TokenType.HEX;
import static net.puppygames.thjson.TokenType.INTEGER;
import static net.puppygames.thjson.TokenType.NULL;
import static net.puppygames.thjson.TokenType.SIGNED;
import static net.puppygames.thjson.TokenType.STRING;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Takes THJSON-formatted input from an {@link InputStream} and turns it into tokens which can be listened for.
 */
public class THJSONTokenizer {

	private static final boolean ALLOW_DELIMETERS_IN_QUOTELESS_STRINGS = false; // Set to true for HJSON behaviour

	/** Initial readahead size... should be big enough */
	private static final int READAHEAD_SIZE = 3;

	/**
	 * Determines if the incoming character is legal THJSON whitespace (space, tab, newline)
	 * @param c
	 * @return true if c is a space, tab, newline, backspace, formfeed, or carriage return
	 */
	public static boolean isWhitespace(int c) {
		return c == ' ' || c == '\t' || c == '\n';
	}

	/**
	 * Determines if the incoming character requires quotes if it is found in a string
	 * @param c
	 * @return true if c is whitespace or a token delimiter
	 */
	public static boolean requiresQuotes(int c) {
		return " \t\n{}[],:#\\\"".indexOf(c) != -1;
	}

	private static boolean isBinaryDigit(char c) {
		return c == '0' || c == '1';
	}

	private static boolean isDecimalDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isHexDigit(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	private static TokenType checkHexLiteral(String src) {
		for (int i = 2; i < src.length(); i++) {
			if (!isHexDigit(src.charAt(i))) {
				// Not actually a hex literal after all - so it's a string
				return STRING;
			}
		}
		return HEX;
	}

	private static TokenType checkBinaryLiteral(String src) {
		for (int i = 1; i < src.length(); i++) {
			if (!isBinaryDigit(src.charAt(i))) {
				// Not actually a binary literal after all - so it's a string
				return STRING;
			}
		}
		return BINARY;
	}

	private static TokenType checkNumberLiteral(String src) {
		// Skip initial sign if present
		int start = 0;
		boolean signed = false;
		if (src.charAt(0) == '+') {
			start++;
			signed = true;
		} else if (src.charAt(0) == '-') {
			start++;
		}

		// Otherwise, first char must be a digit or a dot
		if (src.length() <= start || (src.charAt(start) != '.' && !isDecimalDigit(src.charAt(start)))) {
			// Nope, so 'tis a string
			return STRING;
		}

		// These are floats: 1.0, .0, 1e1, 1.1e1, 1.1e+1, 1.1e-1
		int esignIndex = -1;
		int eIndex = -1;
		int dotIndex = -1;
		int digitsAfterE = 0;
		int end = src.length();
		for (int i = start; i < end; i++) {
			char c = src.charAt(i);
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
			return signed ? SIGNED : INTEGER;
		}
	}

	/**
	 * Determines the primitive type of the incoming string. This is a subset of the {@link TokenType} set of enums
	 * @param src
	 * @param start
	 * @param end
	 * @return the {@link TokenType}; never null. Will be one of STRING, INTEGER, BOOLEAN, FLOAT, or NULL.
	 */
	private static TokenType determinePrimitiveType(String s) {
		if (s.equals("")) {
			return STRING;
		} else if (s.equals("null")) {
			return NULL;
		} else if (s.equals("true")) {
			return BOOLEAN;
		} else if (s.equals("false")) {
			return BOOLEAN;
		} else if (s.startsWith("0x")) {
			// Unsigned hex integer
			return checkHexLiteral(s);
		} else if (s.charAt(0) == '%') {
			// Unsigned binary integer
			return checkBinaryLiteral(s);
		} else {
			// Either float or integer
			return checkNumberLiteral(s);
		}
	}

	/** Building the current token here */
	private final StringBuilder token = new StringBuilder();

	/** For unicode escapes */
	private final byte[] unicode = new byte[2];

	/** Read characters from here */
	private final THJSONInputStream in;

	/** Token queue */
	private Token[] peekQueue = new Token[READAHEAD_SIZE];
	private int[] peekQueueLine = new int[READAHEAD_SIZE];
	private int[] peekQueueCol = new int[READAHEAD_SIZE];

	/** Peek queue length */
	private int peekLength;

	/** Multiline quotes column alignment */
	private int align;

	/** Multiline quotes row */
	private int row;

	/** Current line/col */
	private int line, col;

	/** Position listener */
	private PositionListener listener;

	/** Describe the stream's identity */
	private String source;

	/**
	 * C'tor
	 * @param in Cannot be null
	 */
	public THJSONTokenizer(InputStream in) {
		this(new THJSONInputStream(requireNonNull(in, "in cannot be null")));
	}

	/**
	 * C'tor
	 * @param in Cannot be null
	 */
	public THJSONTokenizer(THJSONInputStream in) {
		this.in = requireNonNull(in, "in cannot be null");
	}

	/* --- */

	/**
	 * Sets or clears the position listener
	 * @param listener May be null
	 */
	public void setListener(PositionListener listener) {
		this.listener = listener;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getSource() {
		return source;
	}

	public void setTabSize(int tabSize) {
		in.setTabSize(tabSize);
	}

	public int getTabSize() {
		return in.getTabSize();
	}

	public int getLine() {
		return line;
	}

	public int getCol() {
		return col;
	}

	/**
	 * Peeks ahead a number of tokens
	 * @param ahead The number of tokens to read ahead; 0 is the next token that would be returned by {@link #read()}
	 * @return a {@link Token}; never null
	 * @throws IOException
	 */
	public Token peek(int ahead) throws IOException {
		if (peekQueue.length <= ahead) {
			peekQueue = Arrays.copyOf(peekQueue, ahead + 1);
			peekQueueLine = Arrays.copyOf(peekQueueLine, ahead + 1);
			peekQueueCol = Arrays.copyOf(peekQueueCol, ahead + 1);
		}
		for (int i = peekLength; i <= ahead; i++) {
			peekQueue[i] = readToken();
			peekQueueLine[i] = line;
			peekQueueCol[i] = col;
			peekLength++;
		}
		return peekQueue[ahead];
	}

	/**
	 * Reads the next token
	 * @return a {@link Token}; never null. When at the end of the stream the token return will have a TokenType of EOF.
	 * @throws IOException
	 */
	public Token read() throws IOException {
		// Consume from read-ahead queue first
		Token ret;
		if (peekLength > 0) {
			ret = peekQueue[0];
			line = peekQueueLine[0];
			col = peekQueueCol[0];
			arraycopy(peekQueue, 1, peekQueue, 0, --peekLength);
			arraycopy(peekQueueLine, 1, peekQueueLine, 0, peekLength);
			arraycopy(peekQueueCol, 1, peekQueueCol, 0, peekLength);
		} else {
			ret = readToken();
		}

		// Note the position
		if (listener != null) {
			listener.onPosition(source, line, col);
		}
		return ret;
	}

	/**
	 * Reads characters from the input stream until we have a complete token. At EOF, we return a Token with the EOF {@link TokenType}
	 * @return a {@link Token}; never null
	 * @throws IOException
	 */
	private Token readToken() throws IOException {
		// First clear
		token.setLength(0);

		// Skip all initial whitespace
		int c;
		do {
			c = in.read();
		} while (isWhitespace(c));

		// Now note position
		line = in.getLine();
		col = in.getCol();

		if (c == -1) {
			return Token.EOF;
		}

		// What we do next depends on the character we get first
		switch (c) {
			case '{':
				return Token.OPEN_CURLY_BRACKET;
			case '[':
				return Token.OPEN_SQUARE_BRACKET;
			case '(':
				return Token.OPEN_ROUND_BRACKET;
			case '}':
				return Token.CLOSE_CURLY_BRACKET;
			case ']':
				return Token.CLOSE_SQUARE_BRACKET;
			case ')':
				return Token.CLOSE_ROUND_BRACKET;
			case ':':
				return Token.COLON;
			case ',':
				return Token.COMMA;
			case '/':
				// Maybe C or C++ style comment
				if (in.peek(0) == '/') {
					in.read();
					return readSingleLineComment(TokenType.SLASHSLASH_COMMENT);
				} else if (in.peek(0) == '*') {
					return readBlockComment();
				}
				break;
			case '"':
				// Quoted string
				return readQuotedString();
			case '`':
				// Quoted bytes
				return readQuotedBytes();
			case '\'':
				// Maybe multiline string
				if (in.peek(0) == '\'' && in.peek(1) == '\'') {
					return readMultilineString();
				}
				break;
			case '<':
				// Maybe multiline bytes
				if (in.peek(0) == '<' && in.peek(1) == '<') {
					return readMultilineBytes();
				}
				break;
			case '#':
				// Directive
				if (in.peek(0) == '"') {
					in.read();
					return readQuotedDirective();
				} else {
					return readDirective();
				}
		}

		// Quoteless string starting with the character we just read
		return readQuotelessToken(c);
	}

	private Token readSingleLineComment(TokenType type) throws IOException {
		int c;
		for (;;) {
			c = in.read();
			if (c == '\n' || c == -1) {
				// Done
				return new Token(token.toString(), type);
			} else {
				token.append((char) c);
			}
		}
	}

	private Token readBlockComment() throws IOException {
		int c;
		for (;;) {
			c = in.read();
			if (c == -1) {
				throw new EOFException("Unexpected EOF reading block comment at line " + getLine() + ":" + getCol() + " in " + source);
			}
			token.append((char) c);
			if (c == '*' && in.peek(0) == '/') {
				in.read();
				return new Token(token.toString(), TokenType.BLOCK_COMMENT);
			}
		}
	}

	private Token readDirective() throws IOException {
		int c;
		for (;;) {
			c = in.peek(0);
			if (c == ',') {
				// Consume commas
				in.read();
			} else if (c == -1 || c == '\n' || c == ',' || c == ':' || c == '{' || c == '}' || c == '[' || c == ']' || c == '#' || (c == '/' && (in.peek(1) == '/' || in.peek(1) == '*'))) {
				// End the directive at a delimiter or comment
				return new Token(getToken(true, true), TokenType.DIRECTIVE);
			} else {
				token.append((char) in.read());
				if (c == '"') {
					// Start reading a string.
					readStringInDirective();
				}
			}
		}
	}

	private void readStringInDirective() throws IOException {
		int c;
		for (;;) {
			c = in.read();
			switch (c) {
				case -1:
					throw new EOFException("Unexpected EOF reading quoted string in directive at line " + getLine() + ":" + getCol() + " in " + source);
				case '\n':
					throw new EOFException("Unexpected end of line reading quoted string in directive at line " + getLine() + ":" + getCol() + " in " + source);
				case '\\':
					// Escape sequence
					readEscape();
					break;
				default:
					if (c > 0x7F) {
						c = ((c & 0b11111) << 6) | (in.read() & 0b111111);
					}
					// Simply append to string so far
					token.append((char) c);
					if (c == '"') {
						return;
					}
			}
		}
	}

	private Token readQuotedString() throws IOException {
		int c;
		for (;;) {
			c = in.read();
			switch (c) {
				case -1:
					throw new EOFException("Unexpected EOF reading quoted string at line " + getLine() + ":" + getCol() + " in " + source);
				case '\n':
					throw new EOFException("Unexpected end of line reading quoted string at line " + getLine() + ":" + getCol() + " in " + source);
				case '"':
					// End the string
					return new Token(getToken(false, false), TokenType.STRING);
				case '\\':
					// Escape sequence
					readEscape();
					break;
				default:
					if (c > 0x7F) {
						c = ((c & 0b11111) << 6) | (in.read() & 0b111111);
					}
					// Simply append to string so far
					token.append((char) c);
			}
		}
	}

	private Token readQuotedBytes() throws IOException {
		int c;
		for (;;) {
			c = in.read();
			switch (c) {
				case -1:
					throw new EOFException("Unexpected EOF reading quoted bytes at line " + getLine() + ":" + getCol() + " in " + source);
				case '\n':
					throw new EOFException("Unexpected end of line reading quoted bytes at line " + getLine() + ":" + getCol() + " in " + source);
				case '`':
					// End the bytes
					return new Token(Base64.getDecoder().decode(getToken(false, false).getBytes(UTF_8)), TokenType.BYTES);
				default:
					// Only allow valid Base64 characters: A-Z,a-z,0-9,\+
					if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=') {
						// Simply append to string so far
						token.append((char) c);
					} else {
						throw new IOException("Expected base64 character but got " + (char) c + " at line " + getLine() + ":" + getCol() + " in " + source);
					}
			}
		}
	}

	private Token readQuotedDirective() throws IOException {
		int c;
		for (;;) {
			c = in.read();
			switch (c) {
				case -1:
					throw new EOFException("Unexpected EOF reading quoted directive at line " + getLine() + ":" + getCol() + " in " + source);
				case '\n':
					throw new EOFException("Unexpected end of line reading quoted directive at line " + getLine() + ":" + getCol() + " in " + source);
				case '"':
					// End the directive
					return new Token(getToken(false, false), TokenType.DIRECTIVE);
				case '\\':
					// Escape sequence
					readEscape();
					break;
				default:
					if (c > 0x7F) {
						c = ((c & 0b11111) << 6) | (in.read() & 0b111111);
					}
					// Simply append to string so far
					token.append((char) c);
			}
		}
	}

	private Token readMultilineString() throws IOException {
		align = getCol();
		row = getLine();
		in.read();
		in.read();
		for (;;) {
			int c = in.read();
			switch (c) {
				case -1:
					throw new EOFException("Unexpected EOF reading multiline string at line " + getLine() + ":" + getCol() + " in " + source);
				case '\'':
					if (in.peek(0) == '\'' && in.peek(1) == '\'') {
						// Got it all. Ditch the quotes
						in.read();
						in.read();
						// Trim off the whitespace up to and including the last \n - unless there's no terminating \n at all
						for (int i = token.length(); --i >= 0;) {
							c = token.charAt(i);
							if (!isWhitespace(c)) {
								break;
							}
							if (c == '\n') {
								token.setLength(i);
								break;
							}
						}
						return new Token(token.toString(), TokenType.MULTILINE_STRING);
					}
					// Intentional fallthrough
				default:
					if (c > 0x7F) {
						c = ((c & 0b11111) << 6) | (in.read() & 0b111111);
					}
					char ch = (char) c;
					if (token.length() == 0 && isWhitespace(c) && row == getLine()) {
						// Ignore the initial whitespace on the first line if we've not got any characters yet
						continue;
					}
					if (getCol() >= align || !isWhitespace(ch)) {
						token.append(ch);
					}
			}
		}
	}

	private Token readMultilineBytes() throws IOException {
		align = getCol();
		row = getLine();
		in.read();
		in.read();
		for (;;) {
			int c = in.read();
			switch (c) {
				case -1:
					throw new EOFException("Unexpected EOF reading multiline bytes at line " + getLine() + ":" + getCol() + " in " + source);
				case '>':
					if (in.peek(0) == '>' && in.peek(1) == '>') {
						// Got it all. Ditch the angle brackets
						in.read();
						in.read();
						// Trim off the whitespace up to and including the last \n - unless there's no terminating \n at all
						for (int i = token.length(); --i >= 0;) {
							c = token.charAt(i);
							if (!isWhitespace(c)) {
								break;
							}
							if (c == '\n') {
								token.setLength(i);
								break;
							}
						}
						return new Token(Base64.getDecoder().decode(token.toString().getBytes(UTF_8)), TokenType.MULTILINE_BYTES);
					}
					// Intentional fallthrough
				default:
					char ch = (char) c;
					if (token.length() == 0 && isWhitespace(c) && row == getLine()) {
						// Ignore the initial whitespace on the first line if we've not got any characters yet
						continue;
					}
					if (getCol() >= align || !isWhitespace(ch)) {
						// Only allow valid Base64 characters: A-Z,a-z,0-9,\+
						if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=') {
							token.append(ch);
						} else if (isWhitespace(c)) {
							// If we hit whitespace, ignore it
						} else {
							throw new IOException("Expected base64 character but got " + (char) c + " at line " + getLine() + ":" + getCol() + " in " + source);
						}
					}
			}
		}
	}

	/**
	 * Read up until a delimiter, starting with a character we've already read
	 * @param c
	 * @return
	 * @throws IOException
	 */
	private Token readQuotelessToken(int c) throws IOException {
		token.append((char) c);

		boolean hadWhitespace = false;
		boolean consumeRestOfLine = false;

		for (;;) {
			c = in.peek(0);
//			if (c == -1) {
//				throw new EOFException("Unexpected EOF reading key at " + getLine() + ":" + getCol() + " in " + getSource());
//			}

			boolean append = false;
			if (c == '\n' || c == -1) {
				// That's the end
			} else if (isWhitespace(c)) {
				// In HJSON, encountering other whitespace in the middle of a quoteless token means it is going to go to the end of the line.
				// This behaviour is a bit dangerous and causes very hard to spot parsing errors, so for now it's turned off
				hadWhitespace = ALLOW_DELIMETERS_IN_QUOTELESS_STRINGS;
				append = true;
			} else if ("{}()[],:".indexOf(c) != -1) {
				if (consumeRestOfLine) {
					// We are consuming the rest of the line
					// blah blah [
					append = true;
				} else {
					// blah [
					append = false;
				}
			} else if (c == '/' && (in.peek(1) == '/' || in.peek(1) == '*')) {
				if (consumeRestOfLine) {
					// We are consuming the rest of the line
					// blah blah [
					append = true;
				} else {
					// blah [
					append = false;
				}
			} else {
				if (hadWhitespace) {
					consumeRestOfLine = true;
				}
				append = true;
			}

			if (append) {
				c = in.read();
				if (c > 0x7F) {
					c = ((c & 0b11111) << 6) | (in.read() & 0b111111);
				}
				token.append((char) c);
			} else {
				// Trim the whitespace from the end
				String value = getToken(false, true);
				// What type is it?
				TokenType type = determinePrimitiveType(value);
				// Comma is only a delimiter if what we have read so far is a literal or number or a string with no spaces
				if (c != ',' || (c == ',' && (type.isLiteral() || !hadWhitespace))) {
					if (c == ',') {
						// Consume commas
						in.read();
					}
					Token ret;
					try {
						switch (type) {
							case NULL:
								ret = Token.NULL;
								break;
							case BOOLEAN:
								ret = Boolean.parseBoolean(value) ? Token.TRUE : Token.FALSE;
								break;
							case INTEGER:
							case SIGNED:
								ret = new Token(Integer.parseInt(value), type);
								break;
							case HEX:
								ret = new Token(Integer.parseUnsignedInt(value.substring(2), 16), TokenType.HEX);
								break;
							case BINARY:
								ret = new Token(Integer.parseUnsignedInt(value.substring(1), 2), TokenType.BINARY);
								break;
							case FLOAT:
								ret = new Token(Float.parseFloat(value));
								break;
							default:
								ret = new Token(value, TokenType.STRING);
						}
					} catch (NumberFormatException e) {
						// Stringify instead
						ret = new Token(value, TokenType.STRING);
					}
					if (c == '\n') {
						ret.setEndOfLine();
					}
					return ret;
				} else if (c == ',') {
					// Append a comma after all
					token.append(',');
					in.read();
				}
			}
		}
	}

	private void readEscape() throws IOException {
		int c = in.read();
		switch (c) {
			case -1:
				throw new EOFException("Unexpected EOF reading escape at " + getLine() + ":" + getCol() + " in " + source);
			case 'u':
				// Unicode escape
				token.append(readUnicodeEscape());
				break;
			case 't':
				token.append('\t');
				break;
			case 'n':
				token.append('\n');
				break;
			case 'r':
				token.append('\r');
				break;
			default:
				token.append((char) c);
		}
	}

	/**
	 * Obtains the current token, trimmed from left and/or right of whitespace
	 */
	private String getToken(boolean trimLeft, boolean trimRight) {
		int start = 0, end = token.length();
		if (trimLeft) {
			for (start = 0; start < end; start++) {
				if (!isWhitespace(token.charAt(start))) {
					break;
				}
			}
		}

		if (trimRight) {
			while (end > start && isWhitespace(token.charAt(end - 1))) {
				end--;
			}
		}

		return token.substring(start, end);
	}

	/**
	 * Read a hex digit (case insensitive)
	 * @return 0...15
	 * @throws IOException
	 */
	private byte readHexDigit() throws IOException {
		int c = in.read();
		if (c == -1) {
			throw new EOFException("Unexpected EOF expecting hex digit at line " + getLine() + ":" + getCol() + " in " + source);
		}
		if (c >= '0' && c <= '9') {
			return (byte) (c - '0');
		}
		if (c >= 'a' && c <= 'f') {
			return (byte) ((c - 'a') + 10);
		}
		if (c >= 'A' && c <= 'F') {
			return (byte) ((c - 'A') + 10);
		}
		throw new IOException("Expected hex digit but got " + (char) c + " at line " + getLine() + ":" + getCol() + " in " + source);
	}

	/**
	 * The next four chars must be hex digits referring to a 16-bit unicode escape
	 * @return a 16-bit integer that must be encoded in UTF8
	 * @throws IOException
	 */
	private String readUnicodeEscape() throws IOException {
		unicode[0] = (byte) ((readHexDigit() << 4) | (readHexDigit()));
		unicode[1] = (byte) ((readHexDigit() << 4) | (readHexDigit()));
		return new String(unicode, StandardCharsets.UTF_16BE);
	}

}
