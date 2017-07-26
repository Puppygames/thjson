package net.puppygames.thjson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class Loader extends RepeatingInputStreamPerformanceTest {

	static byte[] getTHJSON(String res) throws IOException {
		InputStream is = Loader.class.getResourceAsStream(res);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] temp = new byte[4096];
		long read;
		while ((read = is.read(temp)) != -1) {
			baos.write(temp, 0, (int) read);
		}
		return baos.toByteArray();
	}

	private Loader() {
	}

}
