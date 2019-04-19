package sam.server.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import sam.http.server.extra.ServerLogger;
import sam.io.infile.DataMeta;

class BufferedZipDocRoot extends ZipDocRoot {
	private final ByteBuffer buffer;

	public BufferedZipDocRoot(ServerLogger logger, Path path, ByteBuffer buffer) throws IOException {
		super(logger, path);
		this.buffer = Objects.requireNonNull(buffer);
		
		buffer.clear();
		
		Map<String, DataMeta> map = ZipDocRoot.read(path, zis -> {
			int pos = buffer.position();
			int size2 = 0;
			int read = 0;
			
			while((read = zis.read(buffer.array(), buffer.position(), buffer.remaining())) != -1) {
				buffer.position(buffer.position() + read);
				size2 += read;
			}
			return new DataMeta(pos, size2); 
		});
		
		buffer.flip();
		this.map = map;
		
		init();
	}

	@Override
	protected InputStream read(DataMeta dm) {
		return new InputStream() {
			int pos = (int) dm.position;
			int remaining = dm.size;

			@Override
			public int read() throws IOException {
				checkClosed();
				
				if(remaining == 0)
					return -1;

				remaining--;
				return buffer.get(pos++);
			}
			@Override
			public long skip(long n) throws IOException {
				checkClosed();
				
				if(n <= 0)
					return 0;
				
				if(n >= remaining) {
					n = Math.min(remaining, n);
					remaining = 0;
					pos = (int) (dm.size + dm.position);
				} else {
					pos += n;
					remaining -= n;	
				}
				return n;
			}
			@Override
			public int available() throws IOException {
				checkClosed();
				return remaining;
			}
		};
	}
}

