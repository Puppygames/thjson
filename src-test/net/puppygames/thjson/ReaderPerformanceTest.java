package net.puppygames.thjson;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ReaderPerformanceTest {

	public static void main(String[] args) throws Exception {
		new ReaderPerformanceTest().run();
	}

	int tokens;

	public ReaderPerformanceTest() {
	}

	public void run() throws Exception {
		// RepeatingInputStream ris = new RepeatingInputStream(() -> THJSONTokenizerTest.class.getResourceAsStream("test2.thjson"), 100000);
		RepeatingInputStream ris = new RepeatingInputStream(() -> {
			try {
				return new FileInputStream("c:/Projects/thjson/src/net/puppygames/thjson/test2.thjson");
			} catch (FileNotFoundException e) {
				return null;
			}
		}, 100);
		THJSONReader reader = new THJSONReader(ris, new THJSONListener() {

			@Override
			public void value(float value) {
				tokens++;
			}

			@Override
			public void value(boolean value) {
				tokens++;
			}

			@Override
			public void value(String value, StringType type) {
				tokens++;
			}

			@Override
			public void value(byte[] value, StringType type) {
				System.out.println(new String(Base64.getEncoder().encode(value), StandardCharsets.UTF_8));
				tokens++;
			}

			@Override
			public void value(int value, IntegerType type) {
				tokens++;
			}

			@Override
			public void property(String key, float value) {
				tokens++;
			}

			@Override
			public void property(String key, boolean value) {
				tokens++;
			}

			@Override
			public void property(String key, String value, StringType type) {
				tokens++;
			}

			@Override
			public void property(String key, byte[] value, StringType type) {
				System.out.println(new String(Base64.getEncoder().encode(value), StandardCharsets.UTF_8));
				tokens++;
			}

			@Override
			public void property(String key, int value, IntegerType type) {
				tokens++;
			}

			@Override
			public void nullValue() {
				tokens++;
			}

			@Override
			public void nullProperty(String key) {
				tokens++;
			}

			@Override
			public void endObject() {
				tokens++;
			}

			@Override
			public void endMap() {
				tokens++;
			}

			@Override
			public void endList() {
				tokens++;
			}

			@Override
			public void endArray() {
				tokens++;
			}

			@Override
			public void beginObjectValue(String clazz) {
				tokens++;
			}

			@Override
			public void beginObject(String key, String clazz) {
				tokens++;
			}

			@Override
			public void beginMapValue() {
				tokens++;
			}

			@Override
			public void beginMap(String key) {
				tokens++;
			}

			@Override
			public void beginListValue(String clazz) {
				tokens++;
			}

			@Override
			public void beginList(String key, String clazz) {
				tokens++;
			}

			@Override
			public void beginArrayValue() {
				tokens++;
			}

			@Override
			public void beginArray(String key) {
				tokens++;
			}
		});
		Token token;

		long then = System.currentTimeMillis();
		reader.parse();
		long now = System.currentTimeMillis();
		System.out.println("Read " + ris.getBytesRead() + " bytes in " + (now - then) + "ms, or " + ((ris.getBytesRead() * 1000L) / (now - then)) / (1024L * 1024L) + " MB per second");
		System.out.println("Total tokens " + tokens);
	}

}
