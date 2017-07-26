package net.puppygames.thjson;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class THJSONInputStreamPerformanceTest {

	public static void main(String[] args) throws Exception {
		new THJSONInputStreamPerformanceTest().run();
	}

	public THJSONInputStreamPerformanceTest() {
	}

	public void run() throws IOException {
		byte[] buf = Loader.getTHJSON("test2.thjson");
		RepeatingInputStream ris = new RepeatingInputStream(() -> new ByteArrayInputStream(buf), 100000);
		THJSONInputStream is = new THJSONInputStream(ris);

		long then = System.currentTimeMillis();
		while (is.read() != -1) {
			// System.out.println(token);
		}
		long now = System.currentTimeMillis();
		System.out.println("Read " + ris.getBytesRead() + " bytes in " + (now - then) + "ms, or " + ((ris.getBytesRead() * 1000L) / (now - then)) / (1024L * 1024L) + " MB per second");
	}

}
