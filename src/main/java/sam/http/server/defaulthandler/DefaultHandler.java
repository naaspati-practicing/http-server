package sam.http.server.defaulthandler;

import static fi.iki.elonen.NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED;
import static fi.iki.elonen.NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE;

import java.nio.file.Path;
import java.util.function.Consumer;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import sam.http.server.api.DocRoot;
import sam.http.server.api.DocRootFactory;
import sam.http.server.api.DocRootHandler;
import sam.http.server.api.NotFound;
import sam.http.server.api.ServerException;
import sam.http.server.api.ServerLogger;
import sam.myutils.Checker;
import sam.nopkg.EnsureSingleton;

class DefaultHandler implements DocRootHandler {
	private static final EnsureSingleton singleton = new EnsureSingleton(() -> {throw new IllegalStateException("only one instance allowed");});

	private final DocRootFactory[] factories;
	private volatile DocRoot docRoot;
	private final ServerLogger logger;
	private Consumer<Throwable> closeErrorHandler = e -> e.printStackTrace(); 

	public DefaultHandler(ServerLogger logger, DocRootFactory[] factories) {
		if(Checker.isEmpty(factories))
			throw new IllegalArgumentException("no factories specified");

		singleton.init();
		
		this.factories = factories;
		this.logger = logger;
	}
	
	@Override
	public void setCloseErrorHandler(Consumer<Throwable> closeErrorHandler) {
		this.closeErrorHandler = closeErrorHandler;
	}
	@Override
	public void setDocRoot(Path root) throws Exception {
		try {
			if(this.docRoot != null)
				this.docRoot.close();	
		} catch (Throwable e) {
			closeErrorHandler.accept(e);
		}
		
		if(root == null) {
			this.docRoot = null;
			return;
		}
		
		for (DocRootFactory fac : factories) {
			DocRoot docRoot = fac.create(root, logger);
			
			if(docRoot != null) {
				logger.info(() -> "root change: "+this.docRoot);
				this.docRoot = docRoot;
				return;
			}
		}
		
		throw new Exception("no DocRootFactory found for: "+root);
	}

	@Override
	public Path getDocRootPath() {
		return docRoot == null ? null : docRoot.getPath();
	}

	@Override
	public Response serve(IHTTPSession session) throws Exception {
		if(docRoot == null) 
			return ServerException.create(SERVICE_UNAVAILABLE, "root not set");

		if(!session.getMethod().equals(Method.GET))
			return ServerException.create(METHOD_NOT_ALLOWED, "only GET method allowed");

		Response resp = docRoot.getFor(session);

		if(resp == null)
			return NotFound.create("no data found for: "+session.getUri());
		else 
			return resp;
	}

	@Override
	public void close() throws Exception {
		setDocRoot(null);
	}
}
