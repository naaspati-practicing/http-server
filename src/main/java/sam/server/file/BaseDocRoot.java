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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ini4j.Profile.Section;
import org.ini4j.Wini;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import sam.http.server.extra.ServerLogger;
import sam.reference.WeakPool;

abstract class BaseDocRoot implements DocRoot {
	protected static final WeakPool<byte[]> wbytes = new WeakPool<>(true, () -> new byte[4*1024]);

	protected final Object LOCK = new Object();
	private final AtomicBoolean closed = new AtomicBoolean(false);
	protected static final String RESOURCE_URI = "/sam-resource";
	protected static final String RESOURCE_URI_SLASHED = "/sam-resource/";
	protected static final String downloaded_index_subpath = RESOURCE_URI_SLASHED.substring(1).concat("downloaded_index.ini");
	protected final Path path; 
	protected final ServerLogger logger;
	protected volatile List<Downloaded> new_downloaded;
	protected Map<Downloadable, Downloaded> old_downloaded;
	protected Wini old_downloaded_ini;
	private boolean init = false;
	protected volatile HashMap<Integer, Downloadable> downloadables;

	public BaseDocRoot(ServerLogger logger, Path path) throws IOException {
		this.logger = Objects.requireNonNull(logger);
		this.path = Objects.requireNonNull(path);
		
		if(Files.notExists(path))
			throw new FileNotFoundException(path.toString());
	}
	protected void init() throws IOException {
		if(init)
			throw new IllegalStateException("already initialized");
		
		this.init = true;
		
		Src is = null;
		try {
			is = getFor0(downloaded_index_subpath);
			
			if(is == null || is.is == null) {
				old_downloaded_ini = new Wini();
				old_downloaded = Collections.emptyMap();
			} else {
				old_downloaded_ini = new Wini(is.is);
				old_downloaded = new HashMap<>();
				old_downloaded_ini.forEach((name, section) -> new Downloaded(name, section));
			}
		} finally {
			if(is != null && is.is != null)
				is.is.close();
		}
	}

	protected abstract Src getFor0(String subpath) throws IOException;

	protected void writeDownloaded(OutputStream os) throws IOException {
		if(old_downloaded_ini == null)
			old_downloaded_ini = new Wini();
		
		for (Downloaded d : new_downloaded) {
			Section sec = old_downloaded_ini.add(d.name);
			sec.add(PATH, d.path);
			sec.add(IDENTIFIER, d.downloadable.identifier);
			sec.add(ATTR, d.downloadable.attr);
			sec.add(URL, d.downloadable.url);
		}
		
		old_downloaded_ini.store(os);
	}
	
	@Override
	public Path getPath() {
		return path;
	}

	protected static String bytesToString(long bytes) {
		return bytesToHumanReadableUnits(bytes, false);
	}

	protected void checkClosed() {
		if(closed.get())
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
		if(!init)
			throw new IllegalStateException("not initialized");
		
		String subpath = session.getUri();

		/* TODO
		 * if(subpath.equals(RESOURCE_URI_SLASHED)) 
			return resource(Integer.parseInt(subpath.substring(RESOURCE_URI_SLASHED.length())));

		 */
		
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
		/* TODO
		 * switch (mime) {
			case TEXT_HTML:
				return processHtml(src, mime);
			case TEXT_CSS:
				return processCss(src, mime);
			default:
				return rawResponse(src, mime);
		}
		 */
		
		return rawResponse(src, mime);
	}

	private Response rawResponse(Src src, String mime) {
		checkClosed();
		if(src.size < 0)
			return newChunkedResponse(ACCEPTED, mime, src.is);
		else 
			return newFixedLengthResponse(ACCEPTED, mime, src.is, src.size);
	}

	private Response processCss(Src src, String mime) {
		// TODO Auto-generated method stub
		return rawResponse(src, TEXT_CSS);
	}

	private Response processHtml(Src src, String mime) {
		// TODO Auto-generated method stub
		return rawResponse(src, TEXT_HTML);
	}

	private Response resource(int resource_id) throws IOException {
		Downloaded dld;

		synchronized (LOCK) {
			Downloadable dnew = downloadables.get(resource_id);
			if(dnew == null)
				return null;

			dld = old_downloaded.get(dnew);
			//TODO find in old
			// if found - getFor0(RESOURCE_URI+)...
			Src src =  null; //TODO dld == null ? null : resource(dld);
			
			if(src != null && src.is != null)
				return rawResponse(src, getMimeTypeForFile(dld.name));
		}

		// FIXME Auto-generated method stub
		// download dld
		notYetImplemented(); 
		return null;
	}
	
	@Override
	public final void close() throws Exception {
		checkClosed();
		if(!closed.compareAndSet(false, true))
			throw new IllegalStateException("closed");
		
		try {
			close0();	
		} finally {
			synchronized (LOCK) {
				new_downloaded.clear();	
			}
		}
		
	}
	
	protected abstract void close0() throws IOException;

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
}
