package net.puppygames.thjson;

import static java.lang.Integer.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * For writing prettily formatted THJSON data to a String.
 */
public class SimpleTHJSONWriter extends StringWriter implements THJSONWriter {

	public static void main(String[] args) throws IOException, IllegalArgumentException, IllegalAccessException {
		@SuppressWarnings("resource")
		SimpleTHJSONWriter writer = new SimpleTHJSONWriter();

		JsonObject json = THJSONReader.convertToJSON("test2.thjson");
		JSONtoTHJSONConverter jsonConverter = new JSONtoTHJSONConverter(writer);
		writer.begin();
		jsonConverter.write(json);
		writer.end();
		System.out.println(writer.toString());

		Map<String, Object> obj2 = THJSONReader.convertToMap("test2.thjson");
		MapToTHJSONConverter mapConverter = new MapToTHJSONConverter(writer);

		writer.setCompact(true);
		writer.setRootBraces(true);
		writer.begin();
		mapConverter.write(obj2);
		writer.end();

		@SuppressWarnings("unused")
		class TestPOJO {

			int a = 1, b = 2, c = 3;
			float d = 1.0f;
			String e = "a string test";
			int[] f = {1, 2, 3, 4};
			float[] g = {1.0f, 2.0f, 3.0f};
			String[] h = {"testing", "a", "string", "array"};
			List<String> i = Arrays.asList("testing", "a", "string with spaces", "list\tescapes");
			Map<Integer, String> j = new HashMap<>();
			{
				j.put(1, "one");
				j.put(2, "two");
				j.put(3, "three");
			}
			List<Object> k = new ArrayList<>();
			Object l = null;
		}

		POJOtoTHJSONConverter pojoConverter = new POJOtoTHJSONConverter(writer);
		writer.setCompact(false);
		writer.setRootBraces(false);
		writer.begin();
		TestPOJO testPOJO = new TestPOJO();
		pojoConverter.write(testPOJO);
		writer.end();

		writer.close();
		System.out.println(writer.toString());
	}

	/** Use tabs or spaces */
	private boolean useTabs = false;

	/** Tab size */
	private int tabSize = 4;

	/** Root braces */
	private boolean rootBraces;

	/** Output #thjson header */
	private boolean outputHeader = true;

	/** Compact mode */
	private boolean compact;

	/** Current indent level */
	private int level;

	/** Last thing we output was a property */
	private boolean lastWasProperty;

	/** Need a comma? */
	private boolean needComma;

	/**
	 * C'tor
	 */
	public SimpleTHJSONWriter() {
	}

	/**
	 * @param useTabs
	 */
	public void setUseTabs(boolean useTabs) {
		this.useTabs = useTabs;
	}

	/**
	 * @param tabSize
	 */
	public void setTabSize(int tabSize) {
		this.tabSize = tabSize;
	}

	/**
	 * @param rootBraces
	 */
	public void setRootBraces(boolean rootBraces) {
		this.rootBraces = rootBraces;
	}

	/**
	 * @param outputHeader
	 */
	public void setOutputHeader(boolean outputHeader) {
		this.outputHeader = outputHeader;
	}

	/**
	 * @param compact
	 */
	public void setCompact(boolean compact) {
		this.compact = compact;
	}

	/* --- */

	@Override
	public void begin() {
		if (outputHeader && !compact) {
			write("#thjson\n");
		}
		if (rootBraces) {
			write('{');
			if (!compact) {
				write('\n');
			}
			level++;
		}
		needComma = false;
	}

	private void gapBeforeProperty() {
		if (compact || lastWasProperty) {
			return;
		}
		if (level == 0 || (rootBraces && level == 1)) {
			write('\n');
		}
	}

	private void gapBeforeObect() {
		if (compact) {
			return;
		}
		if (level == 0 || (rootBraces && level == 1)) {
			write('\n');
		}
	}

