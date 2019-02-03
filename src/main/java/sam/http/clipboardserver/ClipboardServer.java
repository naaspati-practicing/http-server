package sam.http.clipboardserver;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
import static fi.iki.elonen.NanoHTTPD.Response.Status.ACCEPTED;
import static fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR;
import static fi.iki.elonen.NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED;
import static sam.http.server.extra.Utils.TEXT_PLAIN;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.function.Consumer;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import sam.http.server.extra.Page;
import sam.http.server.extra.Utils;
import sam.http.server.handler.IHandler;
import sam.http.server.handler.ServerException;

public class ClipboardServer implements IHandler {
	private static final String PATH = "/clipboard";
	private final Consumer<String> onChange;
	private String text;
	private Page page;

	public ClipboardServer(Consumer<String> onChange) throws UnsupportedEncodingException, IOException {
		this.onChange = onChange;
		this.page = new Page(getClass());
	}
	public void setText(String s) {
		this.text = s;
	}
	@Override
	public Response serve(IHTTPSession session) {
		if(!session.getUri().startsWith(PATH))
			return null;

		switch (session.getMethod()) {
			case GET:
				return page();
			case PUT:
				int content_length = Integer.parseInt(session.getHeaders().get("content-length"));
				
				try {
					InputStream is = session.getInputStream(); // dont close stream... (ba practice)
					text = Utils.readString(is, content_length);
					onChange.accept(text);
					return newFixedLengthResponse(Status.NO_CONTENT, TEXT_PLAIN, null);
				} catch (IOException e) {
					return ServerException.create(INTERNAL_ERROR, "failed to read body", e);
				}
			default:
				return newFixedLengthResponse(METHOD_NOT_ALLOWED, TEXT_PLAIN, "method not allowed: "+session.getMethod());
		}
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

}
