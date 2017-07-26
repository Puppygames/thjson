package net.puppygames.thjson;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

class RepeatingInputStream extends InputStream {
	int repeats;
	Supplier<InputStream> supplier;
	InputStream in;
	int bytesRead;

	RepeatingInputStream(Supplier<InputStream> supplier, int repeats) {
		this.repeats = repeats;
		this.supplier = supplier;
		in = new BufferedInputStream(supplier.get());
	}

	public int getBytesRead() {
		return bytesRead;
	}

	@Override
	public int read() throws IOException {
		int ret = in.read();
		if (ret == -1) {
			// EOF
			repeats--;
			if (repeats == 0) {
				return -1;
			}
			in.close();
			in = null;
			in = new BufferedInputStream(supplier.get());
			return read();
		}
		bytesRead++;
		return ret;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int ret = in.read(b, off, len);
		if (ret == -1) {
			// EOF
			repeats--;
			if (repeats == 0) {
				return -1;
			}
			in.close();
			in = null;
			in = new BufferedInputStream(supplier.get());
			return read(b, off, len);
		}
		bytesRead += ret;
		return ret;
	}

	@Override
	public int available() throws IOException {
		if (repeats > 0) {
			return in.available() + 1;
		} else {
			return in.available();
		}
	}
}