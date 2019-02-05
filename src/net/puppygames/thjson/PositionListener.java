package net.puppygames.thjson;

/**
 * For listening to the position that the tokenizer is about to read from
 */
public interface PositionListener {

	/**
	 * @param source May be null
	 * @param line
	 * @param col
	 */
	void onPosition(String source, int line, int col);

}
