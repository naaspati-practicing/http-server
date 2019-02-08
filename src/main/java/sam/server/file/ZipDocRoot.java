package sam.server.file;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static sam.http.server.extra.Utils.pipe;
import static sam.myutils.Checker.isEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import sam.http.server.extra.ServerLogger;
import sam.myutils.MyUtilsPath;
import sam.nopkg.AutoCloseableWrapper;

public abstract class ZipDocRoot extends BaseDocRoot {
	private Set<String> entries;

	public ZipDocRoot(ServerLogger logger, Path path) throws IOException {
		super(logger, path);
	}

	protected void init() throws IOException {
		entries = new HashSet<>();

		try(ZipInputStream zis = zis()) {
			ZipEntry z;
			while((z = zis.getNextEntry()) != null) {
				entries.add(z.getName());
				zis.closeEntry();
			}
		}
		
		super.init();
	}

	protected abstract ZipInputStream zis() throws IOException;
	protected abstract long size();
	public    abstract boolean isBuffered();

	@Override
	public String toString() {
		return getClass().getSimpleName()+" [size="+bytesToString(size())+", buffered="+isBuffered()+", path=\""+path+"\"]";
	}

	@Override
	public Src getFor0(String subpath) throws IOException {
		InputStream is = find(subpath);
		return is == null ? null : new Src(-1, is);
	} 

	@Override
	protected void close0() throws IOException {
		synchronized (LOCK) {
			if(isEmpty(new_downloaded))
				return;

			Path temp = MyUtilsPath.TEMP_DIR.resolve(path.getFileName()+".tmp");

			try(InputStream is = zis();
					ZipInputStream zis = new ZipInputStream(is);
					OutputStream os = Files.newOutputStream(temp);
					ZipOutputStream zos = new ZipOutputStream(os);
					AutoCloseableWrapper<byte[]> wbuffer = new AutoCloseableWrapper<>(wbytes::poll, wbytes::add);
					) {

				byte[] buffer = wbuffer.get();

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
		}
	}

	private ZipInputStream find(String name) throws IOException {
		synchronized (LOCK) {
			if(!entries.contains(name))
				return null;

			ZipInputStream zis = null;
			boolean found = false;
			try {
				zis = zis();
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
}
