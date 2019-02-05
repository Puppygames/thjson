package net.puppygames.thjson;

import static java.lang.Integer.toHexString;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;

/**
 * For writing prettily formatted THJSON data to a String.
 */
public class SimpleTHJSONWriter extends StringWriter implements THJSONWriter {

	private static final boolean DEBUG = false;

	private enum StringClassification {
		SIMPLE, QUOTED, MULTILINE
	}

	private static StringClassification classifyValue(String s, boolean compact) {
		// If the string has any whitespace in it, or contains quotes or escapes, it must be quoted.
		// If it's "null", "true", or "false", it also needs quoting.
		if ("null".equals(s) || "true".equals(s) || "false".equals(s) || "".equals(s)) {
			return StringClassification.QUOTED;
		}
		boolean needsQuotes = false;
		boolean lastWasWhitespace = false;
		int newlines = 0;
		int maxLineLength = 0, lineLength = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ("\"',{}[]()<>:#\t".indexOf(c) != -1) {
				needsQuotes = true;
			} else if (c == ' ') {
				lastWasWhitespace = true;
				lineLength++;
			} else if (c == '\n') {
				needsQuotes = true;
				newlines++;
				maxLineLength = Math.max(maxLineLength, lineLength);
				lineLength = 0;
			} else {
				lastWasWhitespace = false;
				lineLength++;
			}
		}
		needsQuotes |= lastWasWhitespace;
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
			if (THJSONTokenizer.requiresQuotes(c)) {
				return StringClassification.QUOTED;
			}
			if (c == '/' && i < s.length() - 1 && s.charAt(i + 1) == '*') {
				return StringClassification.QUOTED;
			}
		}
		return StringClassification.SIMPLE;
	}

	public static void main(String[] args) throws Exception {
		try (SimpleTHJSONWriter writer = new SimpleTHJSONWriter()) {

			//			JsonObject json = THJSONReader.convertToJSON("test2.thjson");
			//			JSONtoTHJSONConverter jsonConverter = new JSONtoTHJSONConverter(writer);
			//			writer.begin();
			//			jsonConverter.write(json);
			//			writer.end();
			//			System.out.println(writer.toString());
			//
			//			Map<String, Object> obj2 = THJSONReader.convertToMap("test2.thjson");
			//			MapToTHJSONConverter mapConverter = new MapToTHJSONConverter(writer);
			//
			//			writer.setCompact(true);
			//			writer.setRootBraces(true);
			//			writer.begin();
			//			mapConverter.write(obj2);
			//			writer.end();

			class SimpleEmbedded {
				int xxx;

				SimpleEmbedded(int xxx) {
					this.xxx = xxx;
				}
			}

			class EvenMoreEmbedded {
				Map<Integer, String> map = new HashMap<>();
				{
					map.put(1, "one");
					map.put(2, "two");
					map.put(3, "three");
				}
				Map<String, Integer> emptyMap = new HashMap<>();
				int[] integers = {1, 2, 3};
				List<StringBuffer> objects = Arrays.asList(new StringBuffer("arrgh"), new StringBuffer("blarrgh"));
				float[] empty = {};
				String bigString = "\tThis is a really big string with at least\nfour newlines in it. This will cause the\nSimpleTHJSONWriter to classify the string as\na multiline string.";
			}

			class EmbeddedPOJO {
				int xyzzy = 1;
				String stringy = "Some string or other";
				EvenMoreEmbedded embedded = new EvenMoreEmbedded();
			}

			@SuppressWarnings("unused")
			class TestPOJO {

				int a = 1, bee = 2, cee = 3;
				float dee = 1.0f;
				String eeee = "a string test ending with spaces  ";
				int[] effrefa = {1, 2, 3, 4};
				float[] gee = {1.0f, 2.0f, 3.0f};
				String[] h = {"testing", "a", "string", "array"};
				List<String> i = Arrays.asList("testing", "a", "string with spaces", "string, with commas", "list\tescapes");
				Map<Integer, String> j = new HashMap<>();
				{
					j.put(1, "one");
					j.put(2, "two");
					j.put(3, "three");
				}
				List<Object> k = new ArrayList<>();
				Object l = null;
				EmbeddedPOJO m = new EmbeddedPOJO();
				//				int[][][] n = new int[2][2][2];
				//				Object[][][] o = new Object[2][1][0];
				List<Object> children = new ArrayList<>();
				{
					children.add(new SimpleEmbedded(1));
					children.add(new SimpleEmbedded(2));
					children.add(new SimpleEmbedded(3));
				}
			}

			POJOtoTHJSONConverter pojoConverter = new POJOtoTHJSONConverter(writer);
			writer.setRootBraces(false);
			writer.setRootGap(true);
			writer.setDefaultCompact(false);
			pojoConverter.setCompact(TestPOJO.class, true);
			pojoConverter.setCompact(SimpleEmbedded.class, true);
			writer.begin();
			TestPOJO testPOJO = new TestPOJO();
			pojoConverter.write(testPOJO);
			writer.end();
			System.out.println(writer.toString());
		}
	}

	/** Use tabs or spaces */
	private boolean useTabs = false;

	/** Tab size */
	private int tabSize = 4;

	/** Root braces */
	private boolean rootBraces;

	/** Output #thjson header */
	private boolean outputHeader = true;

	/** Gap between root entries */
	private boolean rootGap;

	/* --- */

	/** Current indent level */
	private int indent;

	/** Current column */
	private int col;

	/** Default compaction */
	private boolean defaultCompact;

	/** The next object's compaction status */
	private boolean nextCompact;

	/** Currently compact */
	private boolean compact;

	private enum Output {
		NEWLINE, COMMA, TEXT, WHITESPACE, OPEN, CLOSE
	}

	private Output lastOutput = Output.NEWLINE;

	private enum ObjType {
		OBJECT('{', '}'), LIST('[', ']');

		final char openBracket, closeBracket;

		private ObjType(char openBracket, char closeBracket) {
			this.openBracket = openBracket;
			this.closeBracket = closeBracket;
		}
	}

	private class ObjectOutput {
		final ObjType type;
		final boolean compact;
		final String clazz;

		/** Number of values output so far */
		int values;

		ObjectOutput(ObjType type, String clazz, boolean compact) {
			this.type = type;
			this.clazz = clazz;
			this.compact = compact;
		}
	}

	private final Stack<ObjectOutput> objects = new Stack<>();

	private String key;

	/**
	 * C'tor
	 */
	public SimpleTHJSONWriter() {
	}

	public void setRootGap(boolean rootGap) {
		this.rootGap = rootGap;
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

	public void setDefaultCompact(boolean defaultCompact) {
		this.defaultCompact = defaultCompact;
	}

	/**
	 * The <em>next</em> object or list we output will be compact
	 * @param compact
	 */
	public void setCompact(boolean compact) {
		this.nextCompact = compact;
	}

	/* --- */

	@Override
	public void begin() {
		if (outputHeader && !compact) {
			write("#thjson\n");
		}
		if (rootBraces) {
			write('{');
			indent++;
		}
		compact = nextCompact = defaultCompact;
	}

	@Override
	public void comment(String comment, CommentType type) {
		if (compact) {
			return; // Ignore comments in compact mode
		}

		switch (type) {
			case BLOCK:
				write("/* ");
				write(comment);
				write(" */\n");
				break;
			case SLASHSLASH:
				write(" // ");
				write(comment);
				write('\n');
				break;
			default:
				assert false : type;
		}
	}

	@Override
	public void directive(String directive) {
		write("\n#");
		write(directive);
		//write('\n');
	}

	private void writeTripleQuotedString(String s) {
		write("'''\n");
		write(s);
		write("\n");
		write("'''");
	}

	private void writeTripleQuotedBytes(byte[] bytes) {
		write("<<<\n");
		String encoded = new String(Base64.getEncoder().encode(bytes), UTF_8);
		for (int i = 0; i < encoded.length(); i += 64) {
			write(encoded.substring(i, Math.min(encoded.length(), i + 64)));
			write('\n');
		}
		write(">>>");
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

	private void writeQuotedBytes(byte[] bytes) {
		write('`');
		write(new String(Base64.getEncoder().encode(bytes), UTF_8));
		write('`');
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

	@Override
	public void write(int c) {
		write(String.valueOf((char) c));
	}

	@Override
	public void write(String str) {
		StringTokenizer st = new StringTokenizer(str, "\n, ", true);
		while (st.hasMoreTokens()) {
			String s = st.nextToken();
			if ("\n".equals(s)) {
				if (DEBUG) {
					System.out.println();
				}
				super.write('\n');
				lastOutput = Output.NEWLINE;
				col = 0;
			} else {
				if (col == 0) {
					if (useTabs) {
						for (int i = 0; i < indent; i++) {
							super.write('\t');
							if (DEBUG) {
								System.out.print("¦...");
							}
						}
					} else {
						for (int i = 0; i < indent * tabSize; i++) {
							super.write(' ');
							if (DEBUG) {
								System.out.print('.');
							}
						}
					}
					col = indent * tabSize;
				}
				col += s.length();
				if (DEBUG) {
					System.out.print(s);
				}
				super.write(s);
				if (",".equals(s)) {
					lastOutput = Output.COMMA;
				} else if (" ".equals(s)) {
					lastOutput = Output.WHITESPACE;
				} else if ("[".equals(s) || "{".equals(s)) {
					lastOutput = Output.OPEN;
				} else if ("]".equals(s) || "}".equals(s)) {
					lastOutput = Output.CLOSE;
				} else {
					lastOutput = Output.TEXT;
				}
			}
		}
	}

	private void begin(ObjType type, String clazz) {
		ObjectOutput output = new ObjectOutput(type, clazz, compact = nextCompact);
		nextCompact = defaultCompact;
		writeValue(true);
		objects.push(output);

		if (clazz != null) {
			if (lastOutput == Output.TEXT) {
				write("\n");
			}
			write("(");
			write(clazz);
			write(")");
			//indent++;
		}
	}

	private void endObj() {
		ObjectOutput output = objects.pop();
		if (output.values == 0) {
			// No values were output, so emit an open-and-close brace as we've not yet output the opening brace
			if (lastOutput == Output.TEXT) {
				write(" ");
			}
			write(output.type.openBracket);
			write(output.type.closeBracket);
		} else {
			boolean compact = output.compact;
			if (compact) {
				switch (lastOutput) {
					case NEWLINE:
						break;
					case TEXT:
						break;
					case WHITESPACE:
						break;
					case COMMA:
					default:
						break;
				}
			} else {
				switch (lastOutput) {
					case NEWLINE:
						break;
					case TEXT:
						write("\n");
						break;
					case WHITESPACE:
						break;
					case COMMA:
					default:
						break;
				}
				indent--;
			}
			write(output.type.closeBracket);
//			if (!compact) {
//			if (objects.isEmpty() || objects.peek().clazz != null) {
			indent--;
//			}
//			} else {
//				indent--;
//			}
		}
		if (!objects.isEmpty()) {
			compact = objects.peek().compact;
		}
		if (!compact) {
			write("\n");
		}
	}

	@Override
	public void beginObject(String clazz) {
		begin(ObjType.OBJECT, clazz);
	}

	@Override
	public void beginList(String clazz) {
		begin(ObjType.LIST, clazz);
	}

	@Override
	public void endObject() {
		endObj();
	}

	@Override
	public void endList() {
		endObj();
	}

	@Override
	public void property(String key) {
		if (key == null) {
			return;
		}

		this.key = key;
	}

	private void emitProperty(boolean isObj) {
		if (key == null) {
			if (isObj && lastOutput != Output.NEWLINE) {
				write("\n");
			}
			return;
		}

		boolean compact = objects.isEmpty() ? this.compact : objects.peek().compact;
		if (compact) {
			switch (lastOutput) {
				case COMMA:
					write(" ");
					break;
				case NEWLINE:
					break;
				case TEXT:
					break;
				case CLOSE:
					write(", ");
					break;
				case OPEN:
				case WHITESPACE:
					break;
				default:
					break;
			}
		} else {
			switch (lastOutput) {
				case COMMA:
					write(" ");
					break;
				case NEWLINE:
					break;
				case TEXT:
				case OPEN:
				case CLOSE:
					write("\n");
					break;
				case WHITESPACE:
					break;
				default:
					break;
			}
		}

		switch (classifyKey(key)) {
			case QUOTED:
				writeQuotedString(key);
				break;
			case SIMPLE:
				write(key);
				break;
			default:
				assert false;
		}
		write(":");

		key = null;
	}

	private void maybeOpenBrace() {
		if (!objects.isEmpty()) {
			ObjectOutput obj = objects.peek();
			if (obj.values++ == 0) {
				// First value. Need an opening brace

				if (obj.compact) {
					switch (lastOutput) {
						case COMMA:
							break;
						case NEWLINE:
							break;
						case TEXT:
							write(" ");
							break;
						case WHITESPACE:
						case OPEN:
						case CLOSE:
						default:
							break;
					}
					indent++;
				} else {
					switch (lastOutput) {
						case COMMA:
							break;
						case NEWLINE:
//							if (obj.clazz != null) {
//								indent++;
//							}
							indent++;
							break;
						case TEXT:
							write("\n");
							indent++;
							break;
						case WHITESPACE:
						case OPEN:
						case CLOSE:
							write("\n");
							indent++;
							break;
						default:
							break;
					}
				}
				write(obj.type.openBracket);
				if (!obj.compact) {
					write("\n");
					indent++;
				}
			}
		}
	}

	private void writeValue(boolean isObj) {
		maybeOpenBrace();
		if (compact) {
			switch (lastOutput) {
				case COMMA:
					write(" ");
					break;
				case NEWLINE:
					break;
				case OPEN:
					break;
				case TEXT:
					write(",");
					break;
				case CLOSE:
					break;
				case WHITESPACE:
					break;
				default:
					break;
			}
		} else {
			switch (lastOutput) {
				case COMMA:
					write(" ");
					break;
				case NEWLINE:
					break;
				case TEXT:
					write("\n");
					break;
				case OPEN:
					break;
				case CLOSE:
					write("\n");
					break;
				case WHITESPACE:
					break;
				default:
					break;
			}
		}
		emitProperty(isObj);
		switch (lastOutput) {
			case COMMA:
				write(" ");
				break;
			case NEWLINE:
				break;
			case TEXT:
			case CLOSE:
				write(" ");
				break;
			case OPEN:
				break;
			case WHITESPACE:
				break;
			default:
				break;
		}
	}

	private void writeValue(String text) {
		writeValue(false);
		write(text);
	}

	@Override
	public void valueNull() {
		writeValue("null");
	}

	@Override
	public void value(boolean b) {
		writeValue(String.valueOf(b));
	}

	@Override
	public void value(byte[] bytes) {
		writeValue(false);
		if (compact) {
			writeQuotedBytes(bytes);
		} else {
			if (bytes.length <= 80) {
				writeQuotedBytes(bytes);
			} else {
				if (objects.size() > 0 && objects.peek().type == ObjType.OBJECT) {
					indent++;
					write("\n");
				}
				writeTripleQuotedBytes(bytes);
				if (objects.size() > 0 && objects.peek().type == ObjType.OBJECT) {
					indent--;
				}
			}
		}
	}

	@Override
	public void value(float f) {
		writeValue(String.valueOf(f));
	}

	@Override
	public void value(int i) {
		writeValue(String.valueOf(i));
	}

	@Override
	public void value(String s) {
		writeValue(false);
		switch (classifyValue(s, compact)) {
			case MULTILINE:
				if (objects.size() > 0 && objects.peek().type == ObjType.OBJECT) {
					indent++;
					write("\n");
				}
				writeTripleQuotedString(s);
				if (objects.size() > 0 && objects.peek().type == ObjType.OBJECT) {
					indent--;
				}
				break;
			case QUOTED:
				writeQuotedString(s);
				break;
			case SIMPLE:
				write(s);
				break;
			default:
				assert false;
		}
	}

	@Override
	public void end() {
		if (rootBraces) {
			write("}");
			indent--;
			if (compact) {
				write(' ');
			} else {
				write('\n');
			}
		}
	}
}