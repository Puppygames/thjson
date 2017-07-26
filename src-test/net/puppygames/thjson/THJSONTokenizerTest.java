package net.puppygames.thjson;

import java.io.IOException;
import java.io.InputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class THJSONTokenizerTest {

	InputStream in;

	@Before
	public void setUp() throws Exception {
		in = THJSONTokenizerTest.class.getResourceAsStream("test4.thjson");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	@Ignore
	public void test() throws IOException {
		THJSONTokenizer tokenizer = new THJSONTokenizer(in);
		Token token;

		while ((token = tokenizer.read()) != Token.EOF) {
			System.out.println(token);
		}
	}

}
