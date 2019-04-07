package sam.server.file;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static sam.http.server.extra.Utils.pipe;
import static sam.myutils.Checker.isEmpty;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import sam.functions.IOExceptionFunction;
import sam.http.server.extra.ServerLogger;
import sam.io.infile.DataMeta;
import sam.myutils.MyUtilsPath;
import sam.nopkg.AutoCloseableWrapper;

abstract class ZipDocRoot extends BaseDocRoot {
	protected Map<String, DataMeta> map;
	
	public ZipDocRoot(ServerLogger logger, Path path) throws IOException {
		super(logger, path);
		this.map = Objects.requireNonNull(map);
	}
	
	protected abstract InputStream read(DataMeta dm);

	@Override
	public Src getFor0(String subpath) throws IOException {
		DataMeta dm = map.get(subpath);
		
		if(dm == null || dm.size == 0)
			return null;

		InputStream is = read(dm);
		if(is == null)
			return null;
		
		return new Src(dm.size(), is);
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
	
	protected static Map<String, DataMeta> read(Path path, IOExceptionFunction<ZipInputStream, DataMeta> consumer) throws FileNotFoundException, IOException {
		try(InputStream is = new FileInputStream(path.toFile());
				ZipInputStream zis = new ZipInputStream(is);) {

			ZipEntry z = zis.getNextEntry();

			if(z == null)
				return Collections.emptyMap();

			Map<String, DataMeta> map = new HashMap<>();

			while(z != null) {
				map.put(z.getName().replace('\\', '/'), consumer.apply(zis));
				zis.closeEntry();
				z = zis.getNextEntry();
			}
			return map;
		}
	}
}
