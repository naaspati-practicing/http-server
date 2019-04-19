package sam.http.server.api;

import java.nio.file.Path;
import java.util.function.Consumer;

public interface DocRootHandler extends IHandler {
	Path getDocRootPath() ;
	void setDocRoot(Path root) throws Exception ;
	void setCloseErrorHandler(Consumer<Throwable> closeErrorHandler) ;
}
