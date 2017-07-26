package net.puppygames.thjson;

/**
 * Implemented by things that want to write THJSON data out to somewhere
 */
public interface THJSONWriter {

	/**
	 * Escape the incoming string as if it were to be surrounded by quotes
	 * @param in
	 * @return
	 */
	static String escape(String in) {
		StringBuilder sb = null;
		for (int i = 0; i < in.length() && sb == null; i++) {
			char c = in.charAt(i);
			if ("\"\\\n\t".indexOf(c) != -1 || c > 0x7F) {
				// This string needs escaping
				sb = new StringBuilder(in.length() + 1);
				sb.append(in.substring(0, i));
			}
		}
		if (sb == null) {
			return in;
		}
		for (int i = sb.length(); i < in.length(); i++) {
			char c = in.charAt(i);
			switch (c) {
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					if (c > 0x7F) {
						sb.append("\\u");
						if (c < 0x1000) {
							sb.append('0');
						}
						if (c < 0x100) {
							sb.append('0');
						}
						sb.append(Integer.toHexString(c));
					} else {
						sb.append(c);
					}
			}
		}

		return sb.toString();
	}

	void begin();

	void beginMap(String key);

	void endMap();

	void beginObject(String key, String clazz);

	void endObject();

	void beginArray(String key);

	void endArray();

	void beginList(String key, String clazz);

	void endList();

	void value(boolean b);

	void value(int i);

	void value(float f);

	void value(String s);

	void valueNull();

	void property(String key, boolean b);

	void property(String key, int i);

	void property(String key, float f);

	void property(String key, String s);

	void propertyNull(String key);

	void end();

}