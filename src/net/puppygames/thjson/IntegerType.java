package net.puppygames.thjson;

/**
 * The type of integer in a value
 */
public enum IntegerType {

	/** An ordinary unadorned signed integer */
	PLAIN,

	/** An ordinary integer that was preceded by + */
	SIGNED,

	/** Unsigned hex 0x integer */
	HEX,

	/** Unsigned binary % integer */
	BINARY

}
