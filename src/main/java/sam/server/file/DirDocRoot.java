package sam.server.file;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import sam.http.server.extra.ServerLogger;
import sam.http.server.extra.Utils;
import sam.myutils.Checker;

public class DirDocRoot extends BaseDocRoot {

	public DirDocRoot(ServerLogger logger, Path path) throws IOException {
		super(logger, path);
	}

	@Override
	public Src getFor0(String subpath) throws IOException {
		Path file = path.resolve(subpath);

		if(Files.notExists(file))
			return null;
		return new Src(Files.size(file), Files.newInputStream(file, StandardOpenOption.READ));
	}

	@Override
	public void close0() throws IOException {
		synchronized (LOCK) {
			if(Checker.isEmpty(new_downloaded))
				return;
			
			try(OutputStream os = Files.newOutputStream(getPath().resolve(downloaded_index_subpath))) {
				writeDownloaded(os);
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
}
