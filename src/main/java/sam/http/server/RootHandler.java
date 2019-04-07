package sam.http.server;

import static fi.iki.elonen.NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED;
import static fi.iki.elonen.NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import sam.http.server.extra.ServerLogger;
import sam.http.server.handler.IHttpHandler;
import sam.http.server.handler.NotFound;
import sam.http.server.handler.ServerException;
import sam.server.file.DocRoot;
import sam.server.file.DocRootFactory;

class RootHandler implements IHttpHandler {
	private static final AtomicBoolean IS_INITIATED = new AtomicBoolean(false);

	private volatile DocRoot docRoot;
	private final ServerLogger logger;

	public RootHandler(ServerLogger logger) {
		if(IS_INITIATED.get())
			throw new IllegalStateException("only one instance allowed");
		if(!IS_INITIATED.compareAndSet(false, true))
			throw new IllegalStateException("only one instance allowed");

		this.logger = logger;
	}

	public void setDocRoot(Path root) throws Exception {
		try {
			if(this.docRoot != null)
				this.docRoot.close();	
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.docRoot = root == null ? null : DocRootFactory.of(root, logger);
		logger.info(() -> "root change: "+this.docRoot);
	}

	@Override
	public Path getDocRoot() {
		return docRoot == null ? null : docRoot.getPath();
	}
	

	@Override
	public Response handle(IHTTPSession session) throws Exception {
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
}