	private void indent() {
		if (compact) {
			if (needComma) {
				write(", ");
				needComma = false;
			}
			return;
		}
		if (useTabs) {
			for (int i = 0; i < level; i++) {
				write('\t');
			}
		} else {
			for (int i = 0; i < level * tabSize; i++) {
				write(' ');
			}
		}
	}

	private enum StringClassification {
		SIMPLE, QUOTED, MULTILINE
	}

	private static StringClassification classifyValue(String s) {
		// If the string has any whitespace in it, or contains quotes or escapes, it must be quoted
		boolean needsQuotes = false;
		boolean hadChar = false;
		int newlines = 0;
		int maxLineLength = 0, lineLength = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!hadChar && (THJSONTokenizer.isWhitespace(c) || c == '"')) {
				needsQuotes = true;
			}
			if (c == '\t' || c == '\f' || c == '\r' || c == '\b') {
				needsQuotes = true;
			} else if (c == '\n') {
				needsQuotes = true;
				newlines++;
				maxLineLength = Math.max(maxLineLength, lineLength);
				lineLength = 0;
			} else {
				lineLength++;
				hadChar = true;
			}
		}
		if (!needsQuotes) {
			return StringClassification.SIMPLE;
		}
		//@formatter:off
		if 	(
				(newlines > 1 && maxLineLength > 10)
			||	(newlines > 4 && s.length() > 80)
			)
		//@formatter:on
		{
			return StringClassification.MULTILINE;
		} else {
			return StringClassification.QUOTED;
		}
	}

	private static StringClassification classifyKey(String s) {
		// If the string has any whitespace in it, or contains quotes, it must be quoted
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (THJSONTokenizer.isWhitespace(c) || c == '"') {
				return StringClassification.QUOTED;
			}
		}
		return StringClassification.SIMPLE;
	}

	private void writeTripleQuotedString(String s) {
		level++;
		write("\n");
		indent();
		write("'''\n");
		indent();

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\n') {
				write('\n');
				indent();
			} else {
				write(c);
			}
		}

		write("\n");
		indent();
		write("'''");
		level--;
	}

	private void writeQuotedString(String s) {
		write('\"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (THJSONTokenizer.isWhitespace(c)) {
				switch (c) {
					case ' ':
						write(' ');
						break;
					case '\n':
						write("\\n");
						break;
					case '\t':
						write("\\t");
						break;
				}
			} else if (c == '"') {
				write("\\\"");
			} else {
				writeUcs2(c);
			}
		}
		write('\"');
	}

	private void writeUcs2(char c) {
		if (c < 0xFF) {
			// Plain UTF8
			write(c);
		} else {
			// Needs escape
			write("\\u");
			writeHex((c >> 8) & 0xFF);
			writeHex(c & 0xFF);
		}
	}

	private void writeHex(int v) {
		if (v < 0x10) {
			write('0');
		}
		write(toHexString(v).toUpperCase());
	}

	private void outputKey(String key) {
		indent();
		StringClassification sc = classifyKey(key);
		if (sc == StringClassification.SIMPLE) {
			write(key);
		} else {
			write('"');
			writeQuotedString(key);
			write('"');
		}
		if (compact) {
			write(':');
		} else {
			write(": ");
		}
	}

	@Override
	public void beginMap(String key) {
		gapBeforeObect();
		outputKey(key);
		write("{");
		if (!compact) {
			level++;
			write("\n");
		}
		lastWasProperty = false;
		needComma = false;
	}

	@Override
	public void endMap() {
		if (!compact) {
			level--;
			indent();
			write("}\n");
		} else {
			write("}");
		}
		lastWasProperty = false;
		needComma = true;
	}

	@Override
	public void beginObject(String key, String clazz) {
		gapBeforeObect();
		outputKey(key);
		StringClassification sc = classifyValue(clazz);
		switch (sc) {
			case SIMPLE:
				write(clazz);
				break;
			case QUOTED:
			case MULTILINE:
				writeQuotedString(clazz);
				break;
			default:
				break;
		}
		write(" {");
		if (!compact) {
			level++;
			write("\n");
		}
		lastWasProperty = false;
		needComma = false;
	}

	@Override
	public void endObject() {
		endMap();
	}

	@Override
	public void beginArray(String key) {
		gapBeforeObect();
		outputKey(key);
		write("[");
		if (!compact) {
			level++;
			write("\n");
		}
		lastWasProperty = false;
		needComma = false;
	}

	@Override
	public void endArray() {
		if (!compact) {
			level--;
			indent();
			write("]\n");
		} else {
			write(']');
		}
		lastWasProperty = false;
		needComma = true;
	}

	@Override
	public void beginList(String key, String clazz) {
		gapBeforeObect();
		outputKey(key);
		StringClassification sc = classifyValue(clazz);
		switch (sc) {
			case SIMPLE:
				write(clazz);
				break;
			case QUOTED:
			case MULTILINE:
				writeQuotedString(clazz);
				break;
			default:
				break;
		}
		write(" [");
		if (!compact) {
			level++;
			write("\n");
		}
		lastWasProperty = false;
	}

	@Override
	public void endList() {
		endArray();
	}

	@Override
	public void value(boolean b) {
		indent();
		if (compact) {
			write(b ? "true" : "false");
		} else {
			write(b ? "true\n" : "false\n");
		}
		needComma = true;
	}

	@Override
	public void value(int i) {
		indent();
		write(Integer.toString(i));
		if (!compact) {
			write('\n');
		}
		needComma = true;
	}

	@Override
	public void value(float f) {
		indent();
		write(Float.toString(f));
		if (!compact) {
			write('\n');
		}
		needComma = true;
	}

	@Override
	public void value(String s) {
		indent();
		if (compact) {
			writeQuotedString(s);
		} else {
			StringClassification sc = classifyValue(s);
			switch (sc) {
				case SIMPLE:
					write(s);
					break;
				case QUOTED:
					writeQuotedString(s);
					break;
				case MULTILINE:
					writeTripleQuotedString(s);
					break;
				default:
					break;
			}
			write('\n');
		}
		needComma = true;
	}

	@Override
	public void valueNull() {
		indent();
		if (compact) {
			write("null");
		} else {
			write("null\n");
		}
		needComma = true;
	}

	@Override
	public void property(String key, boolean b) {
		gapBeforeProperty();
		outputKey(key);
		if (compact) {
			write(b ? "true," : "false");
		} else {
			write(b ? "true\n" : "false\n");
		}
		lastWasProperty = true;
		needComma = true;
	}

	@Override
	public void property(String key, int i) {
		gapBeforeProperty();
		outputKey(key);
		write(Integer.toString(i));
		if (!compact) {
			write('\n');
		}
		lastWasProperty = true;
		needComma = true;
	}

	@Override
	public void property(String key, float f) {
		gapBeforeProperty();
		outputKey(key);
		write(Float.toString(f));
		if (!compact) {
			write('\n');
		}
		lastWasProperty = true;
		needComma = true;
	}

	@Override
	public void property(String key, String s) {
		gapBeforeProperty();
		outputKey(key);
		if (compact) {
			writeQuotedString(s);
		} else {
			StringClassification sc = classifyValue(s);
			switch (sc) {
				case SIMPLE:
					write(s);
					break;
				case QUOTED:
					writeQuotedString(s);
					break;
				case MULTILINE:
					writeTripleQuotedString(s);
					break;
			}
			write('\n');
		}
		lastWasProperty = true;
		needComma = true;
	}

	@Override
	public void propertyNull(String key) {
		gapBeforeProperty();
		outputKey(key);
		if (compact) {
			write("null");
		} else {
			write("null\n");
		}
		lastWasProperty = true;
		needComma = true;
	}

	@Override
	public void end() {
		if (rootBraces) {
			write("}");
			level--;
			if (compact) {
				write(' ');
			} else {
				write('\n');
			}
		}
	}
}