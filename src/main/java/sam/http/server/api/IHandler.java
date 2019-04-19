package sam.http.server.api;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public interface IHandler extends AutoCloseable {
	Response serve(IHTTPSession session) throws Exception ;
}
