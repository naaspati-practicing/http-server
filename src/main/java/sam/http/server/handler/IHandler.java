package sam.http.server.handler;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public interface IHandler {
	Response serve(IHTTPSession session);
	void close();
}
