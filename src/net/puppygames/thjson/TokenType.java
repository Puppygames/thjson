/*

Copyright 2017 Shaven Puppy Ltd

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
contributors may be used to endorse or promote products derived from this
software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

*/

package net.puppygames.thjson;

/**
 * The different types of {@link Token}
 */
public enum TokenType {
	//@formatter:off
	STRING(DataType.STRING, false, false),
	BYTES(DataType.BYTES, false, false),
	MULTILINE_STRING(DataType.STRING, false, false),
	MULTILINE_BYTES(DataType.BYTES, false, false),
	SLASHSLASH_COMMENT(DataType.STRING, false, true),
	BLOCK_COMMENT(DataType.STRING, false, true),
	DIRECTIVE(DataType.STRING, false, false),
	INTEGER(DataType.INTEGER, true, false),
	SIGNED(DataType.INTEGER, true, false),
	HEX(DataType.INTEGER, true, false),
	BINARY(DataType.INTEGER, true, false),
	FLOAT(DataType.FLOAT, true, false),
	BOOLEAN(DataType.BOOLEAN, true, false),
	DELIMITER(DataType.CHAR, false, false),
	NULL(DataType.NULL, true, false),
	EOF(DataType.NULL, false, false);
	//@formatter:on

	private final DataType dataType;
	private final boolean literal;
	private final boolean comment;

	private TokenType(DataType dataType, boolean literal, boolean comment) {
		this.dataType = dataType;
		this.literal = literal;
		this.comment = comment;
	}

	public DataType getDataType() {
		return dataType;
	}

	public boolean isLiteral() {
		return literal;
	}

	public boolean isComment() {
		return comment;
	}
}
