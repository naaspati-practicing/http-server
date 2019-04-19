package sam.http.server;

import java.io.IOException;
import java.util.Optional;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import sam.http.server.api.IHandler;
import sam.http.server.api.IHandlerFactory;
import sam.http.server.api.NotFound;
import sam.http.server.api.ServerException;
import sam.http.server.api.ServerLogger;
import sam.http.server.api.Utils;
import sam.myutils.Checker;

public class Server extends NanoHTTPD {
	private final ServerLogger logger;
	private String base_uri;
	private final IHandler[] handlers;
	
	public Server(int port, ServerLogger logger, IHandler[] handlers) {
		super(port);
		if(Checker.isEmpty(handlers))
			throw new IllegalArgumentException("no handlers specified");
		
		this.logger = logger == null ? new ServerLogger() : logger;
		this.handlers = handlers;
	}

	public Server(int port, ServerLogger logger) {
		this(port, logger, Utils.serviceLoaded(IHandlerFactory.class, IHandler.class, IHandlerFactory::get));
	}	
	public void start() throws IOException {
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		logger.info(() -> "started at: "+getBaseUri());
	}
	
	@Override
	public void stop() {
		for (IHandler h : handlers) {
			try {
				h.close();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		} 
			
		super.stop();
	}
	@Override
	public Response serve(IHTTPSession session) {
		for (IHandler h : handlers) {
			try {
				Response r = h.serve(session);
				
				if(r != null)
					return r;
			} catch (Throwable e2) {
				return ServerException.create(Status.INTERNAL_ERROR, e2.getMessage(), e2);
			}
		}
		return NotFound.create("no handler found");
		
	}
	public String getBaseUri() {
		if(base_uri != null)
			return base_uri;
		if(getListeningPort() < 0)
			throw new IllegalStateException("server not started");
		
		return this.base_uri = String.format("http://%s:%s", Optional.ofNullable(getHostname()).orElse("localhost"), getListeningPort());
	}
}
