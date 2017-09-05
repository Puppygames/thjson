package net.puppygames.thjson;

import static java.nio.charset.StandardCharsets.*;

import java.util.Base64;

/**
 * A THJSON Token, which is read from a {@link THJSONTokenizer}
 */
public class Token {

	/** Convenient EOF token */
	public static final Token EOF = new Token(TokenType.EOF);

	/** Convenient NULL token */
	public static final Token NULL = new Token(TokenType.NULL);

	/** Convenient TRUE token */
	public static final Token TRUE = new Token(true);

	/** Convenient FALSE token */
	public static final Token FALSE = new Token(false);

	public static final Token COLON = new Token(':');
	public static final Token COMMA = new Token(',');
	public static final Token OPEN_SQUARE_BRACKET = new Token('[');
	public static final Token OPEN_CURLY_BRACKET = new Token('{');
	public static final Token CLOSE_SQUARE_BRACKET = new Token(']');
	public static final Token CLOSE_CURLY_BRACKET = new Token('}');

	/* --- */

	private final TokenType type;

	private String string;
	private byte[] bytes;
	private boolean bool;
	private int integer;
	private float number;
	private char delimiter;

	private Token(TokenType type) {
		this.type = type;
	}

	private Token(boolean bool) {
		this.type = TokenType.BOOLEAN;
		this.bool = bool;
	}

	private Token(char delimiter) {
		this.type = TokenType.DELIMITER;
		this.delimiter = delimiter;
	}

	/* --- */

	public Token(float number) {
		this.type = TokenType.FLOAT;
		this.number = number;
	}

	public Token(String string, TokenType type) {
		this.string = string;
		this.type = type;
	}

	public Token(byte[] bytes, TokenType type) {
		this.bytes = bytes;
		this.type = type;
	}

	public Token(int integer, TokenType type) {
		this.integer = integer;
		this.type = type;
	}

	/* --- */

	public TokenType getType() {
		return type;
	}

	public String getString() {
		return string;
	}

	public byte[] getBytes() {
		return bytes;
	}

	public boolean getBoolean() {
		return bool;
	}

	public int getInteger() {
		return integer;
	}

	public float getFloat() {
		return number;
	}

	public char getDelimiter() {
		return delimiter;
	}

	@Override
	public String toString() {
		return type + ":" + getValue() + "<";
	}

	public String getValue() {
		switch (type) {
			case BINARY:
				return "%" + Integer.toBinaryString(integer);
			case BLOCK_COMMENT:
				return "/" + string + "/";
			case BOOLEAN:
				return String.valueOf(bool);
			case DELIMITER:
				return String.valueOf(delimiter);
			case DIRECTIVE:
				return "@" + string;
			case EOF:
				return "EOF";
			case FLOAT:
				return Float.toString(number);
			case HASH_COMMENT:
				return "#" + string;
			case HEX:
				return "0x" + Integer.toHexString(integer);
			case INTEGER:
				return Integer.toString(integer);
			case MULTILINE_STRING:
				return "'''" + string + "'''";
			case MULTILINE_BYTES:
				return "===" + new String(Base64.getEncoder().encode(bytes), UTF_8) + "===";
			case NULL:
				return "null";
			case SIGNED:
				return "+" + Integer.toString(integer);
			case SLASHSLASH_COMMENT:
				return "//" + string;
			case STRING:
				return string;
			case BYTES:
				return "`" + new String(Base64.getEncoder().encode(bytes), UTF_8) + "`";
			default:
				assert false : type;
				return "";
		}
	}

}
