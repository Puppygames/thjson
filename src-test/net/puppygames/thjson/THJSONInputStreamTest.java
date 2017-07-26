package net.puppygames.thjson;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStreamReader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class THJSONInputStreamTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testInputStream() throws IOException {
		THJSONInputStream in = new THJSONInputStream(THJSONTokenizerTest.class.getResourceAsStream("test2.thjson"));

		test(in);
	}

	private void test(THJSONInputStream in) throws IOException {
		int c;
		while ((c = in.read()) != -1) {
			// If there's a \r, it's failed!
			if (c == '\r') {
				fail("Unexpected CR");
			}
			// Peek ahead
			c = in.peek(0);
			if (c == '\r') {
				fail("Unexpected CR in peek");
			}
		}

		assertEquals(91, in.getLine());
	}

	@Test
	public void testReader() throws IOException {
		THJSONInputStream in = new THJSONInputStream(new InputStreamReader(THJSONTokenizerTest.class.getResourceAsStream("test2.thjson")));

		test(in);
	}

}
