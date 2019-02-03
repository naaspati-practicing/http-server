package sam.server.file;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import sam.http.server.extra.ServerLogger;
import sam.http.server.extra.Utils;
import sam.myutils.Checker;

public class DirDocRoot extends BaseDocRoot {

	boolean modified;

	public DirDocRoot(ServerLogger logger, Path path) throws IOException {
		super(logger, path);
	}
	
	@Override
	protected Map<Downloadable, Downloaded> readOldDownloaded() throws IOException {
		Path p = getPath().resolve(downloaded_index_subpath);
		if(Files.notExists(p))
			return Collections.emptyMap();
		else {
			try(InputStream is = Files.newInputStream(getPath().resolve(downloaded_index_subpath));) {
				return readDownloaded(is);
			} 
		}
	}

	@Override
	public Src getFor0(String subpath) throws IOException {
		Path file = path.resolve(subpath);

		if(Files.notExists(file))
			return null;

		long size = Files.size(file);
		InputStream is = Files.newInputStream(file, StandardOpenOption.READ);
		RESOURECES.add(new WeakReference<AutoCloseable>(is));
		return new Src(size, is);
	}

	@Override
	public void close() throws IOException {
		checkClosed();
		closed = true;
		
		clearResources();
		
		synchronized (LOCK) {
			if(Checker.isEmpty(new_downloaded))
				return;
			try(OutputStream os = Files.newOutputStream(getPath().resolve(downloaded_index_subpath))) {
				writeDownloaded(os);
				os.flush();
			} finally {
				new_downloaded.clear();
			}
			
		}
	}

	public void zip(Path target) throws IOException {
		Path tempzip = Files.createTempFile(target.getFileName().toString(), null);

		byte[] buffer = null;

		try(OutputStream os = Files.newOutputStream(tempzip, CREATE, WRITE);
				BufferedOutputStream bos = new BufferedOutputStream(os);
				ZipOutputStream zos = new ZipOutputStream(bos)) {
			Iterator<Path> itr = Files.walk(getPath())
					.skip(1)
					.iterator();

			int count = getPath().getNameCount(); 
			buffer = wbytes.poll();

			while (itr.hasNext()) {
				Path path = itr.next();
				boolean isDir = Files.isDirectory(path);
				ZipEntry zis = new ZipEntry(path.subpath(count, path.getNameCount())+(isDir ? "/" : ""));
				zos.putNextEntry(zis);
				if(!isDir) 
					Utils.pipe(path, zos, buffer);
				zos.closeEntry();
			}
		} finally {
			wbytes.add(buffer);
		}
		Files.move(tempzip, target, StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	protected Src resource(Downloaded dld) throws IOException {
		return getFor0(dld.name);
	}

}
