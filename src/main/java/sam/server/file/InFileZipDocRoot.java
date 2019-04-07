package sam.server.file;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipInputStream;

import sam.functions.IOExceptionFunction;
import sam.http.server.extra.ServerLogger;
import sam.io.IOUtils;
import sam.io.infile.DataMeta;
import sam.reference.WeakPool;

class InFileZipDocRoot extends ZipDocRoot {
	private static byte[] tempBuffer;
	private static final WeakPool<ByteBuffer> buffers = new WeakPool<>(() -> ByteBuffer.allocate(4 * 1024));
	
	private final FileChannel tempFile;
	
	public InFileZipDocRoot(ServerLogger logger, Path path, long size) throws IOException {
		super(logger, path);
		tempFile = FileChannel.open(Files.createTempFile("ZipDocRootUnBuffered", null), WRITE, READ);
		
		synchronized(InFileZipDocRoot.class) {
			if(tempBuffer == null)
				tempBuffer = new byte[8124];

			byte[] buffer = tempBuffer;
			
			Map<String, DataMeta> map = ZipDocRoot.read(path, new IOExceptionFunction<ZipInputStream, DataMeta>() {
				long pos;
				
				@Override
				public DataMeta apply(ZipInputStream t) throws IOException {
					long size = IOUtils.pipe(t, tempFile, buffer);
					DataMeta dm = new DataMeta(pos, (int)size);
					pos += size;
					return dm;
				}
			});
			
			this.map = map;
		}
		
		init();
	}
	
	@Override
	protected InputStream read(DataMeta dm) {
		return new InputStream() {
			final long pos = dm.position;
			int remaining = dm.size;
			int read = 0;
			ByteBuffer buffer = buffers.poll();
			boolean first = true;
			boolean closed = false; 

			@Override
			public int read() throws IOException {
				checkClosed0();
				
				if(remaining == 0)
					return -1;
				
				if(first || !buffer.hasRemaining()) {
					first = false;
					buffer.clear();
					buffer.limit(Math.min(remaining, buffer.limit()));
					tempFile.read(buffer, pos + read);
					buffer.flip();
				}

				remaining--;
				return buffer.get(read++);
			}
			private void checkClosed0() throws IOException {
				checkClosed();
				if(closed)
					throw new IOException("closed");
			}
			@Override
			public long skip(long n) throws IOException {
				checkClosed0();
				
				if(n <= 0)
					return 0;
				
				if(n >= remaining) {
					n = Math.min(remaining, n);
					remaining = 0;
					read = dm.size;
				} else {
					read += n;
					remaining -= n;	
				}
				
				buffer.position((int) (buffer.position() + Math.min(n, buffer.remaining())));
				return n;
			}
			
			@Override
			public void close() throws IOException {
				closed = true;
				buffers.add(buffer);
			}
			
			@Override
			public int available() throws IOException {
				checkClosed0();
				return remaining;
			}
		};
	}
}
