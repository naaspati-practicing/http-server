package sam.server.file;

import java.io.IOException;
import java.nio.file.Path;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public interface DocRoot extends AutoCloseable {
	Response getFor(IHTTPSession session) throws IOException;
	Path getPath();
}
