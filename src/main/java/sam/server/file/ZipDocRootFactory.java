package sam.server.file;

import static java.nio.file.StandardOpenOption.READ;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import sam.http.server.extra.ServerLogger;
import sam.myutils.System2;

public class ZipDocRootFactory {
	private static volatile ZipDocRootFactory INSTANCE;

	public static ZipDocRootFactory getInstance() {
		if (INSTANCE != null)
			return INSTANCE;

		synchronized (ZipDocRootFactory.class) {
			if (INSTANCE != null)
				return INSTANCE;

			INSTANCE = new ZipDocRootFactory();
			return INSTANCE;
		}
	}

	public static final int BUFFER_SIZE = Optional.ofNullable(System2.lookup(ZipDocRoot.class.getName()+".buffer.size")).map(Integer::parseInt).orElse(2*1024*1024); // 2Mb
	public static final AtomicInteger count = new AtomicInteger(); 
	private static class Buffer {
		private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		private AtomicBoolean inUse = new AtomicBoolean(false);
		
		public Buffer() {
			System.out.println(getClass().getName()+", created="+count.incrementAndGet());
		}
		void close() {
			inUse.set(false);
		}
		void open() {
			if(!inUse.compareAndSet(false, true))
				throw new IllegalStateException("in use");
		}
	} 
	private ZipDocRootFactory() { }

	private final Buffer defaultBuffer = new Buffer();

	public ZipDocRoot create(ServerLogger logger, Path path) throws IOException {
		long size = Files.size(path);

		if(size >= BUFFER_SIZE) {
			logger.fine(() -> "zip file size("+BaseDocRoot.bytesToString(size)+") found larger than buffer capacity("+BaseDocRoot.bytesToString(BUFFER_SIZE)+"), thus using unbuffered");
			return new ZipDocRootUnBuffered(logger, path, size);
		} else {
			boolean success = false;
			Buffer buffer = defaultBuffer.inUse.get() ? new Buffer() : defaultBuffer;
			
			try {
				ZipDocRoot z = new ZipDocRootBuffered(logger, path, buffer);
				success = true;
				return z;
			} finally {
				if(!success)
					buffer.close();
			}	
		}
	}

	private static class ZipDocRootBuffered extends ZipDocRoot {
		private final Buffer buffer;
		private final ByteBuffer buf;
		private final int size;

		public ZipDocRootBuffered(ServerLogger logger, Path path, Buffer buffer) throws IOException {
			super(logger, path);
			this.buffer = Objects.requireNonNull(buffer);
			this.buf = buffer.buffer;
			buffer.open();

			try(FileChannel c = FileChannel.open(path, READ)) {
				buf.clear();
				while(c.read(buf) != -1) {}
				buf.flip();
			}
			
			this.size = buf.remaining();
			init();
		}

		@Override
		protected ZipInputStream zis() {
			checkClosed();
			return new ZipInputStream(new ByteArrayInputStream(buf.array(), buf.position(), buf.limit()));
		}
		@Override
		protected long size() {
			return size;
		}
		@Override
		public boolean isBuffered() {
			return true;
		}
		@Override
		public void close0() throws IOException {
			try {
				super.close0();
			} finally {
				buffer.close();
			}
		}
	}
	private static class ZipDocRootUnBuffered extends ZipDocRoot {
		final long size;
		public ZipDocRootUnBuffered(ServerLogger logger, Path path, long size) throws IOException {
			super(logger, path);
			this.size = size;
			init();
		}
		@Override
		protected ZipInputStream zis() throws IOException {
			return new ZipInputStream(new BufferedInputStream(Files.newInputStream(getPath(), READ)));
		}
		@Override
		protected long size() {
			return size;
		}
		@Override
		public boolean isBuffered() {
			return false;
		}
	}
}
