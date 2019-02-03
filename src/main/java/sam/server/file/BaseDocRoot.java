package sam.server.file;

import static fi.iki.elonen.NanoHTTPD.newChunkedResponse;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
import static fi.iki.elonen.NanoHTTPD.Response.Status.ACCEPTED;
import static java.nio.file.StandardOpenOption.READ;
import static sam.http.server.extra.Utils.TEXT_CSS;
import static sam.http.server.extra.Utils.TEXT_HTML;
import static sam.http.server.extra.Utils.getMimeTypeForFile;
import static sam.http.server.extra.Utils.pipe;
import static sam.myutils.MyUtilsBytes.bytesToHumanReadableUnits;
import static sam.nopkg.Junk.notYetImplemented;
import static sam.server.file.Downloaded.ATTR;
import static sam.server.file.Downloaded.IDENTIFIER;
import static sam.server.file.Downloaded.PATH;
import static sam.server.file.Downloaded.URL;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import sam.http.server.extra.ServerLogger;
import sam.reference.ReferenceUtils;
import sam.reference.WeakQueue;

public abstract class BaseDocRoot implements DocRoot {
	protected static final WeakQueue<byte[]> wbytes = new WeakQueue<>(true, () -> new byte[4*1024]);

	protected static final ArrayList<WeakReference<AutoCloseable>> RESOURECES = new ArrayList<>();
	protected final Object LOCK = new Object();
	protected volatile boolean closed;
	protected static final String RESOURCE_URI = "/sam-resource";
	protected static final String RESOURCE_URI_SLASHED = "/sam-resource/";
	protected static final String downloaded_index_subpath = RESOURCE_URI_SLASHED.substring(1).concat("downloaded_index.ini");
	protected final Path path; 
	protected final ServerLogger logger;
	protected List<Downloaded> new_downloaded;
	protected final Map<Downloadable, Downloaded> old_downloaded;
	protected HashMap<Integer, Downloadable> downloadables;

	public BaseDocRoot(ServerLogger logger, Path path) throws IOException {
		this.logger = Objects.requireNonNull(logger);
		this.path = Objects.requireNonNull(path);

		if(Files.notExists(path))
			throw new FileNotFoundException(path.toString());

		this.old_downloaded = readOldDownloaded();
	}

	protected abstract Map<Downloadable, Downloaded> readOldDownloaded() throws IOException;
	protected abstract Src resource(Downloaded dld) throws IOException;
	protected abstract Src getFor0(String subpath) throws IOException;

	protected Map<Downloadable, Downloaded> readDownloaded(InputStream is) throws IOException{
		InputStreamReader isr = new InputStreamReader(is, "utf-8");
		BufferedReader reader = new BufferedReader(isr);

		List<Downloaded> list = new ArrayList<>();
		Map<String, String> map = new HashMap<>(); 
		String line;
		String name = null;

		while((line = reader.readLine()) != null) {
			String s = line.trim();
			if(s.isEmpty())
				continue;
			if(s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') {
				if(name != null) { 
					map.put("name", name);
					list.add(new Downloaded(map));
					map.clear();
				}
				name = s.substring(1, s.length() - 1);
			} else {
				int n = line.indexOf('=');
				if(n > 0) 
					map.put(s.substring(0, n).trim(), s.substring(n+1));
			}
		}
		return list.stream().collect(Collectors.collectingAndThen(Collectors.toMap(d -> d.downloadable, d -> d, (o, n) -> n), Collections::unmodifiableMap));
	}

	protected void writeDownloaded(OutputStream os) throws IOException {
		OutputStreamWriter osr = new OutputStreamWriter(os, "utf-8");
		
		if(!old_downloaded.isEmpty())
			write(osr, old_downloaded.values());
		
		if(!new_downloaded.isEmpty())
			write(osr, new_downloaded);

		osr.flush();
	}

