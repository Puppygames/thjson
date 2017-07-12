package net.puppygames.thjson;

/**
 * Implemented by things that want to write THJSON data out to somewhere
 */
public interface THJSONWriter {

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