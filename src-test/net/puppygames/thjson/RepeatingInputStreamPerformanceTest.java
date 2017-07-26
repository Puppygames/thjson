package net.puppygames.thjson;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class RepeatingInputStreamPerformanceTest {

	public static void main(String[] args) throws Exception {
		new RepeatingInputStreamPerformanceTest().run();
	}

	public RepeatingInputStreamPerformanceTest() {
	}

	public void run() throws IOException {
		byte[] buf = Loader.getTHJSON("test2.thjson");

		@SuppressWarnings("resource")
		RepeatingInputStream ris = new RepeatingInputStream(() -> new ByteArrayInputStream(buf), 100000);

		long then = System.currentTimeMillis();
		while (ris.read() != -1) {
			// System.out.println(token);
		}
		long now = System.currentTimeMillis();
		System.out.println("Read " + ris.getBytesRead() + " bytes in " + (now - then) + "ms, or " + ((ris.getBytesRead() * 1000L) / (now - then)) / (1024L * 1024L) + " MB per second");
	}

}
