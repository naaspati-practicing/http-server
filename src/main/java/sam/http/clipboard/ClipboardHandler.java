package sam.http.clipboard;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
import static fi.iki.elonen.NanoHTTPD.Response.Status.ACCEPTED;
import static fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR;
import static fi.iki.elonen.NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED;
import static sam.http.server.api.Utils.TEXT_PLAIN;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Consumer;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import sam.http.server.api.IHandler;
import sam.http.server.api.Page;
import sam.http.server.api.ServerException;
import sam.http.server.api.ServerLogger;
import sam.io.ReadableByteChannelCustom;
import sam.io.serilizers.StringIOUtils;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.Resources;

class ClipboardHandler implements IHandler {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	{
		singleton.init();
	}
	
	private static final String PATH = "/clipboard";
	private String text;
	private Page page;
	private volatile Consumer<String> onChange;
	private final ServerLogger logger;

	public ClipboardHandler(ServerLogger logger) {
		this.logger = logger;
		try {
			this.page = new Page(getClass());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public void setText(String s) {
		this.text = s;
	}
	@Override
	public Response serve(IHTTPSession session) {
		if(onChange == null)
			return null;
		
		String s = session.getUri(); 
		if(!s.equals(PATH)) // && !s.startsWith(PATH)
			return null;

		switch (session.getMethod()) {
			case GET:
				return page();
			case PUT:
				try(Resources r = Resources.get();) {
					
					InputStream is = session.getInputStream(); // dont close stream... (bad practice)
					StringBuilder sb = r.sb();
					StringIOUtils.read(wrap(is, r.buffer()), r.sb(), r.decoder(), r.chars());
					text = sb.toString();
					onChange.accept(text);
					return newFixedLengthResponse(Status.NO_CONTENT, TEXT_PLAIN, null);
				} catch (IOException e) {
					return ServerException.create(INTERNAL_ERROR, "failed to read body", e);
				}
			default:
				return newFixedLengthResponse(METHOD_NOT_ALLOWED, TEXT_PLAIN, "method not allowed: "+session.getMethod());
		}
	}

	private ReadableByteChannel wrap(InputStream is, ByteBuffer buffer) {
		return new ReadableByteChannelCustom() {
			@Override
			public long size() throws IOException {
				return -1;
			}
			@Override
			public ByteBuffer buffer() {
				return buffer;
			}
			
			@Override
			public boolean isOpen() {
				return true;
			}
			@Override
			public void close() throws IOException { }
			
			@Override
			public int read(ByteBuffer dst) throws IOException {
				return is.read(dst.array(), dst.position(), dst.remaining());
			}
		};
	}
	private Response page() {
		return page
				.reset()
				.append(text == null ? "" : text)
				.finish()
				.response(ACCEPTED);
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
	}
	public String getPath() {
		return PATH;
	}
	public void setOnChange(Consumer<String> onChange) {
		this.onChange = onChange;
	}

}
