package sam.server.file;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.READ;
import static sam.http.server.extra.Utils.pipe;
import static sam.myutils.Checker.isEmpty;
import static sam.myutils.System2.lookup;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import sam.http.server.extra.ServerLogger;
import sam.myutils.MyUtilsPath;
import sam.reference.WeakQueue;

public class ZipDocRoot extends BaseDocRoot {
	public static final int BUFFER_SIZE = Optional.ofNullable(lookup(ZipDocRoot.class.getName()+".buffer.size")).map(Integer::parseInt).orElse(2*1024*1024); // 2Mb
	private static final WeakQueue<ByteBuffer> BUFFERS = new WeakQueue<>(true, () -> ByteBuffer.allocate(BUFFER_SIZE));

	private final ByteBuffer buffer;
	private final long size;
	private final Set<String> entries = new HashSet<>();

	public ZipDocRoot(ServerLogger logger, Path path) throws IOException {
		super(logger, path);

		if(!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase().endsWith(".zip"))
			throw new IOException("not a zipfile");

		clearResources();

		synchronized (LOCK) {
			long size = Files.size(path);

			if(size >= BUFFER_SIZE) {
				this.size = size;
				logger.fine(() -> "zip file size("+bytesToString(size)+") found larger than buffer capacity("+bytesToString(BUFFER_SIZE)+"), delegating to DirRoot");
				buffer = null;
			} else {
				boolean success = false;
				buffer = BUFFERS.poll();
				this.size = buffer.remaining();

				try {
					try(FileChannel c = FileChannel.open(path, READ)) {
						buffer.clear();
						while(c.read(buffer) != -1) {}
						buffer.flip();
					}
					success = true;
				} finally {
					if(!success) {
						BUFFERS.add(buffer);
						closed = true;	
					}
				}
			}
			boolean success = false;
			try {
				entries.clear();
				try(ZipInputStream zis = zis(true)) {
					ZipEntry z;
					while((z = zis.getNextEntry()) != null) {
						entries.add(z.getName());
						zis.closeEntry();
					}
				}
				success = true;
			} finally {
				if(!success) {
					BUFFERS.add(buffer);
					closed = true;	
				}
			}
		}
	}

	private ZipInputStream zis(boolean closed) throws IOException {
		if(buffer != null)
			return new ZipInputStream(new ByteArrayInputStream(buffer.array(), buffer.position(), buffer.limit()));
		else {
			ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(getPath(), READ)));
			if(!closed) {
				synchronized (RESOURECES) {
					RESOURECES.add(new WeakReference<AutoCloseable>(zis));
				}	
			}
			return zis;
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName()+" [size="+bytesToString(size)+", buffered="+(buffer != null)+", path=\""+path+"\"]";
	}

	@Override
	public Src getFor0(String subpath) throws IOException {
		InputStream is = find(subpath);
		return is == null ? null : new Src(-1, is);
	} 

	@Override
	public void close() throws IOException {
		checkClosed();
		closed = true;

		clearResources();

		synchronized (LOCK) {
			byte[] buffer = null;

			try {
				if(isEmpty(new_downloaded))
					return;

				Path temp = MyUtilsPath.TEMP_DIR.resolve(path.getFileName()+".tmp");
				buffer = wbytes.poll();

				try(InputStream is = zis(true);
						ZipInputStream zis = new ZipInputStream(is);
						OutputStream os = Files.newOutputStream(temp);
						ZipOutputStream zos = new ZipOutputStream(os);) {

					ZipEntry z;
					while((z = zis.getNextEntry()) != null) {
						zos.putNextEntry(z);
						if(!z.getName().equals(downloaded_index_subpath) && !z.isDirectory()) 
							pipe(zis, zos, buffer);

						zis.closeEntry();
						zos.closeEntry();
					}

					for (Downloaded d : new_downloaded) {
						String s = RESOURCE_URI_SLASHED.concat(d.getName());
						d.setPath(s);
						z = new ZipEntry(s);
						zos.putNextEntry(z);
						pipe(Paths.get(d.getPath()), zos, buffer);
						zos.closeEntry();
					}

					z = new ZipEntry(downloaded_index_subpath);
					zos.putNextEntry(z);
					writeDownloaded(zos);
					zos.closeEntry();
				}

				logger.fine(() -> new_downloaded.stream().map(d -> d.name).collect(Collectors.joining("\n  ", "rezipped: \""+path+"\"\nadded:\n  ", "\n")));
				Files.move(temp, path, REPLACE_EXISTING);

			} finally {
				BUFFERS.offer(this.buffer);
				wbytes.add(buffer);
				new_downloaded.clear();

				clearResources();
			}
		}
	}

	private ZipInputStream find(String name) throws IOException {
		synchronized (LOCK) {
			if(!entries.contains(name))
				return null;

			ZipInputStream zis = null;
			boolean found = false;
			try {
				zis = zis(false);
				ZipEntry z;

				while((z = zis.getNextEntry()) != null) {
					checkClosed();

					if(z.getName().equals(name)) {
						found = true;	
						return zis;
					}
					zis.closeEntry();
				}
				return null;
			} finally {
				if(!found) {
					if(zis != null)
						zis.close();
				}
			}
		}
	}

	@Override
	protected Map<Downloadable, Downloaded> readOldDownloaded() throws IOException {
		ZipInputStream zis = find(downloaded_index_subpath);
		if(zis == null)
			return Collections.emptyMap();

		return readDownloaded(zis);
	}

	@Override
	protected Src resource(Downloaded dld) throws IOException {
		return  getFor0(dld.name);
	}
}
