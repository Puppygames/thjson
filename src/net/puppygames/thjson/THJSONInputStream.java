package net.puppygames.thjson;

import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;

/**
 * Takes THJSON-formatted input from an {@link InputStream}. Transforms MacOS and Windows line-endings into *nix line endings (LF only). Keeps track of line
 * number and column number. Has configurable tab size. Allows read-ahead ("peeking").
 * <p>
 * Note that this is not a full implementation of the {@link InputStream} API.
 */
public class THJSONInputStream {

	private static final int DEFAULT_TABSIZE = 4;
	private static final int READAHEAD_SIZE = 3;

	/** Read-ahead */
	private final int[] peekQueue = new int[READAHEAD_SIZE];

	/** Read characters from here */
	private final PushbackReader in;

	/** Whether we need to increase the line number on the next call to {@link #read()} */
	private boolean newLineNext;

	/** Current line number, 1-based */
	private int line = 1;

	/** Current column number, 1-based */
	private int col = 1;

	/** Number of characters in read-ahead queue */
	private int peekLength;

	/** Current tab size in spaces */
	private int tabSize = DEFAULT_TABSIZE;

	/**
	 * C'tor
	 * @param in Cannot be null
	 */
	public THJSONInputStream(InputStream in) {
		this.in = new PushbackReader(new InputStreamReader(requireNonNull(in, "in cannot be null")));
	}

	/**
	 * C'tor
	 * @param in Cannot be null
	 */
	public THJSONInputStream(Reader in) {
		if (in instanceof PushbackReader) {
			this.in = (PushbackReader) in;
		} else {
			this.in = new PushbackReader(requireNonNull(in, "in cannot be null"));
		}
	}

	/**
	 * Sets the tab size
	 * @param tabSize Must be &gt; 0
	 */
	public void setTabSize(int tabSize) {
		if (tabSize < 1) {
			throw new IllegalArgumentException("tabSize must be > 0: " + tabSize);
		}
		this.tabSize = tabSize;
	}

	/**
	 * @return the tab size, in spaces
	 */
	public int getTabSize() {
		return tabSize;
	}

	/**
	 * Gets the current line number (1-based)
	 * @return 1+
	 */
	public int getLine() {
		return line;
	}

	/**
	 * Gets the current column number (1-based)
	 * @return 1+
	 */
	public int getCol() {
		return col;
	}

	/**
	 * Peeks ahead. Only a small number of characters can be read ahead.
	 * @param ahead The number of characters ahead to read, must be in the range (0, {@value #READAHEAD_SIZE}]; 0 is the next character
	 * @return a char, or -1 if that's EOF
	 * @throws IOException
	 */
	public int peek(int ahead) throws IOException {
		if (ahead < 0) {
			throw new IllegalArgumentException("ahead must be >= 0: " + ahead);
		}
		for (int i = peekLength; i <= ahead; i++) {
			peekQueue[i] = read0();
			peekLength++;
		}
		return peekQueue[ahead];
	}

	/**
	 * A tiny bit of preprocessing: we have to turn \r\n sequences into a single \n, and single \r sequences into \n.
	 * @return -1 for EOF; will never return \r
	 * @throws IOException
	 */
	private int read0() throws IOException {
		int c = in.read();
		if (c == '\r') {
			// Read ahead - but we can't use peek
			c = in.read();
			if (c == '\n') {
				// Turn into a single \n by ignoring the \r
				return '\n';
			} else {
				// Turn a \r on its own into \n, and push back the last character
				in.unread(c);
				return '\n';
			}
		} else {
			return c;
		}
	}

	/**
	 * Read the next character, keeping track of line and column number. Carriage returns (\r) are turned into newlines (\n); a CRLF sequence (\r\n) will be
	 * condensed into a single newline.
	 * @return an int character, or -1 if that's EOF
	 * @throws IOException
	 */
	public int read() throws IOException {
		if (newLineNext) {
			newLineNext = false;
			line++;
			col = 1;
		}

		int c;

		if (peekLength > 0) {
			// Consume from read-ahead queue first
			c = peekQueue[0];
			arraycopy(peekQueue, 1, peekQueue, 0, --peekLength);
		} else {
			// Otherwise process
			c = read0();
		}

		if (c == -1) {
			return -1;
		} else if (c == '\n') {
			newLineNext = true;
		} else if (c == '\t') {
			col += tabSize;
			col -= col % tabSize;
		} else {
			col++;
		}
		return c;
	}

}
