package sam.http.server.handler;

import java.nio.file.Path;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public interface IHttpHandler {
	Response handle(IHTTPSession session) throws Exception ;
	void setDocRoot(Path root) throws Exception;
	Path getDocRoot();
}
