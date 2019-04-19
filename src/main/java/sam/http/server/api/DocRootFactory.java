package sam.http.server.api;

import java.nio.file.Path;

public interface DocRootFactory {
	public DocRoot create(Path path, ServerLogger logger);
}
