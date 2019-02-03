package sam.http.server;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import sam.collection.ArraysUtils;
import sam.http.server.extra.ServerLogger;
import sam.http.server.handler.IHandler;
import sam.http.server.handler.IHttpHandler;
import sam.http.server.handler.ServerException;

public class Server extends NanoHTTPD {
	private final IHttpHandler handler;
	private final ServerLogger logger;
	private String base_uri;
	private IHandler[] handlers = new IHandler[0];

	public Server(int port,IHttpHandler handler,  ServerLogger logger) {
		super(port);
		this.logger = logger == null ? defaultLogger() : logger;
		this.handler = handler;
	}
	public Server(int port, ServerLogger logger) {
		this(port, new RootHandler(logger), logger);
	}

	private ServerLogger defaultLogger() {
		return new ServerLogger() {
			@Override public void info(Supplier<String> msg) {}
			@Override public void finer(Supplier<String> msg) { }
			@Override public void fine(Supplier<String> msg) { }
		};
	}
	public void start() throws IOException {
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		logger.info(() -> "started at: "+getBaseUri());
	}
	public void setRoot(File file) throws Exception {
		handler.setDocRoot(file == null ? null : file.toPath());
	}
	@Override
	public void stop() {
		try {
			setRoot(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.stop();
	}
	@Override
	public Response serve(IHTTPSession session) {
		IHandler[] handlers = this.handlers;

		for (IHandler h : handlers) {
			Response r = h.serve(session);
			if(r != null)
				return r;
		}

		try {
			return handler.handle(session);	
		} catch (Exception re) {
			return ServerException.create(Status.INTERNAL_ERROR, re.getMessage(), re);        
		}
	}
	public void add(IHandler h) {
		Objects.requireNonNull(h);

		handlers = new IHandler[handlers.length + 1];
		handlers[handlers.length - 1] = h;
	}
	public void remove(IHandler h) {
		Objects.requireNonNull(h);

		handlers = ArraysUtils.removeIf(handlers, h2 -> {
			if(h2 == h) {
				h.close();
				return true;
			}
			return false;
		}); 
	}
	public String getBaseUri() {
		if(base_uri != null)
			return base_uri;
		if(getListeningPort() < 0)
			throw new IllegalStateException("server not started");
		
		return this.base_uri = String.format("http://%s:%s", Optional.ofNullable(getHostname()).orElse("localhost"), getListeningPort());
	}
}
