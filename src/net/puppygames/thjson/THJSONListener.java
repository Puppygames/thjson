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
 * Listen to the stream of symbols coming in from {@link THJSONReader}
 */
public interface THJSONListener {

	default void begin() {
	}

	default void beginMap(byte[] src, int key, int keyLength) {
		beginObject(src, key, keyLength, 0, 0);
	}

	default void beginObject(byte[] src, int key, int keyLength, int clazz, int clazzLength) {
	}

	default void endObject() {
	}

	default void endMap() {
		endObject();
	}

	default void end() {
	}

	default void beginArray(byte[] src, int key, int keyLength) {
		beginList(src, key, keyLength, 0, 0);
	}

	default void beginList(byte[] src, int key, int keyLength, int clazz, int clazzLength) {
	}

	default void endList() {
	}

	default void endArray() {
	}

	default void value(byte[] src, THJSONPrimitiveType type, int value, int valueLength) {
	}

	default void property(byte[] src, int key, int keyLength, byte[] valueSrc, THJSONPrimitiveType type, int value, int valueLength) {
	}

}
