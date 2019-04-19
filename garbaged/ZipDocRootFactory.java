package sam.server.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import sam.http.server.extra.ServerLogger;
import sam.myutils.System2;
import sam.reference.WeakPool;

class ZipDocRootFactory {
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

	private ZipDocRootFactory() { }

	public static final int BUFFER_SIZE = Optional.ofNullable(System2.lookup(BufferedZipDocRoot.class.getName()+".buffer.size")).map(Integer::parseInt).orElse(2*1024*1024); // 2Mb
	private static final WeakPool<ByteBuffer> buffers = new WeakPool<>(() -> ByteBuffer.allocate(BUFFER_SIZE));

	public ZipDocRoot create(ServerLogger logger, Path path) throws IOException {
		long size = Files.size(path);

		if(size >= BUFFER_SIZE) {
			logger.fine(() -> "zip file size("+BaseDocRoot.bytesToString(size)+") found larger than buffer capacity("+BaseDocRoot.bytesToString(BUFFER_SIZE)+"), thus using unbuffered");
			return new InFileZipDocRoot(logger, path, size);
		} else {
			boolean success = false;
			ByteBuffer buffer = buffers.poll();

			try {
				ZipDocRoot z = new BufferedZipDocRoot(logger, path, buffer);
				success = true;
				return z;
			} finally {
				if(!success)
					buffers.add(buffer);
			}	
		}
	}
	}