	private void write(OutputStreamWriter osr, Collection<Downloaded> values) throws IOException {
		for (Downloaded d : values) {
			osr.append('[').append(d.name).append(']').append('\n');
			append(osr, PATH, d.path);
			append(osr, IDENTIFIER, d.downloadable.identifier);
			append(osr, ATTR, d.downloadable.attr);
			append(osr, URL, d.downloadable.url);
			osr.append('\n');
		}
	}

	private void append(OutputStreamWriter osr, String key, String value) throws IOException {
		if(value != null)
			osr.append(key).append('=').append(value).append('\n');
	}

	@Override
	public Path getPath() {
		return path;
	}

	protected String bytesToString(long bytes) {
		return bytesToHumanReadableUnits(bytes, false);
	}

	protected void checkClosed() {
		if(closed)
			throw new IllegalStateException("closed");
	}

	protected void pipe0(InputStream is, OutputStream os) throws IOException {
		byte[] bytes = wbytes.poll();

		try {
			pipe(is, os, bytes);
		} finally {
			wbytes.add(bytes);
		}
	}

	protected void pipe0(Path p, OutputStream os) throws IOException {
		try(InputStream is = Files.newInputStream(p, READ)) {
			pipe0(is, os);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName()+"("+getPath()+")";
	}

	@Override
	public final Response getFor(IHTTPSession session) throws IOException {
		checkClosed();
		String subpath = session.getUri();

		if(subpath.equals(RESOURCE_URI_SLASHED)) 
			return resource(Integer.parseInt(subpath.substring(RESOURCE_URI_SLASHED.length())));

		if(subpath.equals("/"))
			subpath = "index.html";
		else
			subpath = subpath.substring(1);

		checkClosed();
		Src src = getFor0(subpath);
		checkClosed();
		if(src == null || src.is == null)
			return null;

		String mime = getMimeTypeForFile(subpath);
		switch (mime) {
			case TEXT_HTML:
				return processHtml(src, mime);
			case TEXT_CSS:
				return processCss(src, mime);
			default:
				return rawResponse(src, mime);
		}
	}

	private Response rawResponse(Src src, String mime) {
		checkClosed();
		if(src.size < 0)
			return newChunkedResponse(ACCEPTED, mime, src.is);
		else 
			return newFixedLengthResponse(ACCEPTED, mime, src.is, src.size);
	}

	private Response processCss(Src src, String mime) {
		// FIXME Auto-generated method stub
		return rawResponse(src, TEXT_CSS);
	}

	private Response processHtml(Src src, String mime) {
		// FIXME Auto-generated method stub
		return rawResponse(src, TEXT_HTML);
	}

	private Response resource(int resource_id) throws IOException {
		Downloaded dld;

		synchronized (LOCK) {
			Downloadable dnew = downloadables.get(resource_id);
			if(dnew == null)
				return null;

			dld = old_downloaded.get(dnew);
			Src src = dld == null ? null : resource(dld);
			
			if(src != null && src.is != null)
				return rawResponse(src, getMimeTypeForFile(dld.name));
		}

		// FIXME Auto-generated method stub
		// download dld
		notYetImplemented(); 
		return null;
	}

	protected static class Src {
		final long size;
		final InputStream is;

		public Src(long size, InputStream is) {
			this.size = size;
			this.is = is;
		}
	}

	protected void add(int id, Downloadable d) {
		checkClosed();

		synchronized (LOCK) {
			if(this.downloadables == null)
				downloadables = new HashMap<>();
			downloadables.put(id, d);
		}
	}
	
	protected static void clearResources() {
		if(RESOURECES.isEmpty()) 
			return;

		synchronized (RESOURECES) {
			if(RESOURECES.isEmpty()) 
				return;

			RESOURECES.forEach(s -> {
				AutoCloseable c = ReferenceUtils.get(s);
				if(c != null) {
					try {
						c.close();
					} catch (Exception e) { }
				}
			});
			RESOURECES.clear();
		}
	}
}
