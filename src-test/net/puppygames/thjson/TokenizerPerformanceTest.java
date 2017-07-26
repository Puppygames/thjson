package net.puppygames.thjson;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class TokenizerPerformanceTest {

	public static void main(String[] args) throws Exception {
		new TokenizerPerformanceTest().run();
	}

	public TokenizerPerformanceTest() {
	}

	public void run() throws IOException {
		byte[] buf = Loader.getTHJSON("test2.thjson");
		RepeatingInputStream ris = new RepeatingInputStream(() -> new ByteArrayInputStream(buf), 100000);
		THJSONTokenizer tokenizer = new THJSONTokenizer(ris);
		Token token;
		int tokens = 0;

		long then = System.currentTimeMillis();
		while ((token = tokenizer.read()) != Token.EOF) {
			if (token.getType() != null) {
				tokens++;
			}
		}
		long now = System.currentTimeMillis();
		System.out.println("Read " + ris.getBytesRead() + " bytes in " + (now - then) + "ms, or " + ((ris.getBytesRead() * 1000L) / (now - then)) / (1024L * 1024L) + " MB per second");
		System.out.println("Tokens read: " + tokens);
	}

}
