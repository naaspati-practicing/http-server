package sam.http.server.handler;

import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public final class NotFound {
	public static Response create(String msg) {
		return ServerException.create(Status.NOT_FOUND, msg, null);
	}
	
}
